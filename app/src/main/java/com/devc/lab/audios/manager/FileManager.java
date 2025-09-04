package com.devc.lab.audios.manager;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Scoped Storage 정책 완전 적용 파일 매니저
 * API 29+ Scoped Storage 규정을 준수하여 파일 시스템 관리
 * 앱 전용 디렉토리 구조와 MediaStore API 통합
 */
public class FileManager {
    
    // 파일 선택 콜백 인터페이스
    public interface FilePickerCallback {
        void onFileSelected(Uri fileUri, String fileName);
        void onFileSelectionCanceled();
        void onFileSelectionError(String error);
    }
    
    private Context context;
    
    // 앱 전용 디렉토리 구조
    public static final String AUDIOS_DIR = "Audios";
    public static final String CONVERTED_DIR = "Converted";
    public static final String EDITED_DIR = "Edited"; 
    public static final String TEMP_DIR = "temp";
    
    // 파일 선택 요청 코드
    public static final int REQUEST_CODE_PICK_FILE = 1001;
    
    // 지원하는 비디오 확장자
    private static final String[] VIDEO_EXTENSIONS = {
        ".mp4", ".avi", ".mov", ".mkv", ".wmv", ".flv", ".webm", ".3gp"
    };
    
    // 지원하는 오디오 확장자  
    private static final String[] AUDIO_EXTENSIONS = {
        ".mp3", ".wav", ".aac", ".flac", ".ogg", ".wma", ".m4a"
    };

    public FileManager(Context context) {
        this.context = context;
        createAppDirectories();
    }

    public FileManager() {
        // 기본 생성자 (다른 Manager에서 사용)
    }
    
    
    /**
     * 앱 전용 디렉토리 구조 생성
     */
    private void createAppDirectories() {
        if (context == null) return;
        
        try {
            // 앱 전용 디렉토리 경로
            File audiosDir = new File(context.getExternalFilesDir(null), AUDIOS_DIR);
            File convertedDir = new File(audiosDir, CONVERTED_DIR);
            File editedDir = new File(audiosDir, EDITED_DIR);
            File tempDir = new File(audiosDir, TEMP_DIR);
            
            // 디렉토리 생성
            if (!audiosDir.exists()) {
                audiosDir.mkdirs();
                LoggerManager.logger("Audios 디렉토리 생성: " + audiosDir.getAbsolutePath());
            }
            
            if (!convertedDir.exists()) {
                convertedDir.mkdirs();
                LoggerManager.logger("Converted 디렉토리 생성: " + convertedDir.getAbsolutePath());
            }
            
            if (!editedDir.exists()) {
                editedDir.mkdirs();
                LoggerManager.logger("Edited 디렉토리 생성: " + editedDir.getAbsolutePath());
            }
            
            if (!tempDir.exists()) {
                tempDir.mkdirs();
                LoggerManager.logger("Temp 디렉토리 생성: " + tempDir.getAbsolutePath());
            }
            
        } catch (Exception e) {
            LoggerManager.logger("디렉토리 생성 실패: " + e.getMessage());
        }
    }
    

