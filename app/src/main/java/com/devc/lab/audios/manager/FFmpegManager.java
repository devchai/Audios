package com.devc.lab.audios.manager;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.Level;
import com.arthenica.ffmpegkit.Statistics;
import com.arthenica.ffmpegkit.StatisticsCallback;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.SessionState;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * FFmpeg 기반 미디어 변환 관리자
 * 비디오를 오디오로 변환하는 핵심 기능 제공
 * 비동기 처리 및 진행률 콜백 시스템 구현
 */
public class FFmpegManager {
    
    // 콜백 인터페이스들
    public interface OnStartListener {
        void onFFmpegManagerStart();
    }

    public interface OnProgressListener {
        void onFFmpegManagerProgress(int progress);
    }

    public interface OnCompletionListener {
        void onFFmpegManagerCompletion(String inputPath, String outputPath);
    }

    public interface OnFailureListener {
        void onFFmpegManagerFailure(String message, String failureReason);
    }
    
    // 출력 포맷 열거형
    public enum OutputFormat {
        MP3("mp3", "libmp3lame", ".mp3"),
        AAC("aac", "aac", ".aac"),
        WAV("wav", "pcm_s16le", ".wav"),
        FLAC("flac", "flac", ".flac"),
        OGG("ogg", "libvorbis", ".ogg");
        
        private final String format;
        private final String codec;
        private final String extension;
        
        OutputFormat(String format, String codec, String extension) {
            this.format = format;
            this.codec = codec;
            this.extension = extension;
        }
        
        public String getFormat() { return format; }
        public String getCodec() { return codec; }
        public String getExtension() { return extension; }
    }
    
    // 품질 설정 열거형
    public enum AudioQuality {
        LOW(96),       // 96 kbps
        MEDIUM(128),   // 128 kbps  
        HIGH(192),     // 192 kbps
        VERY_HIGH(320); // 320 kbps
        
        private final int bitrate;
        
        AudioQuality(int bitrate) {
            this.bitrate = bitrate;
        }
        
        public int getBitrate() { return bitrate; }
    }

    private OnStartListener onStartListener;
    private OnProgressListener onProgressListener;
    private OnCompletionListener onCompletionListener;
    private OnFailureListener onFailureListener;

    private FileManager fileManager;
    private ExecutorService executorService;
    private Future<?> currentTask;
    private Handler mainHandler;
    
    // 진행률 추적 변수들
    private long totalDurationMs = 0;
    private boolean isConverting = false;

    public FFmpegManager() {
        fileManager = new FileManager();
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // FFmpeg 설정 초기화
        initializeFFmpeg();
    }

    /**
     * FFmpeg 초기화
     */
    private void initializeFFmpeg() {
        try {
            // FFmpeg 로그 레벨 설정
            FFmpegKitConfig.setLogLevel(Level.AV_LOG_INFO);
            
            // 통계 콜백 설정 (진행률 추적용)
            FFmpegKitConfig.enableStatisticsCallback(new StatisticsCallback() {
                @Override
                public void apply(Statistics statistics) {
                    updateProgress(statistics);
                }
            });
            
            LoggerManager.logger("FFmpeg 초기화 완료");
            
        } catch (Exception e) {
            LoggerManager.logger("FFmpeg 초기화 실패: " + e.getMessage());
        }
    }
    
    /**
     * 비디오에서 오디오 추출 (파일 경로 기반)
     */
    public void extractAudioFromVideo(String fileName, String inputPath, String outputPath, Context context) {
        extractAudioFromVideo(inputPath, outputPath, OutputFormat.MP3, AudioQuality.MEDIUM, context);
    }
    
    /**
     * URI에서 오디오 추출 (Scoped Storage 호환)
     */
    public void extractAudioFromVideoUri(Uri inputUri, String outputFileName, OutputFormat format, 
                                       AudioQuality quality, Context context) {
        if (inputUri == null || context == null) {
            notifyFailure("입력 파라미터 오류", "URI 또는 Context가 null입니다.");
            return;
        }
        
        currentTask = executorService.submit(() -> {
            try {
                // URI를 임시 파일로 복사
                File tempInputFile = fileManager.copyToTemporaryFile(inputUri, context);
                String inputPath = tempInputFile.getAbsolutePath();
                
                // 출력 파일 경로 생성
                String outputPath = fileManager.createOutputFilePath(context, outputFileName, format.getExtension());
                
                // 실제 변환 실행
                performConversion(inputPath, outputPath, format, quality, context);
                
                // 임시 파일 정리
                if (tempInputFile.exists()) {
                    tempInputFile.delete();
                    LoggerManager.logger("임시 입력 파일 삭제: " + tempInputFile.getName());
                }
                
            } catch (Exception e) {
                LoggerManager.logger("URI 변환 실패: " + e.getMessage());
                notifyFailure("변환 실패", e.getMessage());
            }
        });
    }
    
