package com.devc.lab.audios.model;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.devc.lab.audios.manager.AudioPlayerManager;
import com.devc.lab.audios.manager.FFmpegManager;
import com.devc.lab.audios.manager.FileManager;
import com.devc.lab.audios.manager.LoggerManager;
import com.devc.lab.audios.manager.PermissionManager;

import java.util.List;

/**
 * 메인 ViewModel - MVVM 아키텍처의 중심
 * 모든 Manager들을 통합하여 UI와 비즈니스 로직을 연결
 * LiveData를 통한 반응형 상태 관리
 */
public class MainViewModel extends AndroidViewModel {
    
    // Manager 인스턴스들
    private PermissionManager permissionManager;
    private FileManager fileManager;
    private FFmpegManager ffmpegManager;
    private AudioPlayerManager audioPlayerManager;
    
    // UI 상태 LiveData
    private MutableLiveData<String> statusMessage = new MutableLiveData<>("");
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private MutableLiveData<String> errorMessage = new MutableLiveData<>("");
    
    // 파일 선택 관련
    private MutableLiveData<Uri> selectedFileUri = new MutableLiveData<>();
    private MutableLiveData<String> selectedFileName = new MutableLiveData<>("");
    
    // 변환 관련
    private MutableLiveData<Integer> conversionProgress = new MutableLiveData<>(0);
    private MutableLiveData<Boolean> isConverting = new MutableLiveData<>(false);
    private MutableLiveData<String> convertedFilePath = new MutableLiveData<>("");
    
    // 권한 관련
    private MutableLiveData<Boolean> hasRequiredPermissions = new MutableLiveData<>(false);
    private MutableLiveData<String> permissionStatusText = new MutableLiveData<>("");
    
    // 권한 요청 중복 방지 플래그 (ViewModel 레벨)
    private boolean isPermissionRequesting = false;
    
    // 현재 선택된 변환 옵션들
    private FFmpegManager.OutputFormat selectedFormat = FFmpegManager.OutputFormat.MP3;
    private FFmpegManager.AudioQuality selectedQuality = FFmpegManager.AudioQuality.MEDIUM;
    
    public MainViewModel(@NonNull Application application) {
        super(application);
        LoggerManager.logger("MainViewModel 생성");
        updateStatusMessage("앱이 시작되었습니다.");
    }
    
    /**
     * Activity와 연결 (Manager 초기화)
     */
    public void initializeWithActivity(AppCompatActivity activity) {
        if (activity == null) {
            updateErrorMessage("Activity가 null입니다.");
            return;
        }
        
        try {
            // Manager들 초기화
            permissionManager = new PermissionManager(activity);
            fileManager = new FileManager(activity);
            ffmpegManager = new FFmpegManager();
            audioPlayerManager = new AudioPlayerManager(activity);
            
            // FFmpeg 콜백 설정
            setupFFmpegCallbacks();
            
            // AudioPlayer 콜백 설정
            setupAudioPlayerCallbacks();
            
            // 권한 상태 확인
            checkPermissions();
            
            updateStatusMessage("초기화 완료");
            LoggerManager.logger("MainViewModel 초기화 완료");
            
        } catch (Exception e) {
            String error = "초기화 실패: " + e.getMessage();
            LoggerManager.logger(error);
            updateErrorMessage(error);
        }
    }
    
    /**
     * FFmpeg 콜백 설정
     */
    private void setupFFmpegCallbacks() {
        ffmpegManager.setOnStartListener(() -> {
            isConverting.postValue(true);
            conversionProgress.postValue(0);
            updateStatusMessage("변환 시작");
        });
        
        ffmpegManager.setOnProgressListener(progress -> {
            conversionProgress.postValue(progress);
            updateStatusMessage("변환 중... " + progress + "%");
        });
        
        ffmpegManager.setOnCompletionListener((inputPath, outputPath) -> {
            isConverting.postValue(false);
            conversionProgress.postValue(100);
            convertedFilePath.postValue(outputPath);
            updateStatusMessage("변환 완료!");
            
            // 변환된 파일을 자동으로 플레이어에 로드
            loadAudioToPlayer(outputPath);
        });
        
        ffmpegManager.setOnFailureListener((message, reason) -> {
            isConverting.postValue(false);
            conversionProgress.postValue(0);
            String error = "변환 실패: " + message + " (" + reason + ")";
            updateErrorMessage(error);
            updateStatusMessage("변환 실패");
        });
    }
    
