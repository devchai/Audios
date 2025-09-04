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
        // 파일 선택 버튼 클릭 리스너
        binding.btnSelectFile.setOnClickListener(v -> selectFile());
        
        // 비디오 파일 선택 버튼 클릭 리스너
        binding.btnSelectVideoFile.setOnClickListener(v -> selectVideoFile());
        
        // 오디오 파일 선택 버튼 클릭 리스너
        binding.btnSelectAudioFile.setOnClickListener(v -> selectAudioFile());
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
            
            // 출력 파일 경로 생성
            String outputPath = generateOutputPath(fileName, settings.getFormat());
            settings.setOutputPath(outputPath);
            
            startConversion(settings);
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
    
    private String generateOutputPath(String fileName, ConversionSettings.AudioFormat format) {
        // 확장자를 제거하고 새 확장자로 대체
        String nameWithoutExtension = fileName.contains(".") ? 
            fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
        return nameWithoutExtension + "_converted." + format.getExtension();
    }
    
    private void startConversion(ConversionSettings settings) {
        toastManager.showToastShort("변환을 시작합니다: " + settings.getFormat());
        
        // 새로운 진행률 다이얼로그 표시
        String fileName = settings.getOutputPath();
        dialogManager.showConversionProgressDialog(fileName, settings);
        
        // TODO: 실제 FFmpeg 변환 로직 구현
        // 현재는 시뮬레이션 진행
        simulateConversionProgress(settings);
        
        com.devc.lab.audios.manager.LoggerManager.logger("Conversion Settings: " + settings.toString());
    }
    
    private void simulateConversionProgress(ConversionSettings settings) {
        new Thread(() -> {
            try {
                for (int progress = 0; progress <= 100; progress += 5) {
                    final int currentProgress = progress;
                    
                    // UI 스레드에서 진행률 업데이트
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            dialogManager.updateConversionProgress(currentProgress);
                            
                            // 예상 시간 업데이트 (시뮬레이션)
                            int remainingSeconds = (100 - currentProgress) / 10;
                            String timeText = String.format("%d:%02d", remainingSeconds / 60, remainingSeconds % 60);
                            dialogManager.updateEstimatedTime(timeText);
                            
                            // 파일 크기 정보 업데이트 (예시)
                            if (currentProgress == 20) {
                                dialogManager.updateFileSizeInfo("45.2 MB", "예상 8.5 MB");
                            }
                        });
                    }
                    
                    Thread.sleep(300); // 시뮬레이션 딜레이
                }
                
                // 변환 완료
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        dialogManager.dismissProgressDialog();
                        toastManager.showToastShort("변환이 완료되었습니다!");
                    });
                }
                
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}