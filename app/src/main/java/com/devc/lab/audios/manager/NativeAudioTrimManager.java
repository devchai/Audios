package com.devc.lab.audios.manager;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Android Native API 기반 오디오 자르기 관리자 (리팩토링된 버전)
 * 
 * Phase 2 개선사항:
 * - AudioFileManager: 파일 시스템 작업 분리
 * - MediaProcessingEngine: 미디어 처리 로직 분리  
 * - AudioTrimException: 구조화된 예외 처리
 * - 리소스 관리 개선 및 테스트 가능성 향상
 * 
 * 기존 public API는 유지하되 내부 구현만 개선
 */
public class NativeAudioTrimManager {
    
    private static NativeAudioTrimManager instance;
    private Context context;
    
    // 분리된 컴포넌트들
    private AudioFileManager fileManager;
    private MediaProcessingEngine processingEngine;
    
    // 콜백 인터페이스들 (기존 API 유지)
    private OnTrimStartListener onStartListener;
    private OnTrimProgressListener onProgressListener;
    private OnTrimCompletionListener onCompletionListener;
    private OnTrimErrorListener onErrorListener;
    
    // 스레드 관리
    private ExecutorService executorService;
    private Handler mainHandler;
    private Future<?> currentTask;
    private volatile boolean isTrimming = false;
    
    // 진행률 추적
    private long totalDurationUs = 0;
    private long processedDurationUs = 0;
    
    // 콜백 인터페이스 정의 (기존 API 유지)
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
    
    /**
     * 지원되는 오디오 출력 포맷 (기존 API 유지)
     */
    public enum AudioFormat {
        M4A("audio/mp4", ".m4a", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4),
        WEBM("audio/webm", ".webm", MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM);
        
        private final String mimeType;
        private final String extension;
        private final int muxerFormat;
        
        AudioFormat(String mimeType, String extension, int muxerFormat) {
            this.mimeType = mimeType;
            this.extension = extension;
            this.muxerFormat = muxerFormat;
        }
        
        public String getMimeType() { return mimeType; }
        public String getExtension() { return extension; }
        public int getMuxerFormat() { return muxerFormat; }
    }
    
    /**
     * 자르기 작업 상태 추적
     */
    private static class TrimState {
        File tempInputFile;
        String outputPath;
        AudioFormat format;
        long startTimeUs;
        long endTimeUs;
        
        TrimState(AudioFormat format, long startTimeUs, long endTimeUs) {
            this.format = format;
            this.startTimeUs = startTimeUs;
            this.endTimeUs = endTimeUs;
        }
    }
    
    private NativeAudioTrimManager() {
        initializeServices();
    }
    
    /**
     * 서비스 초기화 (의존성 주입 가능하도록 분리)
     */
    private void initializeServices() {
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        processingEngine = new MediaProcessingEngine();
    }
    
    public static synchronized NativeAudioTrimManager getInstance() {
        if (instance == null) {
            instance = new NativeAudioTrimManager();
        }
        return instance;
    }
    
    public void init(Context context) {
        this.context = context.getApplicationContext();
        this.fileManager = new AudioFileManager(this.context);
        LoggerManager.logger("NativeAudioTrimManager 초기화 완료 (리팩토링된 버전)");
    }
    
    /**
     * 콜백 설정 메서드들 (기존 API 유지)
     */
    public void setOnTrimStartListener(OnTrimStartListener listener) {
        this.onStartListener = listener;
    }
    
    public void setOnTrimProgressListener(OnTrimProgressListener listener) {
        this.onProgressListener = listener;
    }
    
    public void setOnTrimCompletionListener(OnTrimCompletionListener listener) {
        this.onCompletionListener = listener;
    }
    
    public void setOnTrimErrorListener(OnTrimErrorListener listener) {
        this.onErrorListener = listener;
    }
    
