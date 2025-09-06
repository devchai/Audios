package com.devc.lab.audios.fragment;

import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.devc.lab.audios.R;
import com.devc.lab.audios.databinding.FragmentEditBinding;
import com.devc.lab.audios.activity.MainActivity;
import com.devc.lab.audios.manager.*;
import com.devc.lab.audios.model.EditViewModel;
import com.devc.lab.audios.model.MainViewModel;
import com.devc.lab.audios.view.WaveformView;
import androidx.lifecycle.ViewModelProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class EditFragment extends Fragment {
    private FragmentEditBinding binding;
    
    // ViewModel
    private EditViewModel editViewModel;
    
    // Manager 클래스들
    private FileManager fileManager;
    private ToastManager toastManager;
    private FFmpegManager ffmpegManager;
    private DialogManager dialogManager;
    private AudioTrimManager audioTrimManager;
    // LoggerManager는 static 메서드를 사용하므로 인스턴스 변수 불필요
    
    // 미디어 플레이어
    private MediaPlayer mediaPlayer;
    private Handler progressHandler;
    private Runnable progressRunnable;
    
    // 현재 선택된 파일 정보
    private Uri selectedFileUri;
    private int audioDurationMs = 0;
    private boolean isPlaying = false;
    
    // Activity Result Launchers
    private ActivityResultLauncher<String[]> audioFileLauncher;
    
    public static EditFragment newInstance() {
        return new EditFragment();
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActivityResultLaunchers();
    }
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEditBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViewModel();
        initManagers();
        setupUI();
        setupPlayerControls();
        setupWaveform();
        setupTrimControls();
        observeViewModel();
    }
    
    private void initViewModel() {
        editViewModel = new ViewModelProvider(this).get(EditViewModel.class);
    }
    
    private void initManagers() {
        fileManager = new FileManager(getContext());
        toastManager = new ToastManager(getContext());
        ffmpegManager = new FFmpegManager();
        dialogManager = new DialogManager(getContext());
        audioTrimManager = AudioTrimManager.getInstance();
        audioTrimManager.init(getContext());
        // LoggerManager는 static 메서드를 사용하므로 인스턴스 불필요
        
        progressHandler = new Handler(Looper.getMainLooper());
        
        // FFmpegManager 콜백 설정
        setupFFmpegCallbacks();
    }
    
    private void setupFFmpegCallbacks() {
        ffmpegManager.setOnStartListener(() -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    LoggerManager.logger("FFmpeg 편집 시작");
                });
            }
        });
        
        ffmpegManager.setOnProgressListener(progress -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    dialogManager.updateProgress(progress);
                    LoggerManager.logger("편집 진행률: " + progress + "%");
                });
            }
        });
        
        ffmpegManager.setOnCompletionListener((inputPath, outputPath) -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    dialogManager.dismissProgressDialog();
                    toastManager.showToastLong("편집이 완료되었습니다!");
                    LoggerManager.logger("편집 완료: " + outputPath);
                });
            }
        });
        
