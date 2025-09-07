package com.devc.lab.audios.manager;

import android.content.Context;
import android.net.Uri;
import com.devc.lab.audios.manager.LoggerManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * 오디오 자르기 기능을 관리하는 싱글톤 매니저
 */
public class AudioTrimManager {
    
    private static AudioTrimManager instance;
    private Context context;
    
    // 콜백 인터페이스
    private OnTrimStartListener onStartListener;
    private OnTrimProgressListener onProgressListener;
    private OnTrimCompletionListener onCompletionListener;
    private OnTrimErrorListener onErrorListener;
    
    // 인터페이스 정의
    public interface OnTrimStartListener {
        void onTrimStart();
    }
    
    public interface OnTrimProgressListener {
        void onTrimProgress(int progress);
    }
    
    public interface OnTrimCompletionListener {
        void onTrimComplete(String outputPath);
    }
    
    public interface OnTrimErrorListener {
        void onTrimError(String error);
    }
    
    private AudioTrimManager() {
        // Private constructor for singleton
    }
    
    public static synchronized AudioTrimManager getInstance() {
        if (instance == null) {
            instance = new AudioTrimManager();
        }
        return instance;
    }
    
    public void init(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 오디오 파일 자르기
     * @param sourceUri 원본 파일 URI
     * @param startTimeMs 시작 시간 (밀리초)
     * @param endTimeMs 끝 시간 (밀리초)
     * @param outputFileName 출력 파일명
     */
    public void trimAudio(Uri sourceUri, long startTimeMs, long endTimeMs, String outputFileName) {
        // Phase 3: Native API로 위임
        NativeAudioTrimManager nativeTrimmer = NativeAudioTrimManager.getInstance();
        setupNativeTrimmerCallbacks(nativeTrimmer);
        nativeTrimmer.trimAudio(sourceUri, startTimeMs, endTimeMs, outputFileName);
    }
    
    /**
     * 오디오 파일 자르기 (비율 기반)
     * @param sourceUri 원본 파일 URI
     * @param startRatio 시작 위치 비율 (0.0 ~ 1.0)
     * @param endRatio 끝 위치 비율 (0.0 ~ 1.0)
     * @param durationMs 전체 길이 (밀리초)
     * @param outputFileName 출력 파일명
     */
    public void trimAudioByRatio(Uri sourceUri, float startRatio, float endRatio, 
                                  long durationMs, String outputFileName) {
        // Phase 3: Native API로 위임
        NativeAudioTrimManager nativeTrimmer = NativeAudioTrimManager.getInstance();
        setupNativeTrimmerCallbacks(nativeTrimmer);
        nativeTrimmer.trimAudioByRatio(sourceUri, startRatio, endRatio, durationMs, outputFileName);
    }
    
    /**
     * URI에서 임시 파일 생성 (Scoped Storage 호환)
     */
    private File createTempFileFromUri(Uri uri) {
        try {
            // 임시 디렉토리
            File tempDir = new File(context.getCacheDir(), "temp_audio");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            
            // 임시 파일
            File tempFile = new File(tempDir, "temp_" + System.currentTimeMillis() + ".tmp");
            
            // URI에서 파일로 복사
            try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                
                if (inputStream == null) {
                    LoggerManager.logger("URI에서 InputStream을 열 수 없음");
                    return null;
                }
                
                byte[] buffer = new byte[8 * 1024];
                int bytesRead;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                
                outputStream.flush();
                LoggerManager.logger("임시 파일 생성 완료: " + tempFile.getAbsolutePath());
                return tempFile;
                
            } catch (IOException e) {
                LoggerManager.logger("임시 파일 생성 실패: " + e.getMessage());
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                return null;
            }
            
        } catch (Exception e) {
            LoggerManager.logger("임시 파일 생성 중 오류: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 밀리초를 FFmpeg 시간 형식으로 변환
     * @param milliseconds 밀리초
     * @return HH:MM:SS.mmm 형식 문자열
     */
    private String formatTimeForFFmpeg(long milliseconds) {
        long hours = milliseconds / 3600000;
        long minutes = (milliseconds % 3600000) / 60000;
        long seconds = (milliseconds % 60000) / 1000;
        long millis = milliseconds % 1000;
        
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", 
                            hours, minutes, seconds, millis);
    }
    
    /**
     * 초 단위 시간을 밀리초로 변환
     */
    public long secondsToMillis(float seconds) {
        return (long) (seconds * 1000);
    }
    
    /**
     * 시간 문자열 (MM:SS) 을 밀리초로 변환
     */
    public long timeStringToMillis(String timeString) {
        try {
            String[] parts = timeString.split(":");
            if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                return (minutes * 60 + seconds) * 1000L;
            }
        } catch (Exception e) {
            LoggerManager.logger("시간 문자열 변환 오류: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * 밀리초를 시간 문자열 (MM:SS) 로 변환
     */
    public String millisToTimeString(long millis) {
        int totalSeconds = (int) (millis / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }
    
    // 리스너 설정 메서드들
    
    public void setOnStartListener(OnTrimStartListener listener) {
        this.onStartListener = listener;
    }
    
    public void setOnProgressListener(OnTrimProgressListener listener) {
        this.onProgressListener = listener;
    }
    
    public void setOnCompletionListener(OnTrimCompletionListener listener) {
        this.onCompletionListener = listener;
    }
    
    public void setOnErrorListener(OnTrimErrorListener listener) {
        this.onErrorListener = listener;
    }
    
    /**
     * 모든 리스너 제거
     */
    public void clearListeners() {
        onStartListener = null;
        onProgressListener = null;
        onCompletionListener = null;
        onErrorListener = null;
    }
    
    /**
     * Phase 3: Native 자르기 관리자에 콜백 설정
     */
    private void setupNativeTrimmerCallbacks(NativeAudioTrimManager nativeTrimmer) {
        nativeTrimmer.setOnStartListener(() -> {
            if (onStartListener != null) {
                onStartListener.onTrimStart();
            }
        });
        
        nativeTrimmer.setOnProgressListener((progress) -> {
            if (onProgressListener != null) {
                onProgressListener.onTrimProgress(progress);
            }
        });
        
        nativeTrimmer.setOnCompletionListener((outputPath) -> {
            if (onCompletionListener != null) {
                onCompletionListener.onTrimComplete(outputPath);
            }
        });
        
        nativeTrimmer.setOnErrorListener((error) -> {
            if (onErrorListener != null) {
                onErrorListener.onTrimError(error);
            }
        });
    }
}