    /**
     * 오디오 파일 자르기 (기존 API 유지)
     * @param sourceUri 원본 파일 URI
     * @param startTimeMs 시작 시간 (밀리초)
     * @param endTimeMs 끝 시간 (밀리초)
     * @param outputFileName 출력 파일명
     */
    public void trimAudio(Uri sourceUri, long startTimeMs, long endTimeMs, String outputFileName) {
        LoggerManager.logger("==================== 🎵 AUDIO TRIM START ====================");
        LoggerManager.logger("📋 입력 파라미터:");
        LoggerManager.logger("   → 원본 URI: " + (sourceUri != null ? sourceUri.toString() : "NULL"));
        LoggerManager.logger("   → 시간 범위: " + startTimeMs + "ms ~ " + endTimeMs + "ms");
        LoggerManager.logger("   → 출력 파일명: " + (outputFileName != null ? outputFileName : "NULL"));
        LoggerManager.logger("   → 자르기 길이: " + (endTimeMs - startTimeMs) + "ms");
        
        try {
            // 파라미터 검증
            validateTrimParameters(sourceUri, startTimeMs, endTimeMs, outputFileName);
            
            // 중복 실행 방지
            if (isTrimming) {
                throw new AudioTrimException(AudioTrimException.ErrorType.PROCESSING_INTERRUPTED,
                    "다른 자르기 작업이 진행 중입니다");
            }
            
            LoggerManager.logger("✅ 파라미터 검증 통과 - 자르기 작업 시작");
            
            // ExecutorService 상태 확인
            ensureExecutorServiceReady();
            
            currentTask = executorService.submit(() -> {
                try {
                    // 최적 포맷 결정
                    AudioFormat optimalFormat = determineOptimalOutputFormat(sourceUri);
                    LoggerManager.logger("🎯 선택된 출력 포맷: " + optimalFormat.name());
                    
                    // 자르기 수행
                    performTrimming(sourceUri, startTimeMs * 1000, endTimeMs * 1000, 
                                  outputFileName, optimalFormat);
                    
                } catch (AudioTrimException e) {
                    LoggerManager.logger("❌ 자르기 실패: " + e.getFullErrorInfo());
                    notifyError(e.getUserMessage());
                    
                } catch (InterruptedException e) {
                    LoggerManager.logger("ℹ️ 자르기 작업 인터럽트");
                    Thread.currentThread().interrupt();
                    notifyError("자르기 작업이 취소되었습니다");
                    
                } catch (Exception e) {
                    LoggerManager.logger("❌ 예상치 못한 오류: " + e.getMessage());
                    AudioTrimException trimException = AudioTrimException.fromException(e);
                    notifyError(trimException.getUserMessage());
                }
            });
            
        } catch (AudioTrimException e) {
            LoggerManager.logger("❌ 파라미터 검증 실패: " + e.getFullErrorInfo());
            notifyError(e.getUserMessage());
        }
    }
    
    /**
     * 오디오 파일 자르기 (비율 기반) (기존 API 유지)
     */
    public void trimAudioByRatio(Uri sourceUri, float startRatio, float endRatio, 
                               long durationMs, String outputFileName) {
        long startTimeMs = (long) (startRatio * durationMs);
        long endTimeMs = (long) (endRatio * durationMs);
        
        trimAudio(sourceUri, startTimeMs, endTimeMs, outputFileName);
    }
    
    /**
     * 파라미터 검증 (구조화된 예외 처리)
     */
    private void validateTrimParameters(Uri sourceUri, long startTimeMs, long endTimeMs, 
                                      String outputFileName) throws AudioTrimException {
        if (sourceUri == null) {
            throw new AudioTrimException(AudioTrimException.ErrorType.INVALID_INPUT_URI,
                "sourceUri가 null입니다");
        }
        
        if (outputFileName == null || outputFileName.trim().isEmpty()) {
            throw new AudioTrimException(AudioTrimException.ErrorType.OUTPUT_PATH_INVALID,
                "출력 파일명이 비어있습니다");
        }
        
        if (startTimeMs < 0) {
            throw new AudioTrimException(AudioTrimException.ErrorType.INVALID_TIME_RANGE,
                "시작 시간이 음수입니다: " + startTimeMs);
        }
        
        if (endTimeMs <= startTimeMs) {
            throw new AudioTrimException(AudioTrimException.ErrorType.INVALID_TIME_RANGE,
                "종료 시간(" + endTimeMs + ")이 시작 시간(" + startTimeMs + ")보다 작거나 같습니다");
        }
    }
    