//         ffmpegManager.setOnErrorListener(error -> {
//             if (getActivity() != null) {
//                 getActivity().runOnUiThread(() -> {
//                     dialogManager.dismissProgressDialog();
//                     toastManager.showToastLong("편집 중 오류가 발생했습니다: " + error);
//                     LoggerManager.logger("편집 오류: " + error);
//                 });
//             }
//         });
    }
    
    private void setupActivityResultLaunchers() {
        audioFileLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    handleSelectedFile(uri);
                }
            }
        );
    }
    
    private void setupUI() {
        // 파일 선택 버튼 클릭 리스너
        binding.btnSelectFileToEdit.setOnClickListener(v -> selectFileToEdit());
        
        // 편집 도구 버튼들 클릭 리스너
        binding.btnTrim.setOnClickListener(v -> trimAudio());
        // 임시 주석 처리된 버튼들의 리스너
        // binding.btnMerge.setOnClickListener(v -> mergeAudio());
        // binding.btnVolume.setOnClickListener(v -> adjustVolume());
        // binding.btnEffects.setOnClickListener(v -> applyEffects());
        
        // 저장/취소 버튼 클릭 리스너
        binding.btnSaveEdited.setOnClickListener(v -> saveEditedFile());
        binding.btnCancelEdit.setOnClickListener(v -> cancelEdit());
        
        // 편집 도구들 초기에는 비활성화
        setEditToolsEnabled(false);
    }
    
    private void setupPlayerControls() {
        // 재생/일시정지 버튼
        binding.btnPlayPause.setOnClickListener(v -> togglePlayback());
        
        // 진행률 슬라이더
        binding.sliderProgress.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                }
            }
            
            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                if (mediaPlayer != null) {
                    int newPosition = (int) (slider.getValue() / 100.0f * audioDurationMs);
                    mediaPlayer.seekTo(newPosition);
                    if (isPlaying) {
                        mediaPlayer.start();
                    }
                }
            }
        });
    }
    
    private void selectFileToEdit() {
        try {
            String[] mimeTypes = {
                "audio/*",
                "audio/mp3",
                "audio/wav",
                "audio/aac",
                "audio/m4a",
                "audio/ogg",
                "audio/flac"
            };
            
            audioFileLauncher.launch(mimeTypes);
            LoggerManager.logger("오디오 파일 선택 다이얼로그 실행");
            
        } catch (Exception e) {
            toastManager.showToastLong("파일 선택 중 오류가 발생했습니다: " + e.getMessage());
            LoggerManager.logger("파일 선택 오류: " + e.getMessage());
        }
    }
    
    private void handleSelectedFile(Uri uri) {
        try {
            selectedFileUri = uri;
            
            // Scoped Storage 호환: URI를 직접 사용 (경로 변환 불필요)
            String fileName = fileManager.getFileName(getContext(), uri);
            
            if (fileName != null && !fileName.isEmpty()) {
                // ViewModel에 파일 정보 전달
                editViewModel.selectFile(uri, fileName);
                
                // URI에서 직접 메타데이터 추출
                extractAudioMetadataFromUri(uri);
                
                // UI 업데이트 (파일명 표시)
                updateUIForSelectedFile(fileName);
                
                LoggerManager.logger("파일 선택됨: " + fileName + " (URI: " + uri.toString() + ")");
                toastManager.showToastShort("파일이 선택되었습니다: " + fileName);
            } else {
                toastManager.showToastLong("선택한 파일 정보를 가져올 수 없습니다");
            }
        } catch (Exception e) {
            toastManager.showToastLong("파일 처리 중 오류가 발생했습니다: " + e.getMessage());
            LoggerManager.logger("파일 처리 오류: " + e.getMessage());
        }
    }
    
    /**
     * URI에서 직접 오디오 메타데이터 추출 (Scoped Storage 호환)
     */
    private void extractAudioMetadataFromUri(Uri uri) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(getContext(), uri);
            
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration != null) {
                audioDurationMs = Integer.parseInt(duration);
                String formattedDuration = formatTime(audioDurationMs);
                binding.tvTotalTime.setText(formattedDuration);
                
                // ViewModel에 오디오 길이 전달
                editViewModel.setAudioDuration(audioDurationMs);
                
                LoggerManager.logger("오디오 길이 추출됨: " + audioDurationMs + "ms (" + formattedDuration + ")");
            } else {
                LoggerManager.logger("오디오 길이 메타데이터를 찾을 수 없음");
                audioDurationMs = 0;
                binding.tvTotalTime.setText("00:00");
                editViewModel.setAudioDuration(0);
            }
            
            retriever.release();
            
        } catch (Exception e) {
            LoggerManager.logger("URI 메타데이터 추출 오류: " + e.getMessage());
            audioDurationMs = 0;
            binding.tvTotalTime.setText("00:00");
        }
    }
    
    /**
     * 레거시 메서드 유지 (다른 부분에서 사용할 수도 있음)
     */
    private void extractAudioMetadata(String filePath) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(filePath);
            
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration != null) {
                audioDurationMs = Integer.parseInt(duration);
                String formattedDuration = formatTime(audioDurationMs);
                binding.tvTotalTime.setText(formattedDuration);
            }
            
            retriever.release();
            
        } catch (Exception e) {
            LoggerManager.logger("메타데이터 추출 오류: " + e.getMessage());
            audioDurationMs = 0;
            binding.tvTotalTime.setText("00:00");
        }
    }
    
    private void updateUIForSelectedFile(String fileName) {
        // 파형 placeholder 숨기기
        binding.layoutWaveformPlaceholder.setVisibility(View.GONE);
        binding.waveformView.setVisibility(View.VISIBLE);
        
        // 파일명 표시
        binding.btnSelectFileToEdit.setText(fileName);
        
        LoggerManager.logger("UI 업데이트 완료 - 파일명: " + fileName);
    }
    
    private void setEditToolsEnabled(boolean enabled) {
        binding.btnTrim.setEnabled(enabled);
        // 임시 주석 처리된 버튼들 비활성화/활성화 제외
        // binding.btnMerge.setEnabled(enabled);
        // binding.btnVolume.setEnabled(enabled);
        // binding.btnEffects.setEnabled(enabled);
        binding.btnSaveEdited.setEnabled(enabled);
        
        binding.btnPlayPause.setEnabled(enabled);
        binding.sliderProgress.setEnabled(enabled);
    }
    
    private void setupWaveform() {
        // 웨이브폼 뷰의 자르기 위치 변경 리스너 설정
        binding.waveformView.setTrimListener(new WaveformView.OnTrimPositionChangeListener() {
            @Override
            public void onTrimStartChanged(float position) {
                editViewModel.setTrimStartPosition(position);
            }
            
            @Override
            public void onTrimEndChanged(float position) {
                editViewModel.setTrimEndPosition(position);
            }
            
            @Override
            public void onTrimPositionChanged(float start, float end) {
                editViewModel.setTrimStartPosition(start);
                editViewModel.setTrimEndPosition(end);
            }
        });
    }
    
    private void setupTrimControls() {
        // 자르기 적용 버튼
        binding.btnApplyTrim.setOnClickListener(v -> {
            editViewModel.performTrim();
        });
    }
    
    private void observeViewModel() {
        // 파일 선택 상태 관찰
        editViewModel.getSelectedFileUri().observe(getViewLifecycleOwner(), uri -> {
            if (uri != null) {
                setEditToolsEnabled(true);
            } else {
                setEditToolsEnabled(false);
                resetUI();
            }
        });
        
        // 자르기 시작 위치 관찰
        editViewModel.getTrimStartPosition().observe(getViewLifecycleOwner(), position -> {
            if (position != null) {
                binding.waveformView.setTrimStartPosition(position);
                updateTrimTimeDisplay();
            }
        });
        
        // 자르기 끝 위치 관찰
        editViewModel.getTrimEndPosition().observe(getViewLifecycleOwner(), position -> {
            if (position != null) {
                binding.waveformView.setTrimEndPosition(position);
                updateTrimTimeDisplay();
            }
        });
        
        // 처리 상태 관찰
        editViewModel.getIsProcessing().observe(getViewLifecycleOwner(), isProcessing -> {
            if (isProcessing != null) {
                setEditToolsEnabled(!isProcessing);
                if (isProcessing) {
                    dialogManager.showProgressDialog();
                } else {
                    dialogManager.dismissProgressDialog();
                }
            }
        });
        
        // 처리 진행률 관찰
        editViewModel.getProcessingProgress().observe(getViewLifecycleOwner(), progress -> {
            if (progress != null) {
                dialogManager.updateProgress(progress);
            }
        });
        
        // 상태 메시지 관찰
        editViewModel.getStatusMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                toastManager.showToastShort(message);
            }
        });
        
        // 재생 위치 관찰
        editViewModel.getPlaybackPosition().observe(getViewLifecycleOwner(), position -> {
            if (position != null) {
                binding.waveformView.setPlayheadPosition(position);
            }
        });
    }
    
    private void updateTrimTimeDisplay() {
        binding.tvTrimTimeRange.setText(editViewModel.getTrimTimeText());
        binding.tvTrimDuration.setText(editViewModel.getTrimDurationText());
    }
    
    private void trimAudio() {
        if (selectedFileUri == null) {
            toastManager.showToastShort("먼저 파일을 선택해주세요");
            return;
        }
        
        // ViewModel을 통해 자르기 실행
        editViewModel.performTrim();
    }
    
    private void mergeAudio() {
        if (selectedFileUri == null) {
            toastManager.showToastShort("먼저 파일을 선택해주세요");
            return;
        }
        
        // 합치기 다이얼로그 표시
        showMergeDialog();
    }
    
    private void showMergeDialog() {
        if (getContext() == null) return;
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        
        builder.setTitle("오디오 합치기")
               .setMessage("합치기 기능은 개발 중입니다.\n다른 오디오 파일과 합치는 기능이\n향후 업데이트에서 제공될 예정입니다.")
               .setPositiveButton("확인", (dialog, which) -> dialog.dismiss())
               .show();
        
        LoggerManager.logger("오디오 합치기 다이얼로그 표시");
    }
    
    private void adjustVolume() {
        if (selectedFileUri == null) {
            toastManager.showToastShort("먼저 파일을 선택해주세요");
            return;
        }
        
        // 볼륨 조절 다이얼로그 표시
        showVolumeDialog();
    }
    
    private void showVolumeDialog() {
        if (getContext() == null) return;
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        
        builder.setTitle("볼륨 조절")
               .setMessage("볼륨 조절 기능은 개발 중입니다.\n오디오 볼륨을 조절하는 기능이\n향후 업데이트에서 제공될 예정입니다.")
               .setPositiveButton("확인", (dialog, which) -> dialog.dismiss())
               .show();
        
        LoggerManager.logger("볼륨 조절 다이얼로그 표시");
    }
    
    private void applyEffects() {
        if (selectedFileUri == null) {
            toastManager.showToastShort("먼저 파일을 선택해주세요");
            return;
        }
        
        // 효과 다이얼로그 표시
        showEffectsDialog();
    }
    
    private void showEffectsDialog() {
        if (getContext() == null) return;
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        
        String[] effects = {"리버브", "에코", "노이즈 제거", "이퀄라이저"};
        
        builder.setTitle("오디오 효과")
               .setItems(effects, (dialog, which) -> {
                   String selectedEffect = effects[which];
                   toastManager.showToastShort(selectedEffect + " 효과는 개발 중입니다");
                   LoggerManager.logger(selectedEffect + " 효과 선택됨");
               })
               .setNegativeButton("취소", (dialog, which) -> dialog.dismiss())
               .show();
        
        LoggerManager.logger("오디오 효과 다이얼로그 표시");
    }
    
    private void saveEditedFile() {
        if (selectedFileUri == null) {
            toastManager.showToastShort("먼저 파일을 선택해주세요");
            return;
        }
        
        // 저장 확인 다이얼로그
        showSaveDialog();
    }
    
    private void showSaveDialog() {
        if (getContext() == null) return;
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        
        builder.setTitle("편집 파일 저장")
               .setMessage("편집된 오디오 파일을 저장하시겠습니까?")
               .setPositiveButton("저장", (dialog, which) -> {
                   performSave();
               })
               .setNegativeButton("취소", (dialog, which) -> dialog.dismiss())
               .show();
    }
    
    private void performSave() {
        try {
            if (selectedFileUri == null) {
                toastManager.showToastLong("선택된 파일이 없습니다");
                return;
            }
            
            // URI에서 파일명 추출
            String originalFileName = fileManager.getFileName(getContext(), selectedFileUri);
            if (originalFileName == null || originalFileName.isEmpty()) {
                originalFileName = "edited_audio.mp3";
            }
            
            // 편집된 파일 경로 생성
            File editedDir = fileManager.getEditedDirectory(getContext());
            if (!editedDir.exists()) {
                editedDir.mkdirs();
            }
            
            String baseName = originalFileName.contains(".") ? 
                originalFileName.substring(0, originalFileName.lastIndexOf(".")) : originalFileName;
            String extension = originalFileName.contains(".") ? 
                originalFileName.substring(originalFileName.lastIndexOf(".")) : ".mp3";
                
            String outputFileName = "edited_" + baseName + "_" + System.currentTimeMillis() + extension;
            File outputFile = new File(editedDir, outputFileName);
            
            // URI에서 파일 복사 (Scoped Storage 호환)
            boolean success = copyUriToFile(selectedFileUri, outputFile);
            
            if (success) {
                toastManager.showToastLong("편집된 파일이 저장되었습니다: " + outputFile.getName());
                LoggerManager.logger("파일 저장 완료: " + outputFile.getAbsolutePath());
            } else {
                toastManager.showToastLong("파일 저장에 실패했습니다");
            }
            
        } catch (Exception e) {
            toastManager.showToastLong("저장 중 오류가 발생했습니다: " + e.getMessage());
            LoggerManager.logger("저장 오류: " + e.getMessage());
        }
    }
    
    /**
     * URI에서 파일로 데이터 복사 (Scoped Storage 호환)
     */
    private boolean copyUriToFile(Uri sourceUri, File destinationFile) {
        try (InputStream inputStream = getContext().getContentResolver().openInputStream(sourceUri);
             FileOutputStream outputStream = new FileOutputStream(destinationFile)) {
            
            if (inputStream == null) {
                LoggerManager.logger("URI에서 InputStream을 열 수 없음");
                return false;
            }
            
            byte[] buffer = new byte[8 * 1024]; // 8KB 버퍼
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            
            outputStream.flush();
            LoggerManager.logger("URI 파일 복사 완료: " + totalBytes + " bytes");
            return true;
            
        } catch (IOException e) {
            LoggerManager.logger("URI 파일 복사 실패: " + e.getMessage());
            return false;
        }
    }
    
    private void cancelEdit() {
        // 재생 중인 미디어 정지
        stopPlayback();
        
        // 선택된 파일 정보 초기화
        selectedFileUri = null;
        audioDurationMs = 0;
        isPlaying = false;
        
        // ViewModel 초기화
        editViewModel.reset();
        
        // UI 초기화
        resetUI();
        
        LoggerManager.logger("편집이 취소되었습니다");
        toastManager.showToastShort("편집이 취소되었습니다");
    }
    
    private void resetUI() {
        // 파형 UI 초기화
        binding.layoutWaveformPlaceholder.setVisibility(View.VISIBLE);
        binding.waveformView.setVisibility(View.GONE);
        
        // 플레이어 컨트롤 초기화
        binding.btnPlayPause.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_play, 0, 0);
        binding.sliderProgress.setValue(0);
        binding.tvCurrentTime.setText("00:00");
        binding.tvTotalTime.setText("00:00");
        
        // 파일 선택 버튼 텍스트 복원
        binding.btnSelectFileToEdit.setText(R.string.select_file_to_edit);
        
        // 편집 도구들 비활성화
        setEditToolsEnabled(false);
    }
    
    // 플레이어 관련 메서드들
    private void togglePlayback() {
        if (selectedFileUri == null) {
            toastManager.showToastShort("먼저 파일을 선택해주세요");
            return;
        }
        
        if (isPlaying) {
            pausePlayback();
        } else {
            startPlayback();
        }
    }
    
    private void startPlayback() {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                // Scoped Storage 호환: URI를 직접 사용
                mediaPlayer.setDataSource(getContext(), selectedFileUri);
                mediaPlayer.prepare();
                
                mediaPlayer.setOnCompletionListener(mp -> {
                    pausePlayback();
                    binding.sliderProgress.setValue(0);
                    binding.tvCurrentTime.setText("00:00");
                });
            }
            
            mediaPlayer.start();
            isPlaying = true;
            
            // 재생 버튼 아이콘 변경
            binding.btnPlayPause.setIcon(getResources().getDrawable(R.drawable.ic_pause, null));
            
            // 진행률 업데이트 시작
            startProgressUpdate();
            
            LoggerManager.logger("오디오 재생 시작 (URI: " + selectedFileUri.toString() + ")");
            
        } catch (IOException e) {
            toastManager.showToastShort("재생 중 오류가 발생했습니다: " + e.getMessage());
            LoggerManager.logger("재생 오류: " + e.getMessage());
        } catch (Exception e) {
            toastManager.showToastShort("재생 중 오류가 발생했습니다: " + e.getMessage());
            LoggerManager.logger("재생 오류: " + e.getMessage());
        }
    }
    
    private void pausePlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        
        isPlaying = false;
        
        // 일시정지 버튼 아이콘 변경
        binding.btnPlayPause.setIcon(getResources().getDrawable(R.drawable.ic_play, null));
        
        // 진행률 업데이트 중지
        stopProgressUpdate();
        
        LoggerManager.logger("오디오 재생 일시정지");
    }
    
    private void stopPlayback() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        
        isPlaying = false;
        stopProgressUpdate();
        
        // 재생 버튼 아이콘 복원
        binding.btnPlayPause.setIcon(getResources().getDrawable(R.drawable.ic_play, null));
    }
    
    private void startProgressUpdate() {
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && isPlaying) {
                    int currentPosition = mediaPlayer.getCurrentPosition();
                    float progress = (float) currentPosition / audioDurationMs * 100;
                    float playbackPosition = (float) currentPosition / audioDurationMs;
                    
                    binding.sliderProgress.setValue(progress);
                    binding.tvCurrentTime.setText(formatTime(currentPosition));
                    
                    // ViewModel에 재생 위치 업데이트
                    editViewModel.setPlaybackPosition(playbackPosition);
                    
                    progressHandler.postDelayed(this, 1000); // 1초마다 업데이트
                }
            }
        };
        progressHandler.post(progressRunnable);
    }
    
    private void stopProgressUpdate() {
        if (progressHandler != null && progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
    }
    
    private String formatTime(long timeMs) {
        int totalSeconds = (int) (timeMs / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // 미디어 플레이어 정리
        stopPlayback();
        
        // 핸들러 정리
        stopProgressUpdate();
        
        binding = null;
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // 앱이 백그라운드로 갈 때 재생 일시정지
        if (isPlaying) {
            pausePlayback();
        }
    }
}