    /**
     * 비디오에서 오디오 추출 (고급 옵션 지원)
     */
    public void extractAudioFromVideo(String inputPath, String outputPath, OutputFormat format, 
                                    AudioQuality quality, Context context) {
        if (inputPath == null || outputPath == null || context == null) {
            notifyFailure("입력 파라미터 오류", "필수 파라미터가 null입니다.");
            return;
        }
        
        if (isConverting) {
            notifyFailure("변환 진행 중", "다른 변환이 진행 중입니다.");
            return;
        }
        
        currentTask = executorService.submit(() -> {
            performConversion(inputPath, outputPath, format, quality, context);
        });
    }
    
    /**
     * 실제 FFmpeg 변환 실행
     */
    private void performConversion(String inputPath, String outputPath, OutputFormat format, 
                                 AudioQuality quality, Context context) {
        try {
            isConverting = true;
            
            // 변환 시작 알림
            notifyStart();
            
            // 입력 파일 기간 가져오기 (진행률 계산용)
            totalDurationMs = getMediaDuration(inputPath);
            LoggerManager.logger("입력 파일 기간: " + totalDurationMs + "ms");
            
            // FFmpeg 명령어 생성
            String[] command = buildFFmpegCommand(inputPath, outputPath, format, quality);
            
            LoggerManager.logger("FFmpeg 명령어: " + String.join(" ", command));
            
            // FFmpeg 실행
            FFmpegSession session = FFmpegKit.execute(String.join(" ", command));
            
            if (ReturnCode.isSuccess(session.getReturnCode())) {
                LoggerManager.logger("변환 성공: " + outputPath);
                
                // MediaStore에 저장 (선택적)
                String fileName = new File(outputPath).getName();
                Uri savedUri = fileManager.saveToDownloadsWithMediaStore(context, outputPath, fileName);
                
                notifyCompletion(inputPath, outputPath);
                
            } else if (ReturnCode.isCancel(session.getReturnCode())) {
                LoggerManager.logger("변환 취소됨");
                notifyFailure("변환 취소", "사용자에 의해 변환이 취소되었습니다.");
                
            } else {
                String errorMessage = "변환 실패 (Return Code: " + session.getReturnCode() + ")";
                LoggerManager.logger(errorMessage);
                notifyFailure("변환 실패", errorMessage);
            }
            
        } catch (Exception e) {
            LoggerManager.logger("변환 중 오류 발생: " + e.getMessage());
            notifyFailure("변환 오류", e.getMessage());
            
        } finally {
            isConverting = false;
        }
    }
    
    /**
     * FFmpeg 명령어 생성
     */
    private String[] buildFFmpegCommand(String inputPath, String outputPath, 
                                      OutputFormat format, AudioQuality quality) {
        switch (format) {
            case MP3:
                return new String[]{
                    "-i", inputPath,
                    "-vn",                                    // 비디오 스트림 제거
                    "-acodec", format.getCodec(),            // 오디오 코덱
                    "-ab", quality.getBitrate() + "k",       // 비트레이트
                    "-ar", "44100",                          // 샘플레이트
                    "-y",                                     // 출력 파일 덮어쓰기
                    outputPath
                };
                
            case AAC:
                return new String[]{
                    "-i", inputPath,
                    "-vn",
                    "-acodec", format.getCodec(),
                    "-b:a", quality.getBitrate() + "k",
                    "-ar", "44100",
                    "-y",
                    outputPath
                };
                
            case WAV:
                return new String[]{
                    "-i", inputPath,
                    "-vn",
                    "-acodec", format.getCodec(),
                    "-ar", "44100",
                    "-y",
                    outputPath
                };
                
            case FLAC:
                return new String[]{
                    "-i", inputPath,
                    "-vn",
                    "-acodec", format.getCodec(),
                    "-ar", "44100",
                    "-y",
                    outputPath
                };
                
            case OGG:
                return new String[]{
                    "-i", inputPath,
                    "-vn",
                    "-acodec", format.getCodec(),
                    "-b:a", quality.getBitrate() + "k",
                    "-ar", "44100",
                    "-y",
                    outputPath
                };
                
            default:
                // 기본값: MP3
                return new String[]{
                    "-i", inputPath,
                    "-vn",
                    "-acodec", "libmp3lame",
                    "-ab", "128k",
                    "-ar", "44100",
                    "-y",
                    outputPath
                };
        }
    }
    
    /**
     * 진행률 업데이트 (Statistics 콜백)
     */
    private void updateProgress(Statistics statistics) {
        if (totalDurationMs > 0 && statistics != null) {
            long currentTimeMs = (long) statistics.getTime();
            int progress = (int) ((currentTimeMs * 100) / totalDurationMs);
            final int finalProgress = Math.min(Math.max(progress, 0), 100); // 0-100 범위로 제한
            
            if (onProgressListener != null) {
                mainHandler.post(() -> onProgressListener.onFFmpegManagerProgress(finalProgress));
            }
        }
    }
    