    /**
     * 최적 출력 포맷 결정
     */
    private AudioFormat determineOptimalOutputFormat(Uri sourceUri) throws AudioTrimException {
        LoggerManager.logger("🔍 입력 포맷 분석 시작");
        
        File tempFile = null;
        MediaExtractor analyzer = null;
        
        try {
            // 임시 파일 생성
            tempFile = fileManager.createTempFileFromUri(sourceUri);
            if (tempFile == null) {
                LoggerManager.logger("⚠️ 임시 파일 생성 실패 - 기본 M4A 포맷 사용");
                return AudioFormat.M4A;
            }
            
            // MediaExtractor로 포맷 분석
            analyzer = new MediaExtractor();
            analyzer.setDataSource(tempFile.getAbsolutePath());
            
            int audioTrackIndex = processingEngine.findAudioTrack(analyzer);
            if (audioTrackIndex < 0) {
                throw new AudioTrimException(AudioTrimException.ErrorType.AUDIO_TRACK_NOT_FOUND);
            }
            
            MediaFormat audioFormat = analyzer.getTrackFormat(audioTrackIndex);
            String inputMime = audioFormat.getString(MediaFormat.KEY_MIME);
            
            // 입력 포맷에 따른 최적 출력 포맷 선택
            AudioFormat selectedFormat = selectOptimalFormat(inputMime);
            
            LoggerManager.logger("✅ 포맷 분석 완료");
            LoggerManager.logger("   → 입력 MIME: " + inputMime);
            LoggerManager.logger("   → 선택된 포맷: " + selectedFormat.name());
            LoggerManager.logger("   → 선택 이유: " + getFormatSelectionReason(inputMime, selectedFormat));
            
            return selectedFormat;
            
        } catch (IOException e) {
            throw new AudioTrimException(AudioTrimException.ErrorType.MEDIA_EXTRACTOR_FAILED, 
                "포맷 분석 실패", e);
        } finally {
            // 리소스 정리
            if (analyzer != null) {
                try {
                    analyzer.release();
                } catch (Exception e) {
                    LoggerManager.logger("⚠️ MediaExtractor 정리 실패: " + e.getMessage());
                }
            }
            
            if (tempFile != null) {
                fileManager.cleanupTempFile(tempFile);
            }
        }
    }
    
    /**
     * 입력 MIME 타입에 따른 최적 포맷 선택
     */
    private AudioFormat selectOptimalFormat(String inputMime) {
        if (inputMime == null) {
            return AudioFormat.M4A; // 기본값
        }
        
        // MP4/AAC 계열 → M4A 유지
        if (inputMime.contains("mp4") || inputMime.contains("aac")) {
            return AudioFormat.M4A;
        }
        
        // WebM/VP8/VP9 계열 → WEBM 유지  
        if (inputMime.contains("webm") || inputMime.contains("vp8") || inputMime.contains("vp9")) {
            return AudioFormat.WEBM;
        }
        
        // 기타 포맷 → 호환성이 좋은 M4A
        return AudioFormat.M4A;
    }
    
    /**
     * 포맷 선택 이유 설명
     */
    private String getFormatSelectionReason(String inputMime, AudioFormat selectedFormat) {
        if (inputMime == null) {
            return "MIME 타입 불명 - 기본값 선택";
        }
        
        if (selectedFormat == AudioFormat.M4A) {
            if (inputMime.contains("mp4") || inputMime.contains("aac")) {
                return "입력과 동일한 MP4 계열 유지";
            }
            return "범용 호환성을 위한 M4A 선택";
        }
        
        if (selectedFormat == AudioFormat.WEBM) {
            return "WebM 계열 입력 포맷 유지";
        }
        
        return "알 수 없음";
    }
    
