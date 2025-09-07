package com.devc.lab.audios.manager;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 오디오 파일 관리 전담 클래스
 * 파일 생성, 임시 파일 관리, 출력 경로 관리 등을 담당
 * 단일 책임 원칙(SRP)에 따라 파일 시스템 작업만 처리
 */
public class AudioFileManager {
    
    private final Context context;
    
    public AudioFileManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * URI에서 임시 파일 생성
     * @param sourceUri 원본 파일 URI
     * @return 생성된 임시 파일 또는 null
     */
    public File createTempFileFromUri(Uri sourceUri) {
        if (sourceUri == null) {
            LoggerManager.logger("❌ createTempFileFromUri: sourceUri가 null");
            return null;
        }
        
        LoggerManager.logger("📂 임시 파일 생성 시작: " + sourceUri.toString());
        
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        File tempFile = null;
        
        try {
            inputStream = context.getContentResolver().openInputStream(sourceUri);
            if (inputStream == null) {
                LoggerManager.logger("❌ InputStream 생성 실패");
                return null;
            }
            
            // 임시 파일 생성
            tempFile = File.createTempFile("audio_temp_", ".tmp", context.getCacheDir());
            outputStream = new FileOutputStream(tempFile);
            
            // 데이터 복사
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            
            outputStream.flush();
            
            LoggerManager.logger("✅ 임시 파일 생성 성공");
            LoggerManager.logger("   → 경로: " + tempFile.getAbsolutePath());
            LoggerManager.logger("   → 크기: " + totalBytes + " bytes");
            
            return tempFile;
            
        } catch (IOException e) {
            LoggerManager.logger("❌ 임시 파일 생성 실패: " + e.getMessage());
            
            // 실패 시 파일 정리
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                LoggerManager.logger("임시 파일 정리: " + (deleted ? "성공" : "실패"));
            }
            
            return null;
            
        } finally {
            // 리소스 정리
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                LoggerManager.logger("⚠️ 스트림 정리 실패: " + e.getMessage());
            }
        }
    }
    
    /**
     * 출력 파일 경로 생성
     * @param outputFileName 출력 파일명
     * @param format 오디오 포맷
     * @return 출력 파일 경로
     */
    public String createOutputPath(String outputFileName, NativeAudioTrimManager.AudioFormat format) {
        if (outputFileName == null || format == null) {
            LoggerManager.logger("❌ createOutputPath: 파라미터가 null");
            return null;
        }
        
        try {
            // 출력 디렉토리 확인/생성
            File outputDir = new File(context.getExternalFilesDir(null), "Audios/Edited");
            if (!outputDir.exists()) {
                boolean created = outputDir.mkdirs();
                LoggerManager.logger("출력 디렉토리 생성: " + (created ? "성공" : "실패"));
                if (!created) {
                    return null;
                }
            }
            
            // 확장자 처리
            String fileName = outputFileName;
            if (!fileName.endsWith(format.getExtension())) {
                fileName = fileName + format.getExtension();
            }
            
            String outputPath = new File(outputDir, fileName).getAbsolutePath();
            LoggerManager.logger("📁 출력 경로 생성: " + outputPath);
            
            return outputPath;
            
        } catch (Exception e) {
            LoggerManager.logger("❌ 출력 경로 생성 실패: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 임시 파일 정리
     * @param tempFile 정리할 임시 파일
     */
    public void cleanupTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            try {
                boolean deleted = tempFile.delete();
                LoggerManager.logger("🗑️ 임시 파일 정리: " + (deleted ? "성공" : "실패"));
                if (!deleted) {
                    LoggerManager.logger("   → 파일: " + tempFile.getAbsolutePath());
                }
            } catch (Exception e) {
                LoggerManager.logger("❌ 임시 파일 정리 실패: " + e.getMessage());
            }
        }
    }
    
    /**
     * 파일 존재 여부 확인
     * @param filePath 파일 경로
     * @return 존재 여부
     */
    public boolean fileExists(String filePath) {
        if (filePath == null) {
            return false;
        }
        
        try {
            File file = new File(filePath);
            return file.exists() && file.isFile() && file.canRead();
        } catch (Exception e) {
            LoggerManager.logger("파일 존재 확인 실패: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 파일 크기 반환
     * @param filePath 파일 경로
     * @return 파일 크기 (bytes), 실패 시 -1
     */
    public long getFileSize(String filePath) {
        if (!fileExists(filePath)) {
            return -1;
        }
        
        try {
            File file = new File(filePath);
            return file.length();
        } catch (Exception e) {
            LoggerManager.logger("파일 크기 확인 실패: " + e.getMessage());
            return -1;
        }
    }
}