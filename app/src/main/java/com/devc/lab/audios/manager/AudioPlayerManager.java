package com.devc.lab.audios.manager;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.IOException;

/**
 * 기본 오디오 플레이어 관리자
 * MediaPlayer를 사용하여 변환된 오디오 파일 재생
 * LiveData를 통한 상태 관리 및 UI 바인딩
 */
public class AudioPlayerManager {
    
    // 플레이어 상태 열거형
    public enum PlaybackState {
        IDLE,           // 초기 상태
        PREPARING,      // 준비 중
        PREPARED,       // 준비 완료
        PLAYING,        // 재생 중
        PAUSED,         // 일시정지
        STOPPED,        // 정지
        ERROR           // 에러 상태
    }
    
    // 콜백 인터페이스들
    public interface OnPlayerStateChangeListener {
        void onStateChanged(PlaybackState newState);
        void onError(String error);
    }
    
    public interface OnProgressUpdateListener {
        void onProgressUpdate(int currentPosition, int duration);
    }
    
    public interface OnCompletionListener {
        void onPlaybackCompleted();
    }
    
    private MediaPlayer mediaPlayer;
    private Context context;
    private Handler progressHandler;
    private Runnable progressRunnable;
    
    // 상태 관리 LiveData
    private MutableLiveData<PlaybackState> playbackState = new MutableLiveData<>(PlaybackState.IDLE);
    private MutableLiveData<Integer> currentPosition = new MutableLiveData<>(0);
    private MutableLiveData<Integer> duration = new MutableLiveData<>(0);
    private MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private MutableLiveData<String> currentTrackPath = new MutableLiveData<>("");
    
    // 콜백 리스너들
    private OnPlayerStateChangeListener stateChangeListener;
    private OnProgressUpdateListener progressUpdateListener;
    private OnCompletionListener completionListener;
    
    // 현재 재생 정보
    private String currentFilePath;
    private boolean isPrepared = false;
    private boolean isProgressTracking = false;
    
    public AudioPlayerManager(Context context) {
        this.context = context;
        this.progressHandler = new Handler(Looper.getMainLooper());
        initializePlayer();
        setupProgressTracking();
    }
    
    /**
     * MediaPlayer 초기화
     */
    private void initializePlayer() {
        try {
            mediaPlayer = new MediaPlayer();
            
            // MediaPlayer 이벤트 리스너 설정
            mediaPlayer.setOnPreparedListener(mp -> {
                isPrepared = true;
                updateState(PlaybackState.PREPARED);
                
                int durationMs = mp.getDuration();
                duration.postValue(durationMs);
                
                LoggerManager.logger("오디오 준비 완료 - 길이: " + durationMs + "ms");
            });
            
            mediaPlayer.setOnCompletionListener(mp -> {
                updateState(PlaybackState.STOPPED);
                isPlaying.postValue(false);
                currentPosition.postValue(0);
                stopProgressTracking();
                
                if (completionListener != null) {
                    completionListener.onPlaybackCompleted();
                }
                
                LoggerManager.logger("재생 완료");
            });
            
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                String errorMsg = "MediaPlayer 오류 - What: " + what + ", Extra: " + extra;
                LoggerManager.logger(errorMsg);
                
                updateState(PlaybackState.ERROR);
                
                if (stateChangeListener != null) {
                    stateChangeListener.onError(errorMsg);
                }
                
                return true; // 에러 처리됨
            });
            