    /**
     * 실제 자르기 수행 (리팩토링된 버전)
     */
    private void performTrimming(Uri sourceUri, long startTimeUs, long endTimeUs, 
                               String outputFileName, AudioFormat format) 
                               throws AudioTrimException, InterruptedException {
        
        LoggerManager.logger("🔧 === 자르기 수행 시작 ===");
        
        TrimState state = new TrimState(format, startTimeUs, endTimeUs);
        MediaExtractor extractor = null;
        MediaMuxer muxer = null;
        
        try {
            isTrimming = true;
            notifyStart();
            
            // STEP 1: 임시 입력 파일 생성
            state.tempInputFile = fileManager.createTempFileFromUri(sourceUri);
            if (state.tempInputFile == null) {
                throw new AudioTrimException(AudioTrimException.ErrorType.FILE_ACCESS_DENIED,
                    "임시 입력 파일 생성 실패");
            }
            
            // STEP 2: 출력 경로 생성
            state.outputPath = fileManager.createOutputPath(outputFileName, format);
            if (state.outputPath == null) {
                throw new AudioTrimException(AudioTrimException.ErrorType.OUTPUT_PATH_INVALID,
                    "출력 경로 생성 실패");
            }
            
            // STEP 3: MediaExtractor 설정
            extractor = new MediaExtractor();
            extractor.setDataSource(state.tempInputFile.getAbsolutePath());
            
            int audioTrackIndex = processingEngine.findAudioTrack(extractor);
            if (audioTrackIndex < 0) {
                throw new AudioTrimException(AudioTrimException.ErrorType.AUDIO_TRACK_NOT_FOUND);
            }
            
            MediaFormat audioFormat = extractor.getTrackFormat(audioTrackIndex);
            
            // STEP 4: MediaMuxer 설정
            muxer = new MediaMuxer(state.outputPath, format.getMuxerFormat());
            
            int muxerTrackIndex = processingEngine.addTrackWithCompatibilityCheck(
                muxer, audioFormat, format);
            
            if (muxerTrackIndex < 0) {
                throw new AudioTrimException(AudioTrimException.ErrorType.TRACK_FORMAT_INCOMPATIBLE,
                    "트랙 추가 실패");
            }
            
            muxer.start();
            
            // STEP 5: 자르기 및 복사 수행
            boolean success = processingEngine.trimAndCopyAudioTrack(
                extractor, muxer, audioTrackIndex, muxerTrackIndex,
                startTimeUs, endTimeUs, this::updateTrimProgress);
            
            if (!success) {
                throw new AudioTrimException(AudioTrimException.ErrorType.PROCESSING_INTERRUPTED,
                    "오디오 복사 실패");
            }
            
            LoggerManager.logger("🎉 자르기 프로세스 성공적으로 완료!");
            LoggerManager.logger("   → 최종 출력 파일: " + state.outputPath);
            
            // 최종 파일 검증
            validateOutputFile(state.outputPath);
            
            notifyCompletion(state.outputPath);
            
        } catch (IOException e) {
            throw new AudioTrimException(AudioTrimException.ErrorType.MEDIA_MUXER_FAILED,
                "미디어 처리 실패", e);
            
        } finally {
            isTrimming = false;
            
            // 리소스 정리 (try-with-resources 스타일)
            cleanupResources(extractor, muxer, state.tempInputFile);
        }
    }
    
    /**
     * 출력 파일 검증
     */
    private void validateOutputFile(String outputPath) throws AudioTrimException {
        if (!fileManager.fileExists(outputPath)) {
            throw new AudioTrimException(AudioTrimException.ErrorType.FILE_NOT_FOUND,
                "출력 파일이 생성되지 않았습니다");
        }
        
        long fileSize = fileManager.getFileSize(outputPath);
        if (fileSize <= 0) {
            throw new AudioTrimException(AudioTrimException.ErrorType.INSUFFICIENT_STORAGE,
                "출력 파일 크기가 0입니다");
        }
        
        LoggerManager.logger("📊 최종 파일 검증 완료:");
        LoggerManager.logger("   → 경로: " + outputPath);
        LoggerManager.logger("   → 크기: " + fileSize + " bytes (" + (fileSize/1024) + " KB)");
    }
    
