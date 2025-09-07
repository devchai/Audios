package com.devc.lab.audios.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.devc.lab.audios.R;
import com.devc.lab.audios.databinding.FragmentConvertBinding;
import com.devc.lab.audios.databinding.DialogConversionSettingsBinding;
import com.devc.lab.audios.manager.FileManager;
import com.devc.lab.audios.manager.ToastManager;
import com.devc.lab.audios.manager.AudioConversionManager;
import com.devc.lab.audios.manager.DialogManager;
import com.devc.lab.audios.model.ConversionSettings;
import com.devc.lab.audios.activity.MainActivity;
import com.devc.lab.audios.model.MainViewModel;

public class ConvertFragment extends Fragment {
    private FragmentConvertBinding binding;
    private FileManager fileManager;
    private ToastManager toastManager;
    private AudioConversionManager audioConversionManager;
    private DialogManager dialogManager;
    
    // 현재 변환 설정 저장
    private ConversionSettings currentConversionSettings;
    
    // Activity Result Launchers (빠른 작업 제거로 단순화)
    private ActivityResultLauncher<String[]> generalFileLauncher;
    
    public static ConvertFragment newInstance() {
        return new ConvertFragment();
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActivityResultLaunchers();
    }
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentConvertBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initManagers();
        setupUI();
    }
    
    private void initManagers() {
        fileManager = new FileManager(getContext());
        toastManager = new ToastManager(getContext());
        audioConversionManager = new AudioConversionManager();
        dialogManager = new DialogManager(getContext());
        
        // AudioConversionManager 콜백 설정
        setupAudioConversionCallbacks();
    }
    
    private void setupAudioConversionCallbacks() {
        // 변환 시작 콜백 (AudioConversionManager는 이미 UI 스레드에서 콜백 실행)
        audioConversionManager.setOnStartListener(() -> {
            // Fragment 생명주기 및 UI 스레드 안전성 체크
            if (isFragmentActive()) {
                com.devc.lab.audios.manager.LoggerManager.logger("Native API 변환 시작");
            }
        });
        
        // 진행률 업데이트 콜백 (UI 스레드에서 이미 실행됨)
        audioConversionManager.setOnProgressListener(progress -> {
            // Fragment 생명주기 체크 후 UI 업데이트
            if (isFragmentActive() && dialogManager != null) {
                dialogManager.updateConversionProgress(progress);
                com.devc.lab.audios.manager.LoggerManager.logger("변환 진행률: " + progress + "%");
            }
        });
        
        // 변환 완료 콜백 (UI 스레드에서 이미 실행됨)
        audioConversionManager.setOnCompletionListener((inputPath, outputPath) -> {
            // Fragment 생명주기 체크 후 UI 업데이트
            if (isFragmentActive() && dialogManager != null && toastManager != null) {
                dialogManager.dismissProgressDialog();
                toastManager.showToastShort("변환 완료: " + new java.io.File(outputPath).getName());
                com.devc.lab.audios.manager.LoggerManager.logger("변환 완료: " + outputPath);
            }
        });
        
        // 변환 실패 콜백 (UI 스레드에서 이미 실행됨)
        audioConversionManager.setOnFailureListener((message, reason) -> {
            // Fragment 생명주기 체크 후 UI 업데이트
            if (isFragmentActive() && dialogManager != null && toastManager != null) {
                dialogManager.dismissProgressDialog();
                toastManager.showToastShort("변환 실패: " + message);
                com.devc.lab.audios.manager.LoggerManager.logger("변환 실패: " + message + " - " + reason);
            }
        });
    }
    
    private void setupActivityResultLaunchers() {
        // 모든 미디어 파일 선택 런처 (빠른 작업 제거로 단순화)
        generalFileLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    handleSelectedFile(uri, "general");
                }
            }
        );
    }
    
    private void setupUI() {
        // 메인 파일 선택 버튼 클릭 리스너 (빠른 작업 제거로 단순화)
        binding.btnSelectFileContainer.setOnClickListener(v -> selectFile());
    }
    
    private void selectFile() {
        // 모든 미디어 파일 타입 선택
        generalFileLauncher.launch(new String[]{"video/*", "audio/*"});
    }
    
    // 빠른 작업 제거로 selectVideoFile, selectAudioFile 메서드 삭제
    // 모든 파일 선택은 selectFile() 메서드를 통해 통합 처리
    
    private void handleSelectedFile(Uri fileUri, String fileType) {
        try {
            String fileName = fileManager.getFileName(getContext(), fileUri);
            
            // ViewModel에 선택된 파일 정보 전달
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                MainViewModel viewModel = activity.getMainViewModel();
                viewModel.setSelectedFile(fileUri, fileName);
            }
            
            // 선택된 파일 정보 표시
            toastManager.showToastShort("선택된 파일: " + fileName);
            
            // TODO: 변환 설정 다이얼로그 표시
            showConversionSettingsDialog(fileUri, fileName, fileType);
            
        } catch (Exception e) {
            e.printStackTrace();
            
            // ViewModel에 오류 전달
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                MainViewModel viewModel = activity.getMainViewModel();
                viewModel.onFileSelectionError(e.getMessage());
            }
            
            toastManager.showToastShort(getString(R.string.file_not_found));
        }
    }
    
    private void showConversionSettingsDialog(Uri fileUri, String fileName, String fileType) {
        DialogConversionSettingsBinding dialogBinding = DialogConversionSettingsBinding.inflate(getLayoutInflater());
        
        // 샘플 레이트 드로프다운 설정
        setupSampleRateDropdown(dialogBinding);
        
        // 비트레이트 슬라이더 설정
        setupBitrateSlider(dialogBinding);
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogBinding.getRoot())
                .setCancelable(true);
                
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        
        // 취소 버튼 클릭 리스너
        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        // 변환 시작 버튼 클릭 리스너
        dialogBinding.btnStartConversion.setOnClickListener(v -> {
            ConversionSettings settings = getConversionSettingsFromDialog(dialogBinding);
            settings.setInputPath(fileUri.toString());
            
            // 출력 파일명 생성 (경로는 AudioConversionManager에서 자동 생성)
            String outputFileName = generateOutputFileName(fileName, settings.getFormat());
            settings.setOutputPath(outputFileName); // 이제 파일명만 저장
            
            // 현재 변환 설정 저장 (콜백에서 사용)
            currentConversionSettings = settings;
            
            startConversion(fileUri, settings);
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void setupSampleRateDropdown(DialogConversionSettingsBinding dialogBinding) {
        // Native API에서는 샘플레이트 설정이 무시되므로 제거
        // UI 간소화로 사용자 혼란 방지
    }
    
    private void setupBitrateSlider(DialogConversionSettingsBinding dialogBinding) {
        // Native API에서는 비트레이트 설정이 무시되므로 제거
        // UI 간소화로 사용자 혼란 방지
    }
    
    private ConversionSettings getConversionSettingsFromDialog(DialogConversionSettingsBinding dialogBinding) {
        ConversionSettings settings = new ConversionSettings();
        
        // M4A 포맷으로 고정 설정 (WEBM 지원 제거)
        settings.setFormat(ConversionSettings.AudioFormat.M4A);
        
        // Native API는 원본 품질을 유지하므로 기본값 사용
        settings.setBitrate(128); // 기본값 (실제로는 원본 비트레이트 유지)
        settings.setSampleRate(44100); // 기본값 (실제로는 원본 샘플레이트 유지)
        
        return settings;
    }
    
    private String generateOutputFileName(String fileName, ConversionSettings.AudioFormat format) {
        // 확장자를 제거하고 새 확장자로 대체
        String nameWithoutExtension = fileName.contains(".") ? 
            fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
        
        // FileManager의 sanitizeFileName 메서드를 사용하여 안전한 파일명 생성
        String safeBaseName = fileManager.sanitizeFileName(nameWithoutExtension);
        String outputFileName = safeBaseName + "_converted." + format.getExtension();
        
        com.devc.lab.audios.manager.LoggerManager.logger("파일명 변환: '" + fileName + "' -> '" + outputFileName + "'");
        
        return outputFileName;
    }
    
    private void startConversion(android.net.Uri fileUri, ConversionSettings settings) {
        toastManager.showToastShort("변환을 시작합니다: " + settings.getFormat());
        
        // 새로운 진행률 다이얼로그 표시
        String fileName = settings.getOutputPath();
        dialogManager.showConversionProgressDialog(fileName, settings);
        
        com.devc.lab.audios.manager.LoggerManager.logger("Conversion Settings: " + settings.toString());
        
        // AudioConversionManager 출력 포맷 변환
        AudioConversionManager.OutputFormat outputFormat = convertToAudioConversionFormat(settings.getFormat());
        AudioConversionManager.AudioQuality audioQuality = convertToAudioConversionQuality(settings.getBitrate());
        
        try {
            // 실제 AudioConversion 변환 시작
            audioConversionManager.extractAudioFromVideoUri(
                fileUri, 
                settings.getOutputPath(), // 파일명
                outputFormat, 
                audioQuality, 
                getContext()
            );
            
        } catch (Exception e) {
            com.devc.lab.audios.manager.LoggerManager.logger("변환 시작 실패: " + e.getMessage());
            dialogManager.dismissProgressDialog();
            toastManager.showToastShort("변환 시작 실패: " + e.getMessage());
        }
    }
    
    private AudioConversionManager.OutputFormat convertToAudioConversionFormat(ConversionSettings.AudioFormat format) {
        // M4A 포맷만 지원하므로 단순화 (WEBM 지원 제거)
        return AudioConversionManager.OutputFormat.M4A;
    }
    
    private AudioConversionManager.AudioQuality convertToAudioConversionQuality(int bitrate) {
        // Native API는 원본 품질을 유지하므로 MEDIUM을 기본값으로 사용
        // 이 값은 로깅 목적으로만 사용되며 실제 변환에는 영향 없음
        return AudioConversionManager.AudioQuality.MEDIUM;
    }
    
    
    /**
     * Fragment가 활성 상태이고 UI 업데이트가 안전한지 확인
     * @return Fragment가 활성 상태이면 true, 아니면 false
     */
    private boolean isFragmentActive() {
        return getActivity() != null && 
               !getActivity().isFinishing() && 
               !getActivity().isDestroyed() && 
               isAdded() && 
               !isDetached() && 
               !isRemoving() && 
               getView() != null;
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // 진행 중인 변환 정리
        if (dialogManager != null) {
            dialogManager.dismissProgressDialog();
        }
        
        // AudioConversionManager 리소스 정리
        if (audioConversionManager != null) {
            audioConversionManager.cleanup();
        }
        
        // 참조 해제
        dialogManager = null;
        toastManager = null;
        audioConversionManager = null;
        binding = null;
        
        com.devc.lab.audios.manager.LoggerManager.logger("ConvertFragment 리소스 정리 완료");
    }
}