package com.devc.lab.audios.manager;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.File;

/**
 * Native Android API 기반 미디어 정보 관리자
 * MediaMetadataRetriever를 사용한 미디어 메타데이터 추출
 * FFmpeg 의존성 없는 순수 안드로이드 구현
 */
public class NativeMediaInfoManager {
    
    private static NativeMediaInfoManager instance;
    private Context context;
    
    // 미디어 정보 조회 콜백 인터페이스
    public interface OnMediaInfoListener {
        void onMediaInfoSuccess(MediaInfo mediaInfo);
        void onMediaInfoError(String error);
    }
    
    private NativeMediaInfoManager() {
        // Private constructor for singleton
    }
    
    public static synchronized NativeMediaInfoManager getInstance() {
        if (instance == null) {
            instance = new NativeMediaInfoManager();
        }
        return instance;
    }
    
    public void init(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 파일 경로로부터 미디어 기간 추출 (밀리초 단위)
     * @param filePath 미디어 파일 경로
     * @return 기간(밀리초), 실패 시 0
     */
    public long getMediaDuration(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            LoggerManager.logger("미디어 기간 추출 실패: 파일 경로가 null 또는 빈 문자열");
            return 0;
        }
        
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(filePath);
            
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            
            if (durationStr != null && !durationStr.isEmpty()) {
                long duration = Long.parseLong(durationStr);
                LoggerManager.logger("미디어 기간 추출 성공: " + duration + "ms (" + filePath + ")");
                return duration;
            } else {
                LoggerManager.logger("미디어 파일 기간 정보 없음: " + filePath);
                return 0;
            }
            
        } catch (NumberFormatException e) {
            LoggerManager.logger("미디어 기간 파싱 실패: " + e.getMessage());
            return 0;
            
        } catch (Exception e) {
            LoggerManager.logger("미디어 파일 기간 가져오기 실패: " + e.getMessage());
            return 0;
            
        } finally {
            safeReleaseRetriever(retriever);
        }
    }
    
    /**
     * URI로부터 미디어 기간 추출 (밀리초 단위)
     * @param uri 미디어 파일 URI
     * @return 기간(밀리초), 실패 시 0
     */
    public long getMediaDuration(Uri uri) {
        if (uri == null) {
            LoggerManager.logger("미디어 기간 추출 실패: URI가 null");
            return 0;
        }
        
        if (context == null) {
            LoggerManager.logger("미디어 기간 추출 실패: Context가 초기화되지 않음");
            return 0;
        }
        
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, uri);
            
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            
            if (durationStr != null && !durationStr.isEmpty()) {
                long duration = Long.parseLong(durationStr);
                LoggerManager.logger("URI 미디어 기간 추출 성공: " + duration + "ms (" + uri + ")");
                return duration;
            } else {
                LoggerManager.logger("URI 미디어 파일 기간 정보 없음: " + uri);
                return 0;
            }
            
        } catch (NumberFormatException e) {
            LoggerManager.logger("URI 미디어 기간 파싱 실패: " + e.getMessage());
            return 0;
            
        } catch (Exception e) {
            LoggerManager.logger("URI 미디어 파일 기간 가져오기 실패: " + e.getMessage());
            return 0;
            
        } finally {
            safeReleaseRetriever(retriever);
        }
    }
    
    /**
     * 파일 경로로부터 상세한 미디어 정보 추출
     * @param filePath 미디어 파일 경로
     * @return MediaInfo 객체, 실패 시 null
     */
    public MediaInfo getMediaInfo(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            LoggerManager.logger("미디어 정보 추출 실패: 파일 경로가 null 또는 빈 문자열");
            return null;
        }
        
        // 파일 존재 여부 확인
        File file = new File(filePath);
        if (!file.exists() || !file.canRead()) {
            LoggerManager.logger("미디어 정보 추출 실패: 파일이 존재하지 않거나 읽을 수 없음 - " + filePath);
            return null;
        }
        
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(filePath);
            
            // 기본 메타데이터 추출
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            
            // 비디오 관련 메타데이터
            String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            
            // 오디오 관련 메타데이터
            String mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            
            MediaInfo mediaInfo = new MediaInfo(duration, bitrate, title, artist, album, 
                                              width, height, null, mimeType, null, filePath);
            
            LoggerManager.logger("미디어 정보 추출 성공: " + mediaInfo.toString());
            return mediaInfo;
            
        } catch (Exception e) {
            LoggerManager.logger("미디어 정보 가져오기 실패: " + e.getMessage());
            return null;
            
        } finally {
            safeReleaseRetriever(retriever);
        }
    }
    
    /**
     * URI로부터 상세한 미디어 정보 추출
     * @param uri 미디어 파일 URI
     * @return MediaInfo 객체, 실패 시 null
     */
    public MediaInfo getMediaInfo(Uri uri) {
        if (uri == null) {
            LoggerManager.logger("미디어 정보 추출 실패: URI가 null");
            return null;
        }
        
        if (context == null) {
            LoggerManager.logger("미디어 정보 추출 실패: Context가 초기화되지 않음");
            return null;
        }
        
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, uri);
            
            // 기본 메타데이터 추출
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            
            // 비디오 관련 메타데이터
            String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            
            // 오디오 관련 메타데이터
            String mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            
            MediaInfo mediaInfo = new MediaInfo(duration, bitrate, title, artist, album, 
                                              width, height, null, mimeType, null, uri.toString());
            
            LoggerManager.logger("URI 미디어 정보 추출 성공: " + mediaInfo.toString());
            return mediaInfo;
            
        } catch (Exception e) {
            LoggerManager.logger("URI 미디어 정보 가져오기 실패: " + e.getMessage());
            return null;
            
        } finally {
            safeReleaseRetriever(retriever);
        }
    }
    
    /**
     * 비동기로 미디어 정보 추출 (파일 경로)
     * @param filePath 미디어 파일 경로
     * @param listener 결과 콜백 리스너
     */
    public void getMediaInfoAsync(String filePath, OnMediaInfoListener listener) {
        if (listener == null) {
            LoggerManager.logger("비동기 미디어 정보 추출 실패: 리스너가 null");
            return;
        }
        
        new Thread(() -> {
            MediaInfo mediaInfo = getMediaInfo(filePath);
            if (mediaInfo != null) {
                listener.onMediaInfoSuccess(mediaInfo);
            } else {
                listener.onMediaInfoError("미디어 정보 추출 실패: " + filePath);
            }
        }).start();
    }
    
    /**
     * 비동기로 미디어 정보 추출 (URI)
     * @param uri 미디어 파일 URI
     * @param listener 결과 콜백 리스너
     */
    public void getMediaInfoAsync(Uri uri, OnMediaInfoListener listener) {
        if (listener == null) {
            LoggerManager.logger("비동기 미디어 정보 추출 실패: 리스너가 null");
            return;
        }
        
        new Thread(() -> {
            MediaInfo mediaInfo = getMediaInfo(uri);
            if (mediaInfo != null) {
                listener.onMediaInfoSuccess(mediaInfo);
            } else {
                listener.onMediaInfoError("URI 미디어 정보 추출 실패: " + uri);
            }
        }).start();
    }
    
    /**
     * 미디어 파일의 비디오 포함 여부 확인
     * @param filePath 미디어 파일 경로
     * @return 비디오 포함 시 true, 오디오 전용이면 false
     */
    public boolean hasVideo(String filePath) {
        MediaInfo info = getMediaInfo(filePath);
        return info != null && info.hasVideo;
    }
    
    /**
     * URI 미디어 파일의 비디오 포함 여부 확인
     * @param uri 미디어 파일 URI
     * @return 비디오 포함 시 true, 오디오 전용이면 false
     */
    public boolean hasVideo(Uri uri) {
        MediaInfo info = getMediaInfo(uri);
        return info != null && info.hasVideo;
    }
    
    /**
     * MediaMetadataRetriever 안전하게 해제
     * @param retriever 해제할 retriever 객체
     */
    private void safeReleaseRetriever(MediaMetadataRetriever retriever) {
        if (retriever != null) {
            try {
                retriever.release();
            } catch (Exception e) {
                LoggerManager.logger("MediaMetadataRetriever 해제 실패: " + e.getMessage());
            }
        }
    }
    
    /**
     * 미디어 파일 정보를 담는 데이터 클래스
     * 기존 FFmpeg 기반 MediaInfo보다 확장된 정보 제공
     */
    public static class MediaInfo {
        public final long durationMs;
        public final int bitrateKbps;
        public final String title;
        public final String artist;
        public final String album;
        public final int videoWidth;
        public final int videoHeight;
        public final String videoCodec;
        public final String mimeType;
        public final String sampleRate;
        public final String sourcePath;
        public final boolean hasVideo;
        public final boolean hasAudio;
        
        public MediaInfo(String duration, String bitrate, String title, String artist, String album,
                        String width, String height, String videoCodec, String mimeType, 
                        String sampleRate, String sourcePath) {
            
            // 기본 정보
            this.durationMs = parseStringToLong(duration, 0);
            this.bitrateKbps = parseStringToInt(bitrate, 0) / 1000; // bps to kbps
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.sourcePath = sourcePath;
            
            // 비디오 정보
            this.videoWidth = parseStringToInt(width, 0);
            this.videoHeight = parseStringToInt(height, 0);
            this.videoCodec = videoCodec;
            this.hasVideo = this.videoWidth > 0 && this.videoHeight > 0;
            
            // 오디오 정보
            this.mimeType = mimeType;
            this.sampleRate = sampleRate;
            this.hasAudio = this.bitrateKbps > 0 || (mimeType != null && mimeType.startsWith("audio"));
        }
        
        private long parseStringToLong(String str, long defaultValue) {
            try {
                return str != null && !str.isEmpty() ? Long.parseLong(str) : defaultValue;
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        
        private int parseStringToInt(String str, int defaultValue) {
            try {
                return str != null && !str.isEmpty() ? Integer.parseInt(str) : defaultValue;
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        
        /**
         * 재생 시간을 MM:SS 형식으로 반환
         */
        public String getDurationFormatted() {
            long seconds = durationMs / 1000;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%02d:%02d", minutes, seconds);
        }
        
        /**
         * 해상도 문자열 반환
         */
        public String getResolution() {
            if (hasVideo) {
                return videoWidth + "x" + videoHeight;
            } else {
                return "오디오 전용";
            }
        }
        
        /**
         * 미디어 타입 문자열 반환
         */
        public String getMediaType() {
            if (hasVideo && hasAudio) {
                return "비디오+오디오";
            } else if (hasVideo) {
                return "비디오 전용";
            } else if (hasAudio) {
                return "오디오 전용";
            } else {
                return "알 수 없음";
            }
        }
        
        /**
         * 파일 크기 정보 (파일 경로가 있는 경우)
         */
        public String getFileSize() {
            if (sourcePath != null && !sourcePath.startsWith("content://")) {
                try {
                    File file = new File(sourcePath);
                    if (file.exists()) {
                        long bytes = file.length();
                        if (bytes < 1024) {
                            return bytes + " B";
                        } else if (bytes < 1024 * 1024) {
                            return String.format("%.1f KB", bytes / 1024.0);
                        } else {
                            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
                        }
                    }
                } catch (Exception e) {
                    LoggerManager.logger("파일 크기 계산 실패: " + e.getMessage());
                }
            }
            return "알 수 없음";
        }
        
        /**
         * 상세한 미디어 정보를 사용자에게 보여주기 위한 포맷
         */
        public String getDetailedInfo() {
            StringBuilder sb = new StringBuilder();
            
            if (title != null && !title.isEmpty()) {
                sb.append("제목: ").append(title).append("\n");
            }
            if (artist != null && !artist.isEmpty()) {
                sb.append("아티스트: ").append(artist).append("\n");
            }
            if (album != null && !album.isEmpty()) {
                sb.append("앨범: ").append(album).append("\n");
            }
            
            sb.append("재생 시간: ").append(getDurationFormatted()).append("\n");
            sb.append("해상도: ").append(getResolution()).append("\n");
            sb.append("타입: ").append(getMediaType()).append("\n");
            
            if (bitrateKbps > 0) {
                sb.append("비트레이트: ").append(bitrateKbps).append(" kbps\n");
            }
            
            sb.append("파일 크기: ").append(getFileSize());
            
            return sb.toString();
        }
        
        @Override
        public String toString() {
            return "MediaInfo{" +
                    "duration=" + getDurationFormatted() +
                    ", bitrate=" + bitrateKbps + "kbps" +
                    ", title='" + title + '\'' +
                    ", artist='" + artist + '\'' +
                    ", resolution=" + getResolution() +
                    ", type=" + getMediaType() +
                    ", hasVideo=" + hasVideo +
                    ", hasAudio=" + hasAudio +
                    '}';
        }
    }
}