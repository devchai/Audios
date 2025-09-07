package com.devc.lab.audios.manager;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Android Native API 기반 오디오 추출 관리자
 * MediaExtractor + MediaMuxer를 사용하여 비디오에서 오디오 트랙을 추출
 * FFmpeg 종속성을 제거하고 성능을 향상시키기 위한 Phase 2 구현
 */
public class NativeAudioExtractorManager {
    
    private static NativeAudioExtractorManager instance;
    private Context context;
    
    // 콜백 인터페이스들
    private OnStartListener onStartListener;
    private OnProgressListener onProgressListener;
    private OnCompletionListener onCompletionListener;
    private OnErrorListener onErrorListener;
    
    // 스레드 관리
    private ExecutorService executorService;
    private Handler mainHandler;
    private Future<?> currentTask;
    private volatile boolean isExtracting = false;
    
    // 진행률 추적
    private long totalDurationUs = 0;
    private long processedDurationUs = 0;
    
    // 콜백 인터페이스 정의
    public interface OnStartListener {
        void onExtractionStart();
    }
    
    public interface OnProgressListener {
        void onExtractionProgress(int progress);
    }
    
    public interface OnCompletionListener {
        void onExtractionComplete(String outputPath);
    }
    
    public interface OnErrorListener {
        void onExtractionError(String error);
    }
    
    /**
     * 지원되는 오디오 출력 포맷
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
    
    private NativeAudioExtractorManager() {
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public static synchronized NativeAudioExtractorManager getInstance() {
        if (instance == null) {
            instance = new NativeAudioExtractorManager();
        }
        return instance;
    }
    
    public void init(Context context) {
        this.context = context.getApplicationContext();
        LoggerManager.logger("NativeAudioExtractorManager 초기화 완료");
    }
    
    /**
     * 비디오에서 오디오 추출 (파일 경로 기반)
     */
    public void extractAudioFromVideo(String inputPath, String outputPath) {
        extractAudioFromVideo(inputPath, outputPath, AudioFormat.M4A);
    }
    
    /**
     * 비디오에서 오디오 추출 (포맷 지정)
     */
    public void extractAudioFromVideo(String inputPath, String outputPath, AudioFormat format) {
        if (inputPath == null || outputPath == null) {
            notifyError("입력 파라미터가 null입니다.");
            return;
        }
        
        if (isExtracting) {
            notifyError("다른 추출 작업이 진행 중입니다.");
            return;
        }
        
        // ExecutorService 상태 확인 및 재생성
        ensureExecutorServiceAvailable();
        
        currentTask = executorService.submit(() -> {
            performExtraction(inputPath, outputPath, format);
        });
    }
    
