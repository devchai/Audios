package com.devc.lab.audios.manager;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 미디어 처리 엔진
 * MediaExtractor와 MediaMuxer를 사용한 실제 오디오 처리 로직 담당
 * 단일 책임 원칙에 따라 미디어 변환 로직만 처리
 */
public class MediaProcessingEngine {
    
    /**
     * 진행률 콜백 인터페이스
     */
    public interface ProgressCallback {
        void onProgress(long processedUs, long totalUs);
    }
    
    /**
     * MediaExtractor에서 오디오 트랙 인덱스 찾기
     * @param extractor MediaExtractor 인스턴스
     * @return 오디오 트랙 인덱스, 없으면 -1
     */
    public int findAudioTrack(MediaExtractor extractor) {
        if (extractor == null) {
            LoggerManager.logger("❌ findAudioTrack: extractor가 null");
            return -1;
        }
        
        int trackCount = extractor.getTrackCount();
        LoggerManager.logger("🔍 트랙 검색 시작 (총 " + trackCount + "개 트랙)");
        
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            
            LoggerManager.logger("   → 트랙 " + i + ": " + mime);
            
            if (mime != null && mime.startsWith("audio/")) {
                LoggerManager.logger("✅ 오디오 트랙 발견: 인덱스 " + i);
                logMediaFormat(format);
                return i;
            }
        }
        