    /**
     * AudioPlayer 콜백 설정
     */
    private void setupAudioPlayerCallbacks() {
        audioPlayerManager.setOnPlayerStateChangeListener(new AudioPlayerManager.OnPlayerStateChangeListener() {
            @Override
            public void onStateChanged(AudioPlayerManager.PlaybackState newState) {
                switch (newState) {
                    case PREPARING:
                        updateStatusMessage("오디오 준비 중...");
                        break;
                    case PREPARED:
                        updateStatusMessage("재생 준비 완료");
                        break;
                    case PLAYING:
                        updateStatusMessage("재생 중");
                        break;
                    case PAUSED:
                        updateStatusMessage("일시정지");
                        break;
                    case STOPPED:
                        updateStatusMessage("정지됨");
                        break;
                    case ERROR:
                        updateStatusMessage("플레이어 오류");
                        break;
                }
            }
            
            @Override
            public void onError(String error) {
                updateErrorMessage("플레이어 오류: " + error);
            }
        });
        
        audioPlayerManager.setOnCompletionListener(() -> {
            updateStatusMessage("재생 완료");
        });
    }
    
    /**
     * 권한 확인 및 요청
     */
    public void checkPermissions() {
        if (permissionManager == null) return;
        
        boolean hasPermissions = permissionManager.hasAllRequiredPermissions();
        hasRequiredPermissions.postValue(hasPermissions);
        
        String statusText = permissionManager.getPermissionStatusString();
        permissionStatusText.postValue(statusText);
        
        if (hasPermissions) {
            updateStatusMessage("필수 권한이 허용되었습니다. 앱을 정상적으로 사용할 수 있습니다.");
        } else {
            updateStatusMessage("미디어 파일 접근 권한이 필요합니다.");
        }
    }
    
    /**
     * 권한 요청 (기존 메서드 - 하위 호환성 유지)
     */
    public void requestPermissions() {
        requestPermissions(null);
    }
    
    /**
     * 권한 요청 with 완료 콜백
     */
    public void requestPermissions(com.devc.lab.audios.activity.MainActivity.PermissionRequestCallback activityCallback) {
        if (permissionManager == null) {
            updateErrorMessage("PermissionManager가 초기화되지 않음");
            if (activityCallback != null) {
                activityCallback.onPermissionRequestCompleted();
            }
            return;
        }
        
        // 이미 권한 요청 중인 경우 중복 요청 방지
        if (isPermissionRequesting) {
            LoggerManager.logger("권한 요청이 이미 진행 중입니다. 중복 요청 무시");
            if (activityCallback != null) {
                activityCallback.onPermissionRequestCompleted();
            }
            return;
        }
        
        isPermissionRequesting = true;
        LoggerManager.logger("권한 요청 시작");
        
        permissionManager.requestStoragePermissions(new PermissionManager.PermissionCallback() {
            @Override
            public void onPermissionGranted(List<String> grantedPermissions) {
                isPermissionRequesting = false; // 플래그 리셋
                hasRequiredPermissions.postValue(true);
                updateStatusMessage("미디어 접근 권한이 허용되었습니다. 파일 변환을 시작할 수 있습니다.");
                LoggerManager.logger("권한 요청 완료 - 허용됨: " + grantedPermissions);
                
                // 상태 업데이트는 불필요한 재귀 호출을 방지하기 위해 제거
                // checkPermissions(); // 제거됨
                
                // 완료 콜백 호출
                if (activityCallback != null) {
                    activityCallback.onPermissionRequestCompleted();
                }
            }
            
            @Override
            public void onPermissionDenied(List<String> deniedPermissions) {
                isPermissionRequesting = false; // 플래그 리셋
                hasRequiredPermissions.postValue(false);
                updateStatusMessage("미디어 파일 접근 권한이 거부되었습니다. 설정에서 허용해주세요.");
                LoggerManager.logger("권한 요청 완료 - 거부됨: " + deniedPermissions);
                
                // 완료 콜백 호출
                if (activityCallback != null) {
                    activityCallback.onPermissionRequestCompleted();
                }
            }
            
            @Override
            public void onPermissionPermanentlyDenied(List<String> deniedPermissions) {
                isPermissionRequesting = false; // 플래그 리셋
                hasRequiredPermissions.postValue(false);
                updateStatusMessage("권한이 영구적으로 거부되었습니다. 안드로이드 설정 → 앱 → Audios → 권한에서 허용해주세요.");
                LoggerManager.logger("권한 요청 완료 - 영구적으로 거부됨: " + deniedPermissions);
                
                // 완료 콜백 호출
                if (activityCallback != null) {
                    activityCallback.onPermissionRequestCompleted();
                }
            }
        });
    }
    