            updateState(PlaybackState.IDLE);
            LoggerManager.logger("AudioPlayerManager 초기화 완료");
            
        } catch (Exception e) {
            LoggerManager.logger("AudioPlayerManager 초기화 실패: " + e.getMessage());
            updateState(PlaybackState.ERROR);
        }
    }
    
    /**
     * 진행률 추적 설정
     */
    private void setupProgressTracking() {
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && isPlaying.getValue() == Boolean.TRUE && isPrepared) {
                    try {
                        int currentPos = mediaPlayer.getCurrentPosition();
                        int totalDuration = mediaPlayer.getDuration();
                        
                        currentPosition.postValue(currentPos);
                        
                        if (progressUpdateListener != null) {
                            progressUpdateListener.onProgressUpdate(currentPos, totalDuration);
                        }
                        
                        // 1초마다 업데이트
                        if (isProgressTracking) {
                            progressHandler.postDelayed(this, 1000);
                        }
                        
                    } catch (Exception e) {
                        LoggerManager.logger("진행률 업데이트 실패: " + e.getMessage());
                    }
                }
            }
        };
    }
    
    /**
     * 오디오 파일 로드 (파일 경로)
     */
    public void loadAudio(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            notifyError("잘못된 파일 경로");
            return;
        }
        
        try {
            // 기존 재생 정리
            reset();
            
            updateState(PlaybackState.PREPARING);
            
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepareAsync(); // 비동기 준비
            
            currentFilePath = filePath;
            currentTrackPath.postValue(filePath);
            
            LoggerManager.logger("오디오 로드 시작: " + filePath);
            
        } catch (IOException e) {
            LoggerManager.logger("오디오 로드 실패: " + e.getMessage());
            notifyError("오디오 로드 실패: " + e.getMessage());
        }
    }
    
    /**
     * 오디오 파일 로드 (URI)
     */
    public void loadAudio(Uri audioUri) {
        if (audioUri == null) {
            notifyError("잘못된 오디오 URI");
            return;
        }
        
        try {
            // 기존 재생 정리
            reset();
            
            updateState(PlaybackState.PREPARING);
            
            mediaPlayer.setDataSource(context, audioUri);
            mediaPlayer.prepareAsync();
            
            currentFilePath = audioUri.toString();
            currentTrackPath.postValue(currentFilePath);
            
            LoggerManager.logger("오디오 로드 시작: " + audioUri.toString());
            
        } catch (IOException e) {
            LoggerManager.logger("오디오 로드 실패: " + e.getMessage());
            notifyError("오디오 로드 실패: " + e.getMessage());
        }
    }
    
    /**
     * 재생 시작
     */
    public void play() {
        try {
            if (mediaPlayer == null) {
                notifyError("MediaPlayer가 초기화되지 않음");
                return;
            }
            
            if (!isPrepared) {
                LoggerManager.logger("아직 준비되지 않음 - 재생 대기");
                return;
            }
            
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                updateState(PlaybackState.PLAYING);
                isPlaying.postValue(true);
                startProgressTracking();
                
                LoggerManager.logger("재생 시작");
            }
            
        } catch (Exception e) {
            LoggerManager.logger("재생 실패: " + e.getMessage());
            notifyError("재생 실패: " + e.getMessage());
        }
    }
    
    /**
     * 일시정지
     */
    public void pause() {
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                updateState(PlaybackState.PAUSED);
                isPlaying.postValue(false);
                stopProgressTracking();
                
                LoggerManager.logger("일시정지");
            }
            
        } catch (Exception e) {
            LoggerManager.logger("일시정지 실패: " + e.getMessage());
            notifyError("일시정지 실패: " + e.getMessage());
        }
    }
    
    /**
     * 정지
     */
    public void stop() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                
                updateState(PlaybackState.STOPPED);
                isPlaying.postValue(false);
                currentPosition.postValue(0);
                stopProgressTracking();
                
                // 다시 재생하려면 prepare 필요
                isPrepared = false;
                
                LoggerManager.logger("정지");
            }
            
        } catch (Exception e) {
            LoggerManager.logger("정지 실패: " + e.getMessage());
            notifyError("정지 실패: " + e.getMessage());
        }
    }
    
    /**
     * 특정 위치로 이동 (SeekBar용)
     */
    public void seekTo(int positionMs) {
        try {
            if (mediaPlayer != null && isPrepared) {
                mediaPlayer.seekTo(positionMs);
                currentPosition.postValue(positionMs);
                
                LoggerManager.logger("위치 이동: " + positionMs + "ms");
            }
            
        } catch (Exception e) {
            LoggerManager.logger("위치 이동 실패: " + e.getMessage());
            notifyError("위치 이동 실패: " + e.getMessage());
        }
    }
    
    /**
     * 플레이어 리셋
     */
    public void reset() {
        try {
            if (mediaPlayer != null) {
                stopProgressTracking();
                
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                
                mediaPlayer.reset();
                isPrepared = false;
                
                updateState(PlaybackState.IDLE);
                isPlaying.postValue(false);
                currentPosition.postValue(0);
                duration.postValue(0);
                
                LoggerManager.logger("플레이어 리셋");
            }
            
        } catch (Exception e) {
            LoggerManager.logger("플레이어 리셋 실패: " + e.getMessage());
        }
    }
    
    /**
     * 리소스 해제
     */
    public void release() {
        try {
            stopProgressTracking();
            
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                
                mediaPlayer.release();
                mediaPlayer = null;
            }
            
            updateState(PlaybackState.IDLE);
            isPlaying.postValue(false);
            
            LoggerManager.logger("AudioPlayerManager 리소스 해제");
            
        } catch (Exception e) {
            LoggerManager.logger("리소스 해제 실패: " + e.getMessage());
        }
    }
    
    /**
     * 진행률 추적 시작
     */
    private void startProgressTracking() {
        isProgressTracking = true;
        progressHandler.post(progressRunnable);
    }
    
    /**
     * 진행률 추적 정지
     */
    private void stopProgressTracking() {
        isProgressTracking = false;
        progressHandler.removeCallbacks(progressRunnable);
    }
    
    /**
     * 상태 업데이트
     */
    private void updateState(PlaybackState newState) {
        playbackState.postValue(newState);
        
        if (stateChangeListener != null) {
            stateChangeListener.onStateChanged(newState);
        }
    }
    
    /**
     * 에러 알림
     */
    private void notifyError(String errorMessage) {
        updateState(PlaybackState.ERROR);
        
        if (stateChangeListener != null) {
            stateChangeListener.onError(errorMessage);
        }
    }
    
    /**
     * 시간 포맷 변환 유틸리티
     */
    public static String formatTime(int timeMs) {
        int seconds = (timeMs / 1000) % 60;
        int minutes = (timeMs / (1000 * 60)) % 60;
        int hours = timeMs / (1000 * 60 * 60);
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }
    
    // Getter 메서드들 (현재 상태 확인용)
    public boolean isCurrentlyPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }
    
    public boolean isPrepared() {
        return isPrepared;
    }
    
    public int getCurrentPosition() {
        try {
            return (mediaPlayer != null && isPrepared) ? mediaPlayer.getCurrentPosition() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
    
    public int getDuration() {
        try {
            return (mediaPlayer != null && isPrepared) ? mediaPlayer.getDuration() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
    
    public String getCurrentFilePath() {
        return currentFilePath;
    }
    
    // LiveData getters (UI 바인딩용)
    public LiveData<PlaybackState> getPlaybackState() {
        return playbackState;
    }
    
    public LiveData<Integer> getCurrentPositionLiveData() {
        return currentPosition;
    }
    
    public LiveData<Integer> getDurationLiveData() {
        return duration;
    }
    
    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }
    
    public LiveData<String> getCurrentTrackPath() {
        return currentTrackPath;
    }
    
    // Setter 메서드들 (콜백 등록)
    public void setOnPlayerStateChangeListener(OnPlayerStateChangeListener listener) {
        this.stateChangeListener = listener;
    }
    
    public void setOnProgressUpdateListener(OnProgressUpdateListener listener) {
        this.progressUpdateListener = listener;
    }
    
    public void setOnCompletionListener(OnCompletionListener listener) {
        this.completionListener = listener;
    }
}