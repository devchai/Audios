package com.devc.lab.audios.manager;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Native API 기반 통합 오디오 변환 관리자
 * FFmpegManager를 대체하여 모든 Native API들을 통합 관리
 * Phase 4: FFmpeg 완전 제거 및 Native API 통합
 */
public class AudioConversionManager {
    
    // 콜백 인터페이스들 (FFmpegManager 호환성 유지)
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
    
    // 출력 포맷 열거형 (FFmpegManager 호환)
    public enum OutputFormat {
        MP3("mp3", "audio/mpeg", ".mp3"),
        AAC("aac", "audio/mp4", ".aac"), 
        WAV("wav", "audio/wav", ".wav"),
        M4A("m4a", "audio/mp4", ".m4a"),
        FLAC("flac", "audio/flac", ".flac"),
        OGG("ogg", "audio/ogg", ".ogg");
        
        private final String format;
        private final String mimeType;
        private final String extension;
        
        OutputFormat(String format, String mimeType, String extension) {
            this.format = format;
            this.mimeType = mimeType;
            this.extension = extension;
        }
        
        public String getFormat() { return format; }
        public String getMimeType() { return mimeType; }
        public String getExtension() { return extension; }
    }
    
    // 품질 설정 열거형 (FFmpegManager 호환)
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

    private ExecutorService executorService;
    private Future<?> currentTask;
    private Handler mainHandler;
    
    // Native 매니저들 참조
    private NativeMediaInfoManager mediaInfoManager;
    private NativeAudioExtractorManager extractorManager;
    private NativeAudioTrimManager trimManager;
    
    // 상태 추적
    private boolean isConverting = false;

    public AudioConversionManager() {
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Native 매니저들 초기화
        initializeNativeManagers();
        
        LoggerManager.logger("AudioConversionManager 초기화 완료");
    }

    /**
     * Native 매니저들 초기화 및 콜백 설정
     */
    private void initializeNativeManagers() {
        mediaInfoManager = NativeMediaInfoManager.getInstance();
        extractorManager = NativeAudioExtractorManager.getInstance();
        trimManager = NativeAudioTrimManager.getInstance();
        
        LoggerManager.logger("AudioConversionManager - Native 매니저들 인스턴스 생성 완료");
        
        // 추출기 콜백 설정
        setupExtractorCallbacks();
        
        // 자르기 관리자 콜백 설정
        setupTrimmerCallbacks();
        
        LoggerManager.logger("AudioConversionManager - 콜백 설정 완료");
    }
    
    /**
     * 추출기 콜백 설정
     */
    private void setupExtractorCallbacks() {
        extractorManager.setOnStartListener(() -> {
            isConverting = true;
            if (onStartListener != null) {
                mainHandler.post(() -> onStartListener.onFFmpegManagerStart());
            }
        });
        
        extractorManager.setOnProgressListener(progress -> {
            if (onProgressListener != null) {
                mainHandler.post(() -> onProgressListener.onFFmpegManagerProgress(progress));
            }
        });
        
        extractorManager.setOnCompletionListener(outputPath -> {
            isConverting = false;
            if (onCompletionListener != null) {
                mainHandler.post(() -> onCompletionListener.onFFmpegManagerCompletion("", outputPath));
            }
        });
        
        extractorManager.setOnErrorListener(error -> {
            isConverting = false;
            if (onFailureListener != null) {
                mainHandler.post(() -> onFailureListener.onFFmpegManagerFailure("변환 실패", error));
            }
        });
    }
    
    /**
     * 자르기 관리자 콜백 설정
     */
    private void setupTrimmerCallbacks() {
        trimManager.setOnStartListener(() -> {
            isConverting = true;
            if (onStartListener != null) {
                mainHandler.post(() -> onStartListener.onFFmpegManagerStart());
            }
        });
        
        trimManager.setOnProgressListener(progress -> {
            if (onProgressListener != null) {
                mainHandler.post(() -> onProgressListener.onFFmpegManagerProgress(progress));
            }
        });
        
        trimManager.setOnCompletionListener(outputPath -> {
            isConverting = false;
            if (onCompletionListener != null) {
                mainHandler.post(() -> onCompletionListener.onFFmpegManagerCompletion("", outputPath));
            }
        });
        
        trimManager.setOnErrorListener(error -> {
            isConverting = false;
            if (onFailureListener != null) {
                mainHandler.post(() -> onFailureListener.onFFmpegManagerFailure("편집 실패", error));
            }
        });
    }
    