    /**
     * 앱 설정 화면 열기
     */
    public void openAppSettings() {
        if (permissionManager != null) {
            permissionManager.openAppSettings();
        }
    }
    
    /**
     * 선택된 파일 설정 (Fragment에서 파일 선택 후 호출)
     */
    public void setSelectedFile(Uri fileUri, String fileName) {
        if (fileUri == null || fileName == null || fileName.isEmpty()) {
            updateErrorMessage("유효하지 않은 파일 정보입니다.");
            return;
        }
        
        selectedFileUri.postValue(fileUri);
        selectedFileName.postValue(fileName);
        updateStatusMessage("파일 선택됨: " + fileName);
        LoggerManager.logger("파일 선택됨: " + fileName + " (" + fileUri + ")");
    }
    
    /**
     * 파일 선택 취소 처리
     */
    public void onFileSelectionCanceled() {
        updateStatusMessage("파일 선택이 취소되었습니다.");
    }
    
    /**
     * 파일 선택 오류 처리  
     */
    public void onFileSelectionError(String error) {
        updateErrorMessage("파일 선택 오류: " + error);
    }
    
    /**
     * 변환 시작
     */
    public void startConversion() {
        Uri fileUri = selectedFileUri.getValue();
        String fileName = selectedFileName.getValue();
        
        if (fileUri == null || fileName == null || fileName.isEmpty()) {
            updateErrorMessage("먼저 파일을 선택해주세요.");
            return;
        }
        
        if (ffmpegManager == null) {
            updateErrorMessage("FFmpegManager가 초기화되지 않음");
            return;
        }
        
        if (ffmpegManager.isConverting()) {
            updateStatusMessage("이미 변환이 진행 중입니다.");
            return;
        }
        
        try {
            Context context = getApplication().getApplicationContext();
            
            // 출력 파일명 생성 (확장자 제거 후 선택된 포맷 확장자 추가)
            String outputFileName = fileName.replaceFirst("\\.[^.]+$", "") + selectedFormat.getExtension();
            
            ffmpegManager.extractAudioFromVideoUri(
                fileUri, 
                outputFileName, 
                selectedFormat, 
                selectedQuality, 
                context
            );
            
            LoggerManager.logger("변환 시작 - 포맷: " + selectedFormat + ", 품질: " + selectedQuality);
            
        } catch (Exception e) {
            String error = "변환 시작 실패: " + e.getMessage();
            LoggerManager.logger(error);
            updateErrorMessage(error);
        }
    }
    
    /**
     * 변환 취소
     */
    public void cancelConversion() {
        if (ffmpegManager != null) {
            ffmpegManager.cancelConversion();
            updateStatusMessage("변환 취소됨");
        }
    }
    
    /**
     * 오디오를 플레이어에 로드
     */
    private void loadAudioToPlayer(String filePath) {
        if (audioPlayerManager != null && filePath != null && !filePath.isEmpty()) {
            audioPlayerManager.loadAudio(filePath);
            LoggerManager.logger("플레이어에 오디오 로드: " + filePath);
        }
    }
    