    /**
     * 변환 취소
     */
    public void cancelConversion() {
        try {
            if (currentTask != null && !currentTask.isDone()) {
                FFmpegKit.cancel();
                currentTask.cancel(true);
                LoggerManager.logger("변환 취소 요청됨");
            }
        } catch (Exception e) {
            LoggerManager.logger("변환 취소 실패: " + e.getMessage());
        }
    }
    
    /**
     * 현재 변환 중인지 확인
     */
    public boolean isConverting() {
        return isConverting;
    }

    /**
     * 콜백 알림 메서드들
     */
    private void notifyStart() {
        if (onStartListener != null) {
            mainHandler.post(() -> onStartListener.onFFmpegManagerStart());
        }
    }
    
    private void notifyCompletion(String inputPath, String outputPath) {
        if (onCompletionListener != null) {
            mainHandler.post(() -> onCompletionListener.onFFmpegManagerCompletion(inputPath, outputPath));
        }
    }
    
    private void notifyFailure(String message, String reason) {
        if (onFailureListener != null) {
            mainHandler.post(() -> onFailureListener.onFFmpegManagerFailure(message, reason));
        }
    }
    
    /**
     * 미디어 파일 기간 가져오기 (개선된 버전)
     */
    public long getMediaDuration(String filePath) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(filePath);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            
            if (durationStr != null && !durationStr.isEmpty()) {
                return Long.parseLong(durationStr);
            } else {
                LoggerManager.logger("미디어 파일 기간 정보 없음: " + filePath);
                return 0;
            }
            
        } catch (Exception e) {
            LoggerManager.logger("미디어 파일 기간 가져오기 실패: " + e.getMessage());
            return 0;
            
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    LoggerManager.logger("MediaMetadataRetriever 해제 실패: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 미디어 파일 정보 가져오기
     */
    public MediaInfo getMediaInfo(String filePath) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(filePath);
            
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            
            return new MediaInfo(duration, bitrate, title, artist, width, height);
            
        } catch (Exception e) {
            LoggerManager.logger("미디어 정보 가져오기 실패: " + e.getMessage());
            return null;
            
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    LoggerManager.logger("MediaMetadataRetriever 해제 실패: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 지원되는 출력 포맷 목록 반환
     */
    public OutputFormat[] getSupportedFormats() {
        return OutputFormat.values();
    }
    
    /**
     * 지원되는 품질 옵션 목록 반환
     */
    public AudioQuality[] getSupportedQualities() {
        return AudioQuality.values();
    }
    
    /**
     * 리소스 정리
     */
    public void cleanup() {
        try {
            // 진행 중인 작업 취소
            cancelConversion();
            
            // ExecutorService 종료
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
            }
            
            // FFmpeg 통계 콜백 비활성화
            FFmpegKitConfig.enableStatisticsCallback(null);
            
            LoggerManager.logger("FFmpegManager 리소스 정리 완료");
            
        } catch (Exception e) {
            LoggerManager.logger("FFmpegManager 리소스 정리 실패: " + e.getMessage());
        }
    }
    
    // Setter 메서드들
    public void setOnStartListener(OnStartListener onStartListener) {
        this.onStartListener = onStartListener;
    }

    public void setOnProgressListener(OnProgressListener onProgressListener) {
        this.onProgressListener = onProgressListener;
    }

    public void setOnCompletionListener(OnCompletionListener onCompletionListener) {
        this.onCompletionListener = onCompletionListener;
    }

    public void setOnFailureListener(OnFailureListener onFailureListener) {
        this.onFailureListener = onFailureListener;
    }
    
    /**
     * 미디어 파일 정보를 담는 데이터 클래스
     */
    public static class MediaInfo {
        public final long durationMs;
        public final int bitrateKbps;
        public final String title;
        public final String artist;
        public final int videoWidth;
        public final int videoHeight;
        public final boolean hasVideo;
        
        public MediaInfo(String duration, String bitrate, String title, String artist, 
                        String width, String height) {
            this.durationMs = duration != null ? Long.parseLong(duration) : 0;
            this.bitrateKbps = bitrate != null ? Integer.parseInt(bitrate) / 1000 : 0;
            this.title = title;
            this.artist = artist;
            this.videoWidth = width != null ? Integer.parseInt(width) : 0;
            this.videoHeight = height != null ? Integer.parseInt(height) : 0;
            this.hasVideo = this.videoWidth > 0 && this.videoHeight > 0;
        }
        
        public String getDurationFormatted() {
            long seconds = durationMs / 1000;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%02d:%02d", minutes, seconds);
        }
        
        public String getResolution() {
            if (hasVideo) {
                return videoWidth + "x" + videoHeight;
            } else {
                return "오디오 전용";
            }
        }
        
        @Override
        public String toString() {
            return "MediaInfo{" +
                    "duration=" + getDurationFormatted() +
                    ", bitrate=" + bitrateKbps + "kbps" +
                    ", title='" + title + '\'' +
                    ", artist='" + artist + '\'' +
                    ", resolution=" + getResolution() +
                    '}';
        }
    }
}
