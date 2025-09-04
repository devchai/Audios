package com.devc.lab.audios.model;

import android.net.Uri;
import java.util.Date;

public class AudioFile {
    private String fileName;
    private String filePath;
    private Uri fileUri;
    private long fileSize;
    private long duration; // 밀리초 단위
    private String format; // MP3, WAV, AAC 등
    private int bitrate;
    private int sampleRate;
    private Date createdDate;
    private Date modifiedDate;
    private String displayName;
    
    public AudioFile() {
    }
    
    public AudioFile(String fileName, String filePath) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.displayName = fileName;
    }
    
    // Getters and Setters
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
        if (displayName == null || displayName.isEmpty()) {
            this.displayName = fileName;
        }
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public Uri getFileUri() {
        return fileUri;
    }
    
    public void setFileUri(Uri fileUri) {
        this.fileUri = fileUri;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public void setDuration(long duration) {
        this.duration = duration;
    }
    
    public String getFormat() {
        return format;
    }
    
    public void setFormat(String format) {
        this.format = format;
    }
    
    public int getBitrate() {
        return bitrate;
    }
    
    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }
    
    public int getSampleRate() {
        return sampleRate;
    }
    
    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }
    
    public Date getCreatedDate() {
        return createdDate;
    }
    
    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }
    
    public Date getModifiedDate() {
        return modifiedDate;
    }
    
    public void setModifiedDate(Date modifiedDate) {
        this.modifiedDate = modifiedDate;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    // 유틸리티 메서드
    public String getFormattedFileSize() {
        if (fileSize == 0) return "알 수 없음";
        
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    public String getFormattedDuration() {
        if (duration == 0) return "00:00";
        
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        
        if (minutes >= 60) {
            long hours = minutes / 60;
            minutes = minutes % 60;
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
    
    public String getFileExtension() {
        if (fileName == null || !fileName.contains(".")) {
            return format != null ? format.toLowerCase() : "unknown";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
    
    @Override
    public String toString() {
        return "AudioFile{" +
                "fileName='" + fileName + '\'' +
                ", fileSize=" + getFormattedFileSize() +
                ", duration=" + getFormattedDuration() +
                ", format='" + format + '\'' +
                '}';
    }
}