    /**
     * URI에서 오디오 추출 (FFmpegManager 호환 메서드)
     */
    public void extractAudioFromVideoUri(Uri inputUri, String outputFileName, OutputFormat format, 
                                       AudioQuality quality, Context context) {
        if (inputUri == null || context == null) {
            notifyFailure("입력 파라미터 오류", "URI 또는 Context가 null입니다.");
            return;
        }
        
        if (isConverting) {
            notifyFailure("변환 진행 중", "다른 변환이 진행 중입니다.");
            return;
        }
        
        // Native 매니저들에 Context 설정 (중요!)
        ensureNativeManagersInitialized(context);
        
        // Native API 지원 포맷으로 매핑
        NativeAudioExtractorManager.AudioFormat nativeFormat = mapToNativeFormat(format);
        
        // 파일명 확장자 처리 (기존 확장자 교체)
        String fileName = outputFileName;
        // 기존 확장자 제거
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            fileName = fileName.substring(0, lastDotIndex);
            LoggerManager.logger("기존 확장자 제거: " + outputFileName + " → " + fileName);
        }
        // 새로운 포맷 확장자 추가
        fileName += nativeFormat.getExtension();
        LoggerManager.logger("최종 파일명: " + fileName);
        
        LoggerManager.logger("오디오 추출 시작 - 포맷: " + format + ", 품질: " + quality + "kbps");
        extractorManager.extractAudioFromVideoUri(inputUri, fileName, nativeFormat);
    }
    
    /**
     * 파일 경로 기반 오디오 추출 (FFmpegManager 호환 메서드)
     */
    public void extractAudioFromVideo(String fileName, String inputPath, String outputPath, Context context) {
        if (inputPath == null || outputPath == null || context == null) {
            notifyFailure("입력 파라미터 오류", "필수 파라미터가 null입니다.");
            return;
        }
        
        if (isConverting) {
            notifyFailure("변환 진행 중", "다른 변환이 진행 중입니다.");
            return;
        }
        
        // Native 매니저들에 Context 설정 (중요!)
        ensureNativeManagersInitialized(context);
        
        LoggerManager.logger("오디오 추출 시작 - 입력: " + inputPath + ", 출력: " + outputPath);
        extractorManager.extractAudioFromVideo(inputPath, outputPath, NativeAudioExtractorManager.AudioFormat.M4A);
    }
    
    /**
     * 고급 옵션 지원 오디오 추출 (FFmpegManager 호환 메서드)
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
        
        // Native 매니저들에 Context 설정 (중요!)
        ensureNativeManagersInitialized(context);
        
        // Native API 지원 포맷으로 매핑
        NativeAudioExtractorManager.AudioFormat nativeFormat = mapToNativeFormat(format);
        
        LoggerManager.logger("오디오 추출 시작 - 포맷: " + format + ", 품질: " + quality + "kbps");
        extractorManager.extractAudioFromVideo(inputPath, outputPath, nativeFormat);
    }
    
    /**
     * 오디오 자르기 (시간 기반)
     */
    public void trimAudio(Uri sourceUri, long startTimeMs, long endTimeMs, String outputFileName, Context context) {
        if (sourceUri == null || outputFileName == null) {
            notifyFailure("입력 파라미터 오류", "소스 URI 또는 출력 파일명이 null입니다.");
            return;
        }
        
        if (isConverting) {
            notifyFailure("편집 진행 중", "다른 편집이 진행 중입니다.");
            return;
        }
        
        // Native 매니저들에 Context 설정 (중요!)
        ensureNativeManagersInitialized(context);
        
        LoggerManager.logger("오디오 자르기 시작 - 시작: " + startTimeMs + "ms, 끝: " + endTimeMs + "ms");
        trimManager.trimAudio(sourceUri, startTimeMs, endTimeMs, outputFileName);
    }
    
    /**
     * 오디오 자르기 (시간 기반) - 호환성 메서드
     */
    public void trimAudio(Uri sourceUri, long startTimeMs, long endTimeMs, String outputFileName) {
        LoggerManager.logger("⚠️ trimAudio 호출시 Context 미제공. Context 필수로 변경 필요.");
        // Context 없이 호출된 경우 오류 반환
        notifyFailure("입력 파라미터 오류", "Context가 필요합니다. trimAudio(Uri, long, long, String, Context) 메서드를 사용해주세요.");
    }
    
    /**
     * 오디오 자르기 (비율 기반)
     */
    public void trimAudioByRatio(Uri sourceUri, float startRatio, float endRatio, 
                               long durationMs, String outputFileName, Context context) {
        if (sourceUri == null || outputFileName == null) {
            notifyFailure("입력 파라미터 오류", "소스 URI 또는 출력 파일명이 null입니다.");
            return;
        }
        
        if (isConverting) {
            notifyFailure("편집 진행 중", "다른 편집이 진행 중입니다.");
            return;
        }
        
        // Native 매니저들에 Context 설정 (중요!)
        ensureNativeManagersInitialized(context);
        
        LoggerManager.logger("오디오 자르기 시작 (비율) - 시작: " + startRatio + ", 끝: " + endRatio);
        trimManager.trimAudioByRatio(sourceUri, startRatio, endRatio, durationMs, outputFileName);
    }
    
    /**
     * 오디오 자르기 (비율 기반) - 호환성 메서드
     */
    public void trimAudioByRatio(Uri sourceUri, float startRatio, float endRatio, 
                               long durationMs, String outputFileName) {
        LoggerManager.logger("⚠️ trimAudioByRatio 호출시 Context 미제공. Context 필수로 변경 필요.");
        // Context 없이 호출된 경우 오류 반환
        notifyFailure("입력 파라미터 오류", "Context가 필요합니다. trimAudioByRatio(Uri, float, float, long, String, Context) 메서드를 사용해주세요.");
    }
    
    /**
     * 작업 취소
     */
    public void cancelConversion() {
        try {
            // 진행 중인 작업 취소
            if (currentTask != null && !currentTask.isDone()) {
                currentTask.cancel(true);
            }
            
            // Native API 작업 취소
            extractorManager.cancelExtraction();
            trimManager.cancelTrimming();
            
            isConverting = false;
            LoggerManager.logger("변환/편집 취소됨");
            
        } catch (Exception e) {
            LoggerManager.logger("변환/편집 취소 실패: " + e.getMessage());
        }
    }
    
    /**
     * 현재 변환 중인지 확인
     */
    public boolean isConverting() {
        return isConverting || extractorManager.isExtracting() || trimManager.isTrimming();
    }
    
    /**
     * 미디어 파일 기간 가져오기
     */
    public long getMediaDuration(String filePath) {
        return mediaInfoManager.getMediaDuration(filePath);
    }
    
    /**
     * 미디어 파일 정보 가져오기
     */
    public NativeMediaInfoManager.MediaInfo getMediaInfo(String filePath) {
        return mediaInfoManager.getMediaInfo(filePath);
    }
    
    /**
     * 지원되는 출력 포맷 목록 반환
     */
    public OutputFormat[] getSupportedFormats() {
        return new OutputFormat[]{
            OutputFormat.M4A,  // Native API에서 완전 지원
            OutputFormat.MP3,  // 호환성을 위해 유지 (M4A로 변환됨)
            OutputFormat.AAC,  // 호환성을 위해 유지 (M4A로 변환됨)
            OutputFormat.WAV,  // 호환성을 위해 유지 (M4A로 변환됨)
        };
    }
    
    /**
     * 지원되는 품질 옵션 목록 반환
     */
    public AudioQuality[] getSupportedQualities() {
        return AudioQuality.values();
    }
    
    /**
     * OutputFormat을 Native API 포맷으로 매핑
     */
    private NativeAudioExtractorManager.AudioFormat mapToNativeFormat(OutputFormat format) {
        // Native API는 현재 M4A와 WEBM만 지원
        // 다른 포맷 요청시 M4A로 통일 (호환성 우선)
        switch (format) {
            case M4A:
            case AAC:
            case MP3:
            case WAV:
            case FLAC:
            case OGG:
            default:
                return NativeAudioExtractorManager.AudioFormat.M4A; // 기본값 및 호환성
        }
    }
    
    /**
     * 콜백 알림 메서드들
     */
    private void notifyFailure(String message, String reason) {
        if (onFailureListener != null) {
            mainHandler.post(() -> onFailureListener.onFFmpegManagerFailure(message, reason));
        }
    }
    
    /**
     * Native 매니저들의 Context 초기화 보장
     * @param context 애플리케이션 Context
     */
    private void ensureNativeManagersInitialized(Context context) {
        if (context == null) {
            LoggerManager.logger("⚠️ ensureNativeManagersInitialized - Context가 null");
            return;
        }
        
        try {
            // NativeMediaInfoManager 초기화
            if (mediaInfoManager != null) {
                mediaInfoManager.init(context);
            }
            
            // NativeAudioExtractorManager 초기화
            if (extractorManager != null) {
                extractorManager.init(context);
            }
            
            // NativeAudioTrimManager 초기화
            if (trimManager != null) {
                trimManager.init(context);
            }
            
            LoggerManager.logger("✅ Native 매니저들 Context 초기화 완료");
            
        } catch (Exception e) {
            LoggerManager.logger("❌ Native 매니저들 Context 초기화 실패: " + e.getMessage());
        }
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
            
            // Native API 정리
            if (extractorManager != null) {
                extractorManager.cleanup();
            }
            if (trimManager != null) {
                trimManager.cleanup();
            }
            
            LoggerManager.logger("AudioConversionManager 리소스 정리 완료");
            
        } catch (Exception e) {
            LoggerManager.logger("AudioConversionManager 리소스 정리 실패: " + e.getMessage());
        }
    }
    
    // Setter 메서드들 (FFmpegManager 호환성 유지)
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
}