        LoggerManager.logger("❌ 오디오 트랙을 찾을 수 없습니다");
        return -1;
    }
    
    /**
     * 오디오 트랙 자르기 및 복사
     * @param extractor 입력 MediaExtractor
     * @param muxer 출력 MediaMuxer  
     * @param audioTrackIndex 오디오 트랙 인덱스
     * @param muxerTrackIndex Muxer 트랙 인덱스
     * @param startTimeUs 시작 시간 (마이크로초)
     * @param endTimeUs 종료 시간 (마이크로초)
     * @param progressCallback 진행률 콜백
     * @return 성공 여부
     */
    public boolean trimAndCopyAudioTrack(MediaExtractor extractor, MediaMuxer muxer, 
                                       int audioTrackIndex, int muxerTrackIndex,
                                       long startTimeUs, long endTimeUs,
                                       ProgressCallback progressCallback) {
        
        if (extractor == null || muxer == null) {
            LoggerManager.logger("❌ trimAndCopyAudioTrack: extractor 또는 muxer가 null");
            return false;
        }
        
        LoggerManager.logger("🎵 오디오 트랙 자르기 시작");
        LoggerManager.logger("   → 트랙 인덱스: " + audioTrackIndex + " → " + muxerTrackIndex);
        LoggerManager.logger("   → 시간 범위: " + (startTimeUs/1000) + "ms ~ " + (endTimeUs/1000) + "ms");
        LoggerManager.logger("   → 자르기 길이: " + ((endTimeUs - startTimeUs)/1000) + "ms");
        
        try {
            // 트랙 선택
            extractor.selectTrack(audioTrackIndex);
            
            // 시작 위치로 이동
            extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            
            // 실제 시작 위치 확인
            long actualStartTime = extractor.getSampleTime();
            LoggerManager.logger("   → 실제 시작 시간: " + (actualStartTime/1000) + "ms");
            
            // 버퍼 할당
            MediaFormat format = extractor.getTrackFormat(audioTrackIndex);
            int maxInputSize = getMaxInputSize(format);
            ByteBuffer buffer = ByteBuffer.allocate(maxInputSize);
            
            // 샘플 복사 루프
            long totalDurationUs = endTimeUs - startTimeUs;
            long processedUs = 0;
            int sampleCount = 0;
            long totalBytes = 0;
            
            while (true) {
                long sampleTime = extractor.getSampleTime();
                
                // 종료 시간 체크
                if (sampleTime >= endTimeUs) {
                    LoggerManager.logger("✅ 종료 시간 도달: " + (sampleTime/1000) + "ms");
                    break;
                }
                
                // 샘플이 더 이상 없는 경우
                if (sampleTime < 0) {
                    LoggerManager.logger("⚠️ 더 이상 샘플이 없습니다");
                    break;
                }
                
                // 샘플 읽기
                buffer.clear();
                int sampleSize = extractor.readSampleData(buffer, 0);
                
                if (sampleSize < 0) {
                    LoggerManager.logger("⚠️ 샘플 읽기 완료 (EOF)");
                    break;
                }
                
                // 샘플 정보 설정
                android.media.MediaCodec.BufferInfo bufferInfo = new android.media.MediaCodec.BufferInfo();
                bufferInfo.offset = 0;
                bufferInfo.size = sampleSize;
                bufferInfo.presentationTimeUs = sampleTime;
                bufferInfo.flags = extractor.getSampleFlags();
                
                // Muxer에 샘플 쓰기
                try {
                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo);
                } catch (Exception e) {
                    LoggerManager.logger("❌ 샘플 쓰기 실패: " + e.getMessage());
                    return false;
                }
                
                // 통계 업데이트
                sampleCount++;
                totalBytes += sampleSize;
                processedUs = sampleTime - actualStartTime;
                
                // 진행률 콜백
                if (progressCallback != null && totalDurationUs > 0) {
                    progressCallback.onProgress(processedUs, totalDurationUs);
                }
                
                // 다음 샘플로 이동
                if (!extractor.advance()) {
                    LoggerManager.logger("⚠️ 더 이상 샘플이 없습니다 (advance)");
                    break;
                }
            }
            
            LoggerManager.logger("✅ 오디오 트랙 자르기 완료");
            LoggerManager.logger("   → 처리된 샘플: " + sampleCount + "개");
            LoggerManager.logger("   → 총 데이터: " + totalBytes + " bytes");
            LoggerManager.logger("   → 처리 시간: " + (processedUs/1000) + "ms");
            
            return true;
            
        } catch (Exception e) {
            LoggerManager.logger("❌ 오디오 트랙 자르기 실패: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * MediaMuxer에 트랙 추가 (호환성 검증 포함)
     * @param muxer MediaMuxer 인스턴스
     * @param audioFormat 오디오 MediaFormat
     * @param outputFormat 출력 포맷
     * @return 트랙 인덱스, 실패 시 -1
     */
    public int addTrackWithCompatibilityCheck(MediaMuxer muxer, MediaFormat audioFormat, 
                                            NativeAudioTrimManager.AudioFormat outputFormat) {
        if (muxer == null || audioFormat == null || outputFormat == null) {
            LoggerManager.logger("❌ addTrackWithCompatibilityCheck: null 파라미터");
            return -1;
        }
        
        try {
            LoggerManager.logger("🎯 MediaMuxer 트랙 추가");
            
            String inputMime = audioFormat.getString(MediaFormat.KEY_MIME);
            LoggerManager.logger("   → 입력 MIME: " + inputMime);
            LoggerManager.logger("   → 출력 포맷: " + outputFormat.name());
            
            // 1단계: 원본 MediaFormat 그대로 시도
            try {
                LoggerManager.logger("   → 1단계: 원본 MediaFormat 시도");
                int trackIndex = muxer.addTrack(audioFormat);
                LoggerManager.logger("✅ 원본 포맷으로 트랙 추가 성공 - 인덱스: " + trackIndex);
                return trackIndex;
                
            } catch (IllegalArgumentException e) {
                LoggerManager.logger("   ⚠️ 1단계 실패 (포맷 비호환): " + e.getMessage());
                
                // 2단계: 안전한 MediaFormat 생성 시도
                LoggerManager.logger("   → 2단계: 안전한 MediaFormat 시도");
                MediaFormat safeFormat = createSafeMediaFormat(audioFormat, outputFormat);
                
                if (safeFormat != null) {
                    try {
                        int trackIndex = muxer.addTrack(safeFormat);
                        LoggerManager.logger("✅ 안전한 포맷으로 트랙 추가 성공 - 인덱스: " + trackIndex);
                        return trackIndex;
                        
                    } catch (Exception e2) {
                        LoggerManager.logger("   ❌ 2단계도 실패: " + e2.getMessage());
                    }
                }
            }
            
            LoggerManager.logger("❌ 모든 트랙 추가 시도 실패");
            return -1;
            
        } catch (Exception e) {
            LoggerManager.logger("❌ 트랙 추가 프로세스 실패: " + e.getMessage());
            return -1;
        }
    }
    
    /**
     * 안전한 MediaFormat 생성
     * @param originalFormat 원본 MediaFormat
     * @param outputFormat 출력 포맷
     * @return 안전한 MediaFormat 또는 null
     */
    public MediaFormat createSafeMediaFormat(MediaFormat originalFormat, 
                                           NativeAudioTrimManager.AudioFormat outputFormat) {
        try {
            LoggerManager.logger("🔧 안전한 MediaFormat 생성");
            
            // 새 MediaFormat 생성
            MediaFormat safeFormat = new MediaFormat();
            
            // 필수 속성들 복사
            String mime = originalFormat.getString(MediaFormat.KEY_MIME);
            safeFormat.setString(MediaFormat.KEY_MIME, mime);
            
            if (originalFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                safeFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, 
                    originalFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
            }
            
            if (originalFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                safeFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 
                    originalFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            }
            
            // 선택적 속성들 (오류 발생 시 무시)
            try {
                if (originalFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    safeFormat.setInteger(MediaFormat.KEY_BIT_RATE, 
                        originalFormat.getInteger(MediaFormat.KEY_BIT_RATE));
                }
            } catch (Exception e) {
                LoggerManager.logger("   ⚠️ BIT_RATE 설정 실패: " + e.getMessage());
            }
            
            LoggerManager.logger("✅ 안전한 MediaFormat 생성 완료");
            return safeFormat;
            
        } catch (Exception e) {
            LoggerManager.logger("❌ 안전한 MediaFormat 생성 실패: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * MediaFormat에서 최대 입력 크기 가져오기
     * @param format MediaFormat
     * @return 최대 입력 크기
     */
    private int getMaxInputSize(MediaFormat format) {
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            return format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        }
        
        // 기본값: 64KB
        int defaultSize = 64 * 1024;
        LoggerManager.logger("⚠️ MAX_INPUT_SIZE 없음 - 기본값 사용: " + defaultSize);
        return defaultSize;
    }
    
    /**
     * MediaFormat 상세 정보 로깅
     * @param format MediaFormat
     */
    private void logMediaFormat(MediaFormat format) {
        try {
            LoggerManager.logger("=== MediaFormat 상세 정보 ===");
            
            String mime = format.getString(MediaFormat.KEY_MIME);
            LoggerManager.logger("MIME: " + mime);
            
            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                LoggerManager.logger("샘플레이트: " + format.getInteger(MediaFormat.KEY_SAMPLE_RATE) + "Hz");
            }
            
            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                LoggerManager.logger("채널 수: " + format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            }
            
            if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                LoggerManager.logger("비트레이트: " + format.getInteger(MediaFormat.KEY_BIT_RATE) + "bps");
            }
            
            LoggerManager.logger("=======================");
            
        } catch (Exception e) {
            LoggerManager.logger("MediaFormat 정보 로깅 실패: " + e.getMessage());
        }
    }
}