    /**
     * URI에서 실제 파일 경로 가져오기 (Scoped Storage 호환)
     * 주의: Scoped Storage에서는 직접 경로 접근이 제한되므로 InputStream 사용 권장
     */
    public String getPathFromUri(Uri uri) {
        if (uri == null) return null;
        
        String path = null;
        Context ctx = this.context;
        if (ctx == null) return null;

        try {
            // DocumentProvider를 사용하여 경로를 가져옵니다
            if (DocumentsContract.isDocumentUri(ctx, uri)) {
                // ExternalStorageProvider
                if (isExternalStorageDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];

                    if ("primary".equalsIgnoreCase(type)) {
                        // Scoped Storage에서는 getExternalStorageDirectory() 사용 제한
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // Scoped Storage에서는 직접 경로보다 URI 사용 권장
                            LoggerManager.logger("Scoped Storage: 직접 경로 접근 제한됨");
                            return null;
                        } else {
                            path = Environment.getExternalStorageDirectory() + "/" + split[1];
                        }
                    }
                }
                // DownloadsProvider
                else if (isDownloadsDocument(uri)) {
                    final String id = DocumentsContract.getDocumentId(uri);

                    if (id.startsWith("raw:")) {
                        path = id.substring(4);
                    } else {
                        try {
                            final Uri contentUri = ContentUris.withAppendedId(
                                    Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                            path = getDataColumn(ctx, contentUri, null, null);
                        } catch (NumberFormatException e) {
                            LoggerManager.logger("Downloads URI 처리 실패: " + e.getMessage());
                        }
                    }
                }
                // MediaProvider
                else if (isMediaDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];

                    Uri contentUri = null;
                    if ("image".equals(type)) {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    } else if ("video".equals(type)) {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    } else if ("audio".equals(type)) {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    }

                    if (contentUri != null) {
                        final String selection = "_id=?";
                        final String[] selectionArgs = new String[]{split[1]};
                        path = getDataColumn(ctx, contentUri, selection, selectionArgs);
                    }
                }
            }
            // MediaStore (및 일반적인 경우)
            else if ("content".equalsIgnoreCase(uri.getScheme())) {
                path = getDataColumn(ctx, uri, null, null);
            }
            // 파일 스키마를 사용하는 경우
            else if ("file".equalsIgnoreCase(uri.getScheme())) {
                path = uri.getPath();
            }
        } catch (Exception e) {
            LoggerManager.logger("URI 경로 변환 실패: " + e.getMessage());
        }

        return path;
    }

    public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    public boolean isVideoFile(Uri uri) {
        String mimeType = getMimeType(this.context, uri);
        return mimeType != null && mimeType.startsWith("video/");
    }

    /**
     * URI를 앱 전용 임시 디렉토리로 복사 (Scoped Storage 호환)
     */
    public File copyToTemporaryFile(Uri uri, Context context) throws IOException {
        if (uri == null || context == null) {
            throw new IllegalArgumentException("URI 또는 Context가 null입니다.");
        }
        
        // 임시 디렉토리 생성
        File tempDir = new File(context.getExternalFilesDir(null), AUDIOS_DIR + "/" + TEMP_DIR);
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        
        // 원본 파일명 가져오기
        String originalFileName = getFileName(context, uri);
        String extension = getFileExtension(originalFileName);
        
        // 임시 파일 생성
        String tempFileName = "temp_" + System.currentTimeMillis() + extension;
        File tempFile = new File(tempDir, tempFileName);

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(tempFile)) {
            
            if (inputStream == null) {
                throw new IOException("InputStream을 열 수 없습니다.");
            }
            
            byte[] buffer = new byte[8 * 1024]; // 8KB 버퍼
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            
            outputStream.flush();
            LoggerManager.logger("임시 파일 복사 완료: " + tempFile.getAbsolutePath() + " (" + totalBytes + " bytes)");
            
        } catch (IOException e) {
            // 복사 실패 시 임시 파일 삭제
            if (tempFile.exists()) {
                tempFile.delete();
            }
            LoggerManager.logger("임시 파일 복사 실패: " + e.getMessage());
            throw e;
        }
        
        return tempFile;
    }
    
    /**
     * 앱 전용 변환 디렉토리로 파일 복사
     */
    public File copyToConvertedDirectory(Uri uri, Context context, String customFileName) throws IOException {
        if (uri == null || context == null) {
            throw new IllegalArgumentException("URI 또는 Context가 null입니다.");
        }
        
        File convertedDir = getConvertedDirectory(context);
        if (!convertedDir.exists()) {
            convertedDir.mkdirs();
        }
        
        String fileName = customFileName != null ? customFileName : 
                         "converted_" + System.currentTimeMillis() + ".mp3";
        
        // 중복 파일명 처리
        fileName = getUniqueFileName(new File(convertedDir, fileName).getAbsolutePath());
        File targetFile = new File(fileName);
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(targetFile)) {
            
            if (inputStream == null) {
                throw new IOException("InputStream을 열 수 없습니다.");
            }
            
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            outputStream.flush();
            LoggerManager.logger("변환 디렉토리 복사 완료: " + targetFile.getAbsolutePath());
        }
        
        return targetFile;
    }
    
    /**
     * 앱 전용 디렉토리 경로 반환 메서드들
     */
    public File getAudiosDirectory(Context context) {
        return new File(context.getExternalFilesDir(null), AUDIOS_DIR);
    }
    
    public File getConvertedDirectory(Context context) {
        return new File(getAudiosDirectory(context), CONVERTED_DIR);
    }
    
    public File getEditedDirectory(Context context) {
        return new File(getAudiosDirectory(context), EDITED_DIR);
    }
    
    public File getTempDirectory(Context context) {
        return new File(getAudiosDirectory(context), TEMP_DIR);
    }
    
    /**
     * 변환 결과를 위한 출력 파일 경로 생성
     */
    public String createOutputFilePath(Context context, String inputFileName, String outputExtension) {
        File convertedDir = getConvertedDirectory(context);
        if (!convertedDir.exists()) {
            convertedDir.mkdirs();
        }
        
        String baseName = removeFileExtension(inputFileName);
        String outputFileName = baseName + outputExtension;
        String outputPath = new File(convertedDir, outputFileName).getAbsolutePath();
        
        // 중복 파일명 처리
        return getUniqueFileName(outputPath);
    }
    
    /**
     * MediaStore API를 통한 Downloads 폴더 저장 (Scoped Storage 호환)
     */
    public Uri saveToDownloadsWithMediaStore(Context context, String sourceFilePath, String fileName) {
        try {
            // 외부 저장소에 파일을 저장하기 위한 ContentValues 준비
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            
            // 파일 확장자에 따른 MIME 타입 설정
            String mimeType = getMimeTypeFromExtension(getFileExtension(fileName));
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            }

            Uri externalUri = context.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

            if (externalUri != null) {
                try (InputStream inputStream = new FileInputStream(sourceFilePath);
                     OutputStream outputStream = context.getContentResolver().openOutputStream(externalUri)) {
                    
                    if (outputStream == null) {
                        throw new IOException("OutputStream을 열 수 없습니다.");
                    }
                    
                    byte[] buffer = new byte[8 * 1024];
                    int bytesRead;
                    
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    
                    outputStream.flush();
                }

                // API 29+ 에서 IS_PENDING 플래그 해제
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear();
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    context.getContentResolver().update(externalUri, values, null, null);
                }

                LoggerManager.logger("MediaStore를 통한 Downloads 저장 완료: " + fileName);
                return externalUri;
            }
            
        } catch (Exception e) {
            LoggerManager.logger("MediaStore 저장 실패: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 파일 확장자에서 MIME 타입 반환
     */
    private String getMimeTypeFromExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return "application/octet-stream";
        }
        
        String lowerExt = extension.toLowerCase();
        switch (lowerExt) {
            case ".mp3": return "audio/mpeg";
            case ".wav": return "audio/wav";
            case ".aac": return "audio/aac";
            case ".flac": return "audio/flac";
            case ".ogg": return "audio/ogg";
            case ".m4a": return "audio/mp4";
            case ".mp4": return "video/mp4";
            case ".avi": return "video/x-msvideo";
            case ".mov": return "video/quicktime";
            default: return "application/octet-stream";
        }
    }
    
    /**
     * 파일 확장자 추출
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex);
        }
        
        return "";
    }
    
    /**
     * 파일명에서 확장자 제거
     */
    private String removeFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return fileName;
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        
        return fileName;
    }
    
    /**
     * 임시 파일 정리
     */
    public void clearTempFiles(Context context) {
        try {
            File tempDir = getTempDirectory(context);
            if (tempDir.exists() && tempDir.isDirectory()) {
                File[] tempFiles = tempDir.listFiles();
                if (tempFiles != null) {
                    for (File file : tempFiles) {
                        if (file.isFile()) {
                            boolean deleted = file.delete();
                            LoggerManager.logger("임시 파일 삭제: " + file.getName() + " (" + deleted + ")");
                        }
                    }
                }
            }
        } catch (Exception e) {
            LoggerManager.logger("임시 파일 정리 실패: " + e.getMessage());
        }
    }
    
    /**
     * 디렉토리 내 파일 목록 가져오기
     */
    public List<File> getFilesInDirectory(File directory) {
        List<File> fileList = new ArrayList<>();
        
        if (directory != null && directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        fileList.add(file);
                    }
                }
            }
        }
        
        return fileList;
    }

    public String getFileName(Context context, Uri uri) {
        String fileName = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    fileName = cursor.getString(nameIndex);
                }
            }
        }
        if (fileName == null) {
            fileName = uri.getPath();
            int cut = fileName.lastIndexOf('/');
            if (cut != -1) {
                fileName = fileName.substring(cut + 1);
            }
        }
        return fileName;
    }


    private static String getMimeType(Context context, Uri uri) {
        String mimeType = null;
        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver cr = context.getContentResolver();
            mimeType = cr.getType(uri);
        } else {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
        }
        return mimeType;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static int getRequestCodePickFile() {
        return REQUEST_CODE_PICK_FILE;
    }

    public String getRenameExtension(String path){
        String originalPath = path;
        int dotIndex = originalPath.lastIndexOf('.');

        String newPath;
        if (dotIndex > 0 && dotIndex < originalPath.length() - 1) {
            newPath = originalPath.substring(0, dotIndex) + ".mp3";
        } else {
            // 확장자가 없는 경우, 원본 경로에 '.mp3'를 추가
            newPath = originalPath + ".mp3";
        }

        return getUniqueFileName(newPath);
    }

    public String getUniqueFileName(String filePath) {
        File file = new File(filePath);
        String parentPath = file.getParent();
        String fileName = file.getName();
        String baseName;
        String extension = "";
        int number = 1;

        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = fileName.substring(lastDotIndex);
            baseName = fileName.substring(0, lastDotIndex);
        } else {
            baseName = fileName;
        }

        int underscoreIndex = baseName.lastIndexOf('_');
        if (underscoreIndex > 0 && underscoreIndex < baseName.length() - 1) {
            String numberStr = baseName.substring(underscoreIndex + 1);
            try {
                number = Integer.parseInt(numberStr) + 1;
                baseName = baseName.substring(0, underscoreIndex);
            } catch (NumberFormatException e) {
                // '_' 뒤에 숫자가 아닌 경우, 기본 번호 1을 사용
            }
        }

        while (new File(parentPath, baseName + "_" + number + extension).exists()) {
            number++;
        }

        return new File(parentPath, baseName + "_" + number + extension).getAbsolutePath();

    }

    public void scopedStorageMoveDownloadFolderWithFile(String fileName, Context context, String outputPath) {
        // 명령 실행 성공, 임시 파일을 최종 위치로 이동
        try {
            // 외부 저장소에 파일을 저장하기 위한 ContentValues 준비
            ContentValues valuesDownloadFolder = new ContentValues();
            valuesDownloadFolder.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName); // 파일 이름 설정
            valuesDownloadFolder.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream"); // MIME 타입 설정
            valuesDownloadFolder.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS); // 저장 위치 설정

            Uri externalUri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    , valuesDownloadFolder);

            if (externalUri != null) {
                try (InputStream inputStream = new FileInputStream(outputPath);
                     OutputStream outputStream = context.getContentResolver().openOutputStream(externalUri)) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = inputStream.read(buf)) > 0) {
                        outputStream.write(buf, 0, len);
                    }
                }

                // 파일 복사 후 원본 파일 삭제
                new File(outputPath).delete();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
