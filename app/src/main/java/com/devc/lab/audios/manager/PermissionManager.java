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
    // manageStorageLauncher 제거: MANAGE_EXTERNAL_STORAGE 권한 불필요
    
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
        
        // MANAGE_EXTERNAL_STORAGE 런처 제거: Google Play 정책 준수를 위한 권한 간소화
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
        
        LoggerManager.logger("=== 권한 요청 시작 ===");
        LoggerManager.logger("Android API 레벨: " + Build.VERSION.SDK_INT);
        LoggerManager.logger("필수 권한 목록: " + getRequiredPermissions());
        
        // 이미 모든 권한이 있는 경우
        if (hasAllRequiredPermissions()) {
            LoggerManager.logger("모든 필수 권한이 이미 허용되어 있음");
            if (callback != null) {
                callback.onPermissionGranted(getRequiredPermissions());
            }
            return;
        }
        
        List<String> missingPermissions = getMissingPermissions();
        LoggerManager.logger("누락된 권한 목록: " + missingPermissions);
        
        // 필수 미디어 권한만 요청 (MANAGE_EXTERNAL_STORAGE 제거)
        if (!missingPermissions.isEmpty()) {
            // 일반 권한 요청
            LoggerManager.logger("일반 권한 다이얼로그 표시: " + missingPermissions);
            permissionLauncher.launch(missingPermissions.toArray(new String[0]));
        } else {
            LoggerManager.logger("요청할 권한이 없음");
        }
    }
    
    // requestManageExternalStoragePermission() 메서드 제거
    // Google Play 정책 준수: MANAGE_EXTERNAL_STORAGE 권한 완전 제거
    
    /**
     * 권한 요청 결과 처리
     */
    private void handlePermissionResults(Map<String, Boolean> results) {
        LoggerManager.logger("=== 권한 요청 결과 처리 ===");
        LoggerManager.logger("권한 요청 결과: " + results);
        
        updatePermissionStatus();
        
        if (callback == null) {
            LoggerManager.logger("콜백이 null이므로 결과 처리 생략");
            return;
        }
        
        List<String> granted = new ArrayList<>();
        List<String> denied = new ArrayList<>();
        List<String> permanentlyDenied = new ArrayList<>();
        
        for (Map.Entry<String, Boolean> entry : results.entrySet()) {
            String permission = entry.getKey();
            boolean isGranted = entry.getValue();
            
            LoggerManager.logger("권한: " + permission + " -> " + (isGranted ? "허용" : "거부"));
            
            if (isGranted) {
                granted.add(permission);
            } else {
                boolean shouldShowRationale = activity.shouldShowRequestPermissionRationale(permission);
                LoggerManager.logger("shouldShowRequestPermissionRationale(" + permission + "): " + shouldShowRationale);
                
                if (shouldShowRationale) {
                    denied.add(permission);
                } else {
                    permanentlyDenied.add(permission);
                }
            }
        }
        
        LoggerManager.logger("최종 결과 - 허용: " + granted + ", 거부: " + denied + ", 영구거부: " + permanentlyDenied);
        
        // 허용된 권한이 있을 때만 onPermissionGranted 호출
        if (!granted.isEmpty()) {
            LoggerManager.logger("권한 허용 콜백 호출");
            callback.onPermissionGranted(granted);
        }
        
        // 거부된 권한이 있을 때만 onPermissionDenied 호출  
        if (!denied.isEmpty()) {
            LoggerManager.logger("권한 거부 콜백 호출");
            callback.onPermissionDenied(denied);
        }
        
        // 영구 거부된 권한이 있을 때만 onPermissionPermanentlyDenied 호출
        if (!permanentlyDenied.isEmpty()) {
            LoggerManager.logger("권한 영구 거부 콜백 호출");
            callback.onPermissionPermanentlyDenied(permanentlyDenied);
        }
    }
    
    /**
     * 모든 필수 권한이 부여되었는지 확인
     * 기본 미디어 권한만 체크 (MANAGE_EXTERNAL_STORAGE는 선택적)
     */
    public boolean hasAllRequiredPermissions() {
        List<String> required = getRequiredPermissions();
        for (String permission : required) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        
        return true;
    }
    
    // hasManageExternalStoragePermission() 메서드 제거
    // Scoped Storage 방식으로 완전 전환하여 해당 권한 불필요
    
    // hasAllPermissionsIncludingOptional() 메서드 제거
    // 필수 미디어 권한만 확인하는 hasAllRequiredPermissions() 사용 권장
    
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
        
        // 필수 권한 상태
        List<String> required = getRequiredPermissions();
        boolean allRequiredGranted = true;
        for (String permission : required) {
            boolean granted = hasPermission(permission);
            if (!granted) allRequiredGranted = false;
            sb.append("- ").append(getPermissionName(permission)).append(": ")
              .append(granted ? "허용" : "거부").append("\n");
        }
        
        // MANAGE_EXTERNAL_STORAGE 권한 상태 표시 제거
        // Google Play 정책 준수로 해당 권한 사용 안함
        
        // 전체 상태 요약 (간소화)
        sb.append("\n상태 요약: ");
        if (allRequiredGranted) {
            sb.append("필수 미디어 권한 허용됨 (모든 기능 이용 가능)");
        } else {
            sb.append("필수 권한 없음 (앱 기능 제한됨)");
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