    /**
     * URI에서 오디오 추출 (Scoped Storage 호환)
     */
    public void extractAudioFromVideoUri(Uri inputUri, String outputFileName, AudioFormat format) {
        if (inputUri == null || context == null) {
            notifyError("입력 URI 또는 Context가 null입니다.");
            return;
        }
        
        if (isExtracting) {
            notifyError("다른 추출 작업이 진행 중입니다.");
            return;
        }
        
        // ExecutorService 상태 확인 및 재생성
        ensureExecutorServiceAvailable();
        
        currentTask = executorService.submit(() -> {
            try {
                // URI를 임시 파일로 복사
                File tempInputFile = createTempFileFromUri(inputUri);
                if (tempInputFile == null) {
                    notifyError("URI를 임시 파일로 복사할 수 없습니다.");
                    return;
                }
                
                // 출력 파일 경로 생성
                File outputDir = new File(context.getExternalFilesDir(null), "Audios/Converted");
                if (!outputDir.exists()) {
                    outputDir.mkdirs();
                }
                
                // 파일명 확장자 처리 (기존 확장자 교체)
                String fileName = outputFileName;
                // 기존 확장자 제거
                int lastDotIndex = fileName.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    fileName = fileName.substring(0, lastDotIndex);
                    LoggerManager.logger("기존 확장자 제거: " + outputFileName + " → " + fileName);
                }
                // 새로운 포맷 확장자 추가
                fileName += format.getExtension();
                LoggerManager.logger("최종 파일명: " + fileName);
                
                File outputFile = new File(outputDir, fileName);
                String outputPath = outputFile.getAbsolutePath();
                
                // 실제 추출 수행
                performExtraction(tempInputFile.getAbsolutePath(), outputPath, format);
                
                // 임시 파일 정리
                if (tempInputFile.exists()) {
                    tempInputFile.delete();
                    LoggerManager.logger("임시 입력 파일 삭제: " + tempInputFile.getName());
                }
                
            } catch (Exception e) {
                LoggerManager.logger("URI 추출 실패: " + e.getMessage());
                notifyError("URI 추출 실패: " + e.getMessage());
            }
        });
    }
    
    /**
     * 실제 오디오 추출 수행
     */
    private void performExtraction(String inputPath, String outputPath, AudioFormat format) {
        MediaExtractor extractor = null;
        MediaMuxer muxer = null;
        
        try {
            isExtracting = true;
            notifyStart();
            
            // 입력 파일 검증
            File inputFile = new File(inputPath);
            if (!inputFile.exists() || !inputFile.canRead()) {
                throw new IOException("입력 파일을 읽을 수 없습니다: " + inputPath);
            }
            
            // 출력 디렉토리 확인
            File outputFile = new File(outputPath);
            File outputDir = outputFile.getParentFile();
            if (outputDir != null && !outputDir.exists()) {
                boolean created = outputDir.mkdirs();
                if (!created) {
                    throw new IOException("출력 디렉토리를 생성할 수 없습니다: " + outputDir.getAbsolutePath());
                }
            }
            
            // MediaExtractor 설정
            extractor = new MediaExtractor();
            extractor.setDataSource(inputPath);
            
            // 오디오 트랙 찾기
            int audioTrackIndex = findAudioTrack(extractor);
            if (audioTrackIndex < 0) {
                throw new IllegalStateException("오디오 트랙을 찾을 수 없습니다.");
            }
            
            // 오디오 트랙 선택
            extractor.selectTrack(audioTrackIndex);
            MediaFormat audioFormat = extractor.getTrackFormat(audioTrackIndex);
            
            // 총 기간 계산 (진행률용)
            totalDurationUs = audioFormat.getLong(MediaFormat.KEY_DURATION);
            processedDurationUs = 0;
            
            LoggerManager.logger("오디오 트랙 정보: " + audioFormat.toString());
            LoggerManager.logger("총 기간: " + totalDurationUs + " μs");
            
            // MediaMuxer 설정
            muxer = new MediaMuxer(outputPath, format.getMuxerFormat());
            int muxerTrackIndex = muxer.addTrack(audioFormat);
            muxer.start();
            
            // 오디오 데이터 복사
            copyAudioTrack(extractor, muxer, muxerTrackIndex);
            
            LoggerManager.logger("오디오 추출 완료: " + outputPath);
            notifyCompletion(outputPath);
            
        } catch (Exception e) {
            LoggerManager.logger("오디오 추출 실패: " + e.getMessage());
            e.printStackTrace();
            notifyError("오디오 추출 실패: " + e.getMessage());
            
        } finally {
            // 리소스 정리
            if (muxer != null) {
                try {
                    muxer.stop();
                    muxer.release();
                } catch (Exception e) {
                    LoggerManager.logger("MediaMuxer 정리 실패: " + e.getMessage());
                }
            }
            
            if (extractor != null) {
                try {
                    extractor.release();
                } catch (Exception e) {
                    LoggerManager.logger("MediaExtractor 정리 실패: " + e.getMessage());
                }
            }
            
            isExtracting = false;
        }
    }
    
    /**
     * 오디오 트랙 인덱스 찾기
     */
    private int findAudioTrack(MediaExtractor extractor) {
        int trackCount = extractor.getTrackCount();
        LoggerManager.logger("총 트랙 수: " + trackCount);
        
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            LoggerManager.logger("트랙 " + i + " MIME: " + mime);
            
            if (mime != null && mime.startsWith("audio/")) {
                LoggerManager.logger("오디오 트랙 발견: 인덱스 " + i);
                return i;
            }
        }
        
        return -1;
    }
    
    /**
     * 오디오 트랙 데이터 복사
     */
    private void copyAudioTrack(MediaExtractor extractor, MediaMuxer muxer, int muxerTrackIndex) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(256 * 1024); // 256KB 버퍼
        android.media.MediaCodec.BufferInfo bufferInfo = new android.media.MediaCodec.BufferInfo();
        
        int sampleCount = 0;
        long lastProgressTime = 0;
        
        while (true) {
            int sampleSize = extractor.readSampleData(buffer, 0);
            if (sampleSize < 0) {
                // 더 이상 읽을 데이터 없음
                break;
            }
            
            // 샘플 정보 가져오기
            bufferInfo.presentationTimeUs = extractor.getSampleTime();
            bufferInfo.flags = extractor.getSampleFlags();
            bufferInfo.size = sampleSize;
            bufferInfo.offset = 0;
            
            // MediaMuxer에 샘플 쓰기
            muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo);
            
            // 진행률 업데이트 (100ms마다)
            processedDurationUs = bufferInfo.presentationTimeUs;
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastProgressTime > 100) {
                updateProgress();
                lastProgressTime = currentTime;
            }
            
            sampleCount++;
            extractor.advance();
        }
        
        LoggerManager.logger("처리된 샘플 수: " + sampleCount);
        
        // 최종 진행률 100%
        notifyProgress(100);
    }
    
    /**
     * 진행률 계산 및 콜백
     */
    private void updateProgress() {
        if (totalDurationUs > 0) {
            int progress = (int) ((processedDurationUs * 100) / totalDurationUs);
            progress = Math.min(Math.max(progress, 0), 100);
            notifyProgress(progress);
        }
    }
    
    /**
     * URI에서 임시 파일 생성
     */
    private File createTempFileFromUri(Uri uri) {
        try {
            // 임시 디렉토리
            File tempDir = new File(context.getCacheDir(), "temp_audio_extractor");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            
            // 임시 파일
            File tempFile = new File(tempDir, "temp_extract_" + System.currentTimeMillis() + ".tmp");
            
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
     * 추출 취소
     */
    public void cancelExtraction() {
        try {
            if (currentTask != null && !currentTask.isDone()) {
                currentTask.cancel(true);
                LoggerManager.logger("오디오 추출 취소 요청됨");
            }
        } catch (Exception e) {
            LoggerManager.logger("추출 취소 실패: " + e.getMessage());
        }
    }
    
    /**
     * 현재 추출 중인지 확인
     */
    public boolean isExtracting() {
        return isExtracting;
    }
    
    /**
     * 콜백 알림 메서드들
     */
    private void notifyStart() {
        if (onStartListener != null) {
            mainHandler.post(() -> onStartListener.onExtractionStart());
        }
    }
    
    private void notifyProgress(int progress) {
        if (onProgressListener != null) {
            mainHandler.post(() -> onProgressListener.onExtractionProgress(progress));
        }
    }
    
    private void notifyCompletion(String outputPath) {
        if (onCompletionListener != null) {
            mainHandler.post(() -> onCompletionListener.onExtractionComplete(outputPath));
        }
    }
    
    private void notifyError(String error) {
        if (onErrorListener != null) {
            mainHandler.post(() -> onErrorListener.onExtractionError(error));
        }
    }
    
    /**
     * 리스너 설정 메서드들
     */
    public void setOnStartListener(OnStartListener listener) {
        this.onStartListener = listener;
    }
    
    public void setOnProgressListener(OnProgressListener listener) {
        this.onProgressListener = listener;
    }
    
    public void setOnCompletionListener(OnCompletionListener listener) {
        this.onCompletionListener = listener;
    }
    
    public void setOnErrorListener(OnErrorListener listener) {
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
     * ExecutorService 상태 확인 및 재생성
     * ThreadPoolExecutor가 종료된 경우 새로운 ExecutorService를 생성합니다.
     */
    private synchronized void ensureExecutorServiceAvailable() {
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            String previousState = executorService == null ? "null" : 
                                 executorService.isShutdown() ? "shutdown" : "terminated";
            LoggerManager.logger("ExecutorService 재생성 - 이전 상태: " + previousState);
            
            executorService = Executors.newSingleThreadExecutor();
            LoggerManager.logger("새로운 ExecutorService 생성 완료");
        }
    }
    
    /**
     * 리소스 정리
     */
    public void cleanup() {
        try {
            // 진행 중인 작업 취소
            cancelExtraction();
            
            // ExecutorService 종료
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
            }
            
            // 임시 파일 정리
            File tempDir = new File(context.getCacheDir(), "temp_audio_extractor");
            if (tempDir.exists()) {
                File[] tempFiles = tempDir.listFiles();
                if (tempFiles != null) {
                    for (File file : tempFiles) {
                        file.delete();
                    }
                }
                tempDir.delete();
            }
            
            LoggerManager.logger("NativeAudioExtractorManager 리소스 정리 완료");
            
        } catch (Exception e) {
            LoggerManager.logger("NativeAudioExtractorManager 리소스 정리 실패: " + e.getMessage());
        }
    }
}