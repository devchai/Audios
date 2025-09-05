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
import com.devc.lab.audios.manager.FFmpegManager;
import com.devc.lab.audios.manager.DialogManager;
import com.devc.lab.audios.model.ConversionSettings;
import com.devc.lab.audios.activity.MainActivity;
import com.devc.lab.audios.model.MainViewModel;

public class ConvertFragment extends Fragment {
    private FragmentConvertBinding binding;
    private FileManager fileManager;
    private ToastManager toastManager;
    private FFmpegManager ffmpegManager;
    private DialogManager dialogManager;
    
    // 현재 변환 설정 저장
    private ConversionSettings currentConversionSettings;
    
    // Activity Result Launchers
    private ActivityResultLauncher<String[]> videoFileLauncher;
    private ActivityResultLauncher<String[]> audioFileLauncher;
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
        ffmpegManager = new FFmpegManager();
        dialogManager = new DialogManager(getContext());
        
        // FFmpegManager 콜백 설정
        setupFFmpegCallbacks();
    }
    
    private void setupFFmpegCallbacks() {
        // 변환 시작 콜백
        ffmpegManager.setOnStartListener(() -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    com.devc.lab.audios.manager.LoggerManager.logger("FFmpeg 변환 시작");
                });
            }
        });
        
        // 진행률 업데이트 콜백
        ffmpegManager.setOnProgressListener(progress -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    dialogManager.updateConversionProgress(progress);
                    com.devc.lab.audios.manager.LoggerManager.logger("변환 진행률: " + progress + "%");
                });
            }
        });
        
        // 변환 완료 콜백
        ffmpegManager.setOnCompletionListener((inputPath, outputPath) -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    dialogManager.dismissProgressDialog();
                    toastManager.showToastShort("변환 완료: " + new java.io.File(outputPath).getName());
                    com.devc.lab.audios.manager.LoggerManager.logger("변환 완료: " + outputPath);
                });
            }
        });
        
        // 변환 실패 콜백
        ffmpegManager.setOnFailureListener((message, reason) -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    dialogManager.dismissProgressDialog();
                    toastManager.showToastShort("변환 실패: " + message);
                    com.devc.lab.audios.manager.LoggerManager.logger("변환 실패: " + message + " - " + reason);
                });
            }
        });
    }
    
    private void setupActivityResultLaunchers() {
        // 비디오 파일 선택 런처
        videoFileLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    handleSelectedFile(uri, "video");
                }
            }
        );
        
        // 오디오 파일 선택 런처
        audioFileLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    handleSelectedFile(uri, "audio");
                }
            }
        );
        
        // 일반 파일 선택 런처
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
        // 메인 파일 선택 버튼 클릭 리스너
        binding.btnSelectFileContainer.setOnClickListener(v -> selectFile());
        
        // 비디오→오디오 변환 옵션 클릭 리스너
        binding.optionVideoToAudio.setOnClickListener(v -> selectVideoFile());
        
        // 오디오 포맷 변환 옵션 클릭 리스너
        binding.optionAudioFormat.setOnClickListener(v -> selectAudioFile());
    }
    
    private void selectFile() {
        // 모든 미디어 파일 타입 선택
        generalFileLauncher.launch(new String[]{"video/*", "audio/*"});
    }
    
    private void selectVideoFile() {
        // 비디오 파일만 선택
        videoFileLauncher.launch(new String[]{"video/*"});
    }
    
    private void selectAudioFile() {
        // 오디오 파일만 선택
        audioFileLauncher.launch(new String[]{"audio/*"});
    }
    
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
            
            // 출력 파일명 생성 (경로는 FFmpegManager에서 자동 생성)
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
        String[] sampleRates = {"22050 Hz", "44100 Hz", "48000 Hz", "96000 Hz"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, sampleRates);
        dialogBinding.spinnerSampleRate.setAdapter(adapter);
        dialogBinding.spinnerSampleRate.setText(sampleRates[1], false); // 44100 Hz 기본 선택
    }
    
    private void setupBitrateSlider(DialogConversionSettingsBinding dialogBinding) {
        dialogBinding.sliderBitrate.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                dialogBinding.tvBitrateValue.setText((int) value + " kbps");
            }
        });
    }
    
    private ConversionSettings getConversionSettingsFromDialog(DialogConversionSettingsBinding dialogBinding) {
        ConversionSettings settings = new ConversionSettings();
        
        // 출력 포맷 설정
        RadioButton selectedFormatButton = dialogBinding.getRoot().findViewById(
                dialogBinding.radioGroupFormat.getCheckedRadioButtonId());
        
        if (selectedFormatButton != null) {
            String formatText = selectedFormatButton.getText().toString();
            switch (formatText) {
                case "MP3":
                    settings.setFormat(ConversionSettings.AudioFormat.MP3);
                    break;
                case "WAV":
                    settings.setFormat(ConversionSettings.AudioFormat.WAV);
                    break;
                case "AAC":
                    settings.setFormat(ConversionSettings.AudioFormat.AAC);
                    break;
                default:
                    settings.setFormat(ConversionSettings.AudioFormat.MP3);
            }
        }
        
        // 비트레이트 설정
        settings.setBitrate((int) dialogBinding.sliderBitrate.getValue());
        
        // 샘플 레이트 설정
        String sampleRateText = dialogBinding.spinnerSampleRate.getText().toString();
        int sampleRate = Integer.parseInt(sampleRateText.split(" ")[0]);
        settings.setSampleRate(sampleRate);
        
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
        
        // FFmpegManager 출력 포맷 변환
        FFmpegManager.OutputFormat outputFormat = convertToFFmpegFormat(settings.getFormat());
        FFmpegManager.AudioQuality audioQuality = convertToFFmpegQuality(settings.getBitrate());
        
        try {
            // 실제 FFmpeg 변환 시작
            ffmpegManager.extractAudioFromVideoUri(
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
    
    private FFmpegManager.OutputFormat convertToFFmpegFormat(ConversionSettings.AudioFormat format) {
        switch (format) {
            case MP3:
                return FFmpegManager.OutputFormat.MP3;
            case WAV:
                return FFmpegManager.OutputFormat.WAV;
            case AAC:
                return FFmpegManager.OutputFormat.AAC;
            default:
                return FFmpegManager.OutputFormat.MP3;
        }
    }
    
    private FFmpegManager.AudioQuality convertToFFmpegQuality(int bitrate) {
        if (bitrate <= 96) {
            return FFmpegManager.AudioQuality.LOW;
        } else if (bitrate <= 128) {
            return FFmpegManager.AudioQuality.MEDIUM;
        } else if (bitrate <= 192) {
            return FFmpegManager.AudioQuality.HIGH;
        } else {
            return FFmpegManager.AudioQuality.VERY_HIGH;
        }
    }
    
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // FFmpegManager 리소스 정리
        if (ffmpegManager != null) {
            ffmpegManager.cleanup();
        }
        
        binding = null;
    }
}