    /**
     * 리소스 정리 (개선된 버전)
     */
    private void cleanupResources(MediaExtractor extractor, MediaMuxer muxer, File tempFile) {
        if (extractor != null) {
            try {
                extractor.release();
            } catch (Exception e) {
                LoggerManager.logger("⚠️ MediaExtractor 정리 실패: " + e.getMessage());
            }
        }
        
        if (muxer != null) {
            try {
                muxer.stop();
            } catch (Exception e) {
                LoggerManager.logger("⚠️ MediaMuxer stop 실패: " + e.getMessage());
            }
            
            try {
                muxer.release();
            } catch (Exception e) {
                LoggerManager.logger("⚠️ MediaMuxer 정리 실패: " + e.getMessage());
            }
        }
        
        if (tempFile != null) {
            fileManager.cleanupTempFile(tempFile);
        }
    }
    
    /**
     * ExecutorService 상태 확인 및 재생성
     */
    private void ensureExecutorServiceReady() {
        if (executorService == null || executorService.isShutdown()) {
            LoggerManager.logger("🔄 ExecutorService 재생성");
            executorService = Executors.newSingleThreadExecutor();
        }
    }
    
    /**
     * 진행률 업데이트
     */
    private void updateTrimProgress(long processedUs, long totalUs) {
        if (totalUs > 0) {
            int progress = (int) ((processedUs * 100) / totalUs);
            progress = Math.max(0, Math.min(100, progress)); // 0-100 범위로 제한
            
            // UI 스레드에서 콜백 실행
            final int finalProgress = progress;
            mainHandler.post(() -> notifyProgress(finalProgress));
        }
    }
    
    /**
     * 콜백 알림 메서드들 (기존 API 유지)
     */
    private void notifyStart() {
        if (onStartListener != null) {
            mainHandler.post(() -> onStartListener.onTrimStart());
        }
    }
    
    private void notifyProgress(int progress) {
        if (onProgressListener != null) {
            onProgressListener.onTrimProgress(progress);
        }
    }
    
    private void notifyCompletion(String outputPath) {
        if (onCompletionListener != null) {
            mainHandler.post(() -> onCompletionListener.onTrimComplete(outputPath));
        }
    }
    
    private void notifyError(String error) {
        if (onErrorListener != null) {
            mainHandler.post(() -> onErrorListener.onTrimError(error));
        }
    }
    
    /**
     * 현재 작업 취소 (기존 API 유지)
     */
    public void cancelCurrentTask() {
        if (currentTask != null && !currentTask.isDone()) {
            LoggerManager.logger("🚫 현재 자르기 작업 취소 요청");
            currentTask.cancel(true);
        }
    }
    
    /**
     * 호환성을 위한 기존 메서드들
     */
    public void setOnStartListener(OnTrimStartListener listener) {
        setOnTrimStartListener(listener);
    }
    
    public void setOnProgressListener(OnTrimProgressListener listener) {
        setOnTrimProgressListener(listener);
    }
    
    public void setOnCompletionListener(OnTrimCompletionListener listener) {
        setOnTrimCompletionListener(listener);
    }
    
    public void setOnErrorListener(OnTrimErrorListener listener) {
        setOnTrimErrorListener(listener);
    }
    
    public void cancelTrimming() {
        cancelCurrentTask();
    }
    
    public boolean isTrimming() {
        return isTrimming;
    }
    
    public void cleanup() {
        release();
    }
    
    /**
     * 리소스 정리 (기존 API 유지)
     */
    public void release() {
        LoggerManager.logger("🗑️ NativeAudioTrimManager 리소스 정리");
        
        cancelCurrentTask();
        
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        
        isTrimming = false;
    }
}