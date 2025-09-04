package com.devc.lab.audios.manager;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 현대적인 권한 관리 시스템
 * ActivityResultLauncher를 사용하여 최신 Android 권한 요청 방식 적용
 * API 레벨별 권한 차이 처리 (API 29-32 vs API 33+)
 */
public class PermissionManager {
    
    // 권한 콜백 인터페이스
    public interface PermissionCallback {
        void onPermissionGranted(List<String> grantedPermissions);
        void onPermissionDenied(List<String> deniedPermissions);
        void onPermissionPermanentlyDenied(List<String> deniedPermissions);
    }
    
    private AppCompatActivity activity;
    private PermissionCallback callback;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Intent> manageStorageLauncher;
    
    // 권한 상태 LiveData
    private MutableLiveData<Boolean> storagePermissionStatus = new MutableLiveData<>(false);
    private MutableLiveData<Boolean> allPermissionsGranted = new MutableLiveData<>(false);
    
    public PermissionManager(AppCompatActivity activity) {
        this.activity = activity;
        initializePermissionLaunchers();
        updatePermissionStatus();
    }
    
    /**
     * ActivityResultLauncher 초기화
     */
    private void initializePermissionLaunchers() {
        // 다중 권한 요청 런처
        permissionLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            this::handlePermissionResults
        );
        
        // MANAGE_EXTERNAL_STORAGE 권한을 위한 설정 화면 런처
        manageStorageLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                updatePermissionStatus();
                if (callback != null) {
                    if (hasAllRequiredPermissions()) {
                        callback.onPermissionGranted(getRequiredPermissions());
                    } else {
                        callback.onPermissionDenied(getMissingPermissions());
                    }
                }
            }
        );
    }
    
    /**
     * API 레벨에 따른 필수 권한 목록 반환
     */
    private List<String> getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else { // API 29-32
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        
        return permissions;
    }
    
    /**
     * 권한 요청 메인 메서드
     */
    public void requestStoragePermissions(PermissionCallback callback) {
        this.callback = callback;
        
        // 이미 모든 권한이 있는 경우
        if (hasAllRequiredPermissions()) {
            if (callback != null) {
                callback.onPermissionGranted(getRequiredPermissions());
            }
            return;
        }
        
        List<String> missingPermissions = getMissingPermissions();
        
        // API 30+ MANAGE_EXTERNAL_STORAGE 권한 처리 (선택적)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
            !Environment.isExternalStorageManager()) {
            requestManageExternalStoragePermission();
        } else if (!missingPermissions.isEmpty()) {
            // 일반 권한 요청
            permissionLauncher.launch(missingPermissions.toArray(new String[0]));
        }
    }
    
    /**
     * MANAGE_EXTERNAL_STORAGE 권한 요청 (API 30+)
     */
    private void requestManageExternalStoragePermission() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
            intent.setData(uri);
            manageStorageLauncher.launch(intent);
        } catch (Exception e) {
            LoggerManager.logger("MANAGE_EXTERNAL_STORAGE 권한 요청 실패: " + e.getMessage());
            // Fallback: 일반 권한 요청
            List<String> missingPermissions = getMissingPermissions();
            if (!missingPermissions.isEmpty()) {
                permissionLauncher.launch(missingPermissions.toArray(new String[0]));
            }
        }
    }
    
    /**
     * 권한 요청 결과 처리
     */
    private void handlePermissionResults(Map<String, Boolean> results) {
        updatePermissionStatus();
        
        if (callback == null) return;
        
        List<String> granted = new ArrayList<>();
        List<String> denied = new ArrayList<>();
        List<String> permanentlyDenied = new ArrayList<>();
        
        for (Map.Entry<String, Boolean> entry : results.entrySet()) {
            String permission = entry.getKey();
            boolean isGranted = entry.getValue();
            
            if (isGranted) {
                granted.add(permission);
            } else {
                if (activity.shouldShowRequestPermissionRationale(permission)) {
                    denied.add(permission);
                } else {
                    permanentlyDenied.add(permission);
                }
            }
        }
        
        if (!granted.isEmpty()) {
            callback.onPermissionGranted(granted);
        }
        
        if (!denied.isEmpty()) {
            callback.onPermissionDenied(denied);
        }
        
        if (!permanentlyDenied.isEmpty()) {
            callback.onPermissionPermanentlyDenied(permanentlyDenied);
        }
    }
    
    /**
     * 모든 필수 권한이 부여되었는지 확인
     */
    public boolean hasAllRequiredPermissions() {
        List<String> required = getRequiredPermissions();
        for (String permission : required) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        
        // API 30+ 에서는 MANAGE_EXTERNAL_STORAGE 권한도 확인 (선택적)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        
        return true;
    }
    
    /**
     * 특정 권한 확인
     */
    public boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * 저장소 권한 확인 (하위 호환성)
     */
    public boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return hasPermission(Manifest.permission.READ_MEDIA_AUDIO) &&
                   hasPermission(Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            return hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }
    
    /**
     * 누락된 권한 목록 반환
     */
    private List<String> getMissingPermissions() {
        List<String> missing = new ArrayList<>();
        List<String> required = getRequiredPermissions();
        
        for (String permission : required) {
            if (!hasPermission(permission)) {
                missing.add(permission);
            }
        }
        
        return missing;
    }
    
    /**
     * 권한 상태 업데이트
     */
    private void updatePermissionStatus() {
        storagePermissionStatus.postValue(hasStoragePermission());
        allPermissionsGranted.postValue(hasAllRequiredPermissions());
    }
    
    /**
     * 앱 설정 화면으로 이동
     */
    public void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
            intent.setData(uri);
            activity.startActivity(intent);
        } catch (Exception e) {
            LoggerManager.logger("앱 설정 화면 열기 실패: " + e.getMessage());
        }
    }
    
    // LiveData getters
    public LiveData<Boolean> getStoragePermissionStatus() {
        return storagePermissionStatus;
    }
    
    public LiveData<Boolean> getAllPermissionsGranted() {
        return allPermissionsGranted;
    }
    
    /**
     * 권한 상태를 문자열로 반환 (디버깅용)
     */
    public String getPermissionStatusString() {
        StringBuilder sb = new StringBuilder();
        sb.append("권한 상태:\n");
        
        List<String> required = getRequiredPermissions();
        for (String permission : required) {
            boolean granted = hasPermission(permission);
            sb.append("- ").append(getPermissionName(permission)).append(": ")
              .append(granted ? "허용" : "거부").append("\n");
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sb.append("- MANAGE_EXTERNAL_STORAGE: ")
              .append(Environment.isExternalStorageManager() ? "허용" : "거부");
        }
        
        return sb.toString();
    }
    
    /**
     * 권한 이름을 한국어로 변환
     */
    private String getPermissionName(String permission) {
        switch (permission) {
            case Manifest.permission.READ_EXTERNAL_STORAGE:
                return "외부 저장소 읽기";
            case Manifest.permission.READ_MEDIA_AUDIO:
                return "오디오 파일 읽기";
            case Manifest.permission.READ_MEDIA_VIDEO:
                return "비디오 파일 읽기";
            default:
                return permission;
        }
    }
}
