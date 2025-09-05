package com.devc.lab.audios.manager;

import android.content.Context;
import android.net.Uri;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
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
        if (context == null) {
            LoggerManager.logger("AudioTrimManager가 초기화되지 않음");
            if (onErrorListener != null) {
                onErrorListener.onTrimError("AudioTrimManager가 초기화되지 않았습니다");
            }
            return;
        }
        
        // 임시 입력 파일 생성 (Scoped Storage 호환)
        File tempInputFile = createTempFileFromUri(sourceUri);
        if (tempInputFile == null) {
            if (onErrorListener != null) {
                onErrorListener.onTrimError("입력 파일을 준비할 수 없습니다");
            }
            return;
        }
        
        // 출력 디렉토리 확인
        File outputDir = new File(context.getExternalFilesDir(null), "Audios/Edited");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        // 출력 파일 경로
        File outputFile = new File(outputDir, outputFileName);
        
        // 시간 포맷 변환 (밀리초 -> HH:MM:SS.mmm)
        String startTime = formatTimeForFFmpeg(startTimeMs);
        String duration = formatTimeForFFmpeg(endTimeMs - startTimeMs);
        
        // FFmpeg 명령어 생성
        String command = String.format(
            "-i %s -ss %s -t %s -acodec copy %s",
            tempInputFile.getAbsolutePath(),
            startTime,
            duration,
            outputFile.getAbsolutePath()
        );
        
        LoggerManager.logger("FFmpeg 자르기 명령어: " + command);
        
        // 콜백 시작
        if (onStartListener != null) {
            onStartListener.onTrimStart();
        }
        
        // FFmpegKit을 사용한 비동기 실행
        FFmpegKit.executeAsync(command, session -> {
            // 임시 파일 삭제
            if (tempInputFile.exists()) {
                tempInputFile.delete();
            }
            
            if (ReturnCode.isSuccess(session.getReturnCode())) {
                LoggerManager.logger("자르기 성공: " + outputFile.getAbsolutePath());
                if (onCompletionListener != null) {
                    onCompletionListener.onTrimComplete(outputFile.getAbsolutePath());
                }
            } else {
                String error = "자르기 실패 (코드: " + session.getReturnCode() + ")";
                LoggerManager.logger(error);
                if (onErrorListener != null) {
                    onErrorListener.onTrimError(error);
                }
            }
        });
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
        long startTimeMs = (long) (startRatio * durationMs);
        long endTimeMs = (long) (endRatio * durationMs);
        
        trimAudio(sourceUri, startTimeMs, endTimeMs, outputFileName);
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
}