    /**
     * 오디오 재생/일시정지 토글
     */
    public void togglePlayback() {
        if (audioPlayerManager == null) return;
        
        if (audioPlayerManager.isCurrentlyPlaying()) {
            audioPlayerManager.pause();
        } else {
            audioPlayerManager.play();
        }
    }
    
    /**
     * 오디오 정지
     */
    public void stopPlayback() {
        if (audioPlayerManager != null) {
            audioPlayerManager.stop();
        }
    }
    
    /**
     * 오디오 위치 이동
     */
    public void seekTo(int positionMs) {
        if (audioPlayerManager != null) {
            audioPlayerManager.seekTo(positionMs);
        }
    }
    
    /**
     * 출력 포맷 설정
     */
    public void setOutputFormat(FFmpegManager.OutputFormat format) {
        this.selectedFormat = format;
        updateStatusMessage("출력 포맷: " + format.name());
    }
    
    /**
     * 오디오 품질 설정
     */
    public void setAudioQuality(FFmpegManager.AudioQuality quality) {
        this.selectedQuality = quality;
        updateStatusMessage("오디오 품질: " + quality.getBitrate() + "kbps");
    }
    
    /**
     * 상태 메시지 업데이트
     */
    private void updateStatusMessage(String message) {
        statusMessage.postValue(message);
        LoggerManager.logger("Status: " + message);
    }
    
    /**
     * 에러 메시지 업데이트
     */
    private void updateErrorMessage(String error) {
        errorMessage.postValue(error);
        LoggerManager.logger("Error: " + error);
    }
    
    // LiveData Getters (UI 바인딩용)
    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }
    
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }
    
    public LiveData<Uri> getSelectedFileUri() {
        return selectedFileUri;
    }
    
    public LiveData<String> getSelectedFileName() {
        return selectedFileName;
    }
    
    public LiveData<Integer> getConversionProgress() {
        return conversionProgress;
    }
    
    public LiveData<Boolean> getIsConverting() {
        return isConverting;
    }
    
    public LiveData<String> getConvertedFilePath() {
        return convertedFilePath;
    }
    
    public LiveData<Boolean> getHasRequiredPermissions() {
        return hasRequiredPermissions;
    }
    
    public LiveData<String> getPermissionStatusText() {
        return permissionStatusText;
    }
    
    // AudioPlayer LiveData 위임
    public LiveData<AudioPlayerManager.PlaybackState> getPlaybackState() {
        return audioPlayerManager != null ? audioPlayerManager.getPlaybackState() : new MutableLiveData<>();
    }
    
    public LiveData<Integer> getCurrentPosition() {
        return audioPlayerManager != null ? audioPlayerManager.getCurrentPositionLiveData() : new MutableLiveData<>();
    }
    
    public LiveData<Integer> getDuration() {
        return audioPlayerManager != null ? audioPlayerManager.getDurationLiveData() : new MutableLiveData<>();
    }
    
    public LiveData<Boolean> getIsPlaying() {
        return audioPlayerManager != null ? audioPlayerManager.getIsPlaying() : new MutableLiveData<>();
    }
    
    // Getters
    public FFmpegManager.OutputFormat getSelectedFormat() {
        return selectedFormat;
    }
    
    public FFmpegManager.AudioQuality getSelectedQuality() {
        return selectedQuality;
    }
    
    public FFmpegManager.OutputFormat[] getSupportedFormats() {
        return ffmpegManager != null ? ffmpegManager.getSupportedFormats() : new FFmpegManager.OutputFormat[0];
    }
    
    public FFmpegManager.AudioQuality[] getSupportedQualities() {
        return ffmpegManager != null ? ffmpegManager.getSupportedQualities() : new FFmpegManager.AudioQuality[0];
    }
    
    /**
     * ViewModel 정리
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        
        try {
            // 리소스 정리
            if (ffmpegManager != null) {
                ffmpegManager.cleanup();
            }
            
            if (audioPlayerManager != null) {
                audioPlayerManager.release();
            }
            
            LoggerManager.logger("MainViewModel 정리 완료");
            
        } catch (Exception e) {
            LoggerManager.logger("MainViewModel 정리 실패: " + e.getMessage());
        }
    }
}
