package com.devc.lab.audios.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.media.MediaMetadataRetriever;
import com.devc.lab.audios.databinding.FragmentLibraryBinding;
import com.devc.lab.audios.adapter.AudioFileAdapter;
import com.devc.lab.audios.model.AudioFile;
import com.devc.lab.audios.manager.FileManager;
import com.devc.lab.audios.manager.ToastManager;
import com.devc.lab.audios.manager.AudioPlayerManager;
import com.google.android.material.tabs.TabLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class LibraryFragment extends Fragment implements AudioFileAdapter.OnItemClickListener, AudioFileAdapter.OnItemLongClickListener {
    private FragmentLibraryBinding binding;
    private AudioFileAdapter adapter;
    private FileManager fileManager;
    private ToastManager toastManager;
    private AudioPlayerManager audioPlayerManager;
    
    // 탭 타입 상수
    private static final int TAB_CONVERTED = 0;
    private static final int TAB_EDITED = 1;
    
    // 현재 선택된 탭
    private int currentTab = TAB_CONVERTED;
    
    public static LibraryFragment newInstance() {
        return new LibraryFragment();
    }
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupUI();
        loadFiles();
    }
    
    private void setupUI() {
        // Manager 초기화
        fileManager = new FileManager((androidx.appcompat.app.AppCompatActivity) getActivity());
        toastManager = new ToastManager(getContext());
        audioPlayerManager = new AudioPlayerManager(getContext());
        
        // 어댑터 설정
        adapter = new AudioFileAdapter(getContext());
        adapter.setOnItemClickListener(this);
        adapter.setOnItemLongClickListener(this);
        
        // RecyclerView 설정
        binding.recyclerViewFiles.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewFiles.setAdapter(adapter);
        
        // 탭 레이아웃 설정
        setupTabs();
        
        // FAB 클릭 리스너 설정
        binding.fabSort.setOnClickListener(v -> showSortOptions());
        
        // 새로고침 리스너
        binding.swipeRefreshLayout.setOnRefreshListener(this::loadFiles);
    }
    
    private void setupTabs() {
        // 탭 선택 리스너
        binding.tabLayoutFileTypes.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                loadFiles();
            }
            
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // 구현 필요 없음
            }
            
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // 구현 필요 없음
            }
        });
        
        // 초기 탭 선택
        TabLayout.Tab defaultTab = binding.tabLayoutFileTypes.getTabAt(TAB_CONVERTED);
        if (defaultTab != null) {
            defaultTab.select();
        }
    }
    
    private void loadFiles() {
        // 로딩 상태 표시
        showLoadingState(true);
        
        // 백그라운드에서 파일 스캔 실행
        new Thread(() -> {
            try {
                List<AudioFile> audioFiles = scanAudioFiles();
                
                // UI 스레드에서 결과 업데이트
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        adapter.setAudioFiles(audioFiles);
                        updateEmptyState(audioFiles.isEmpty());
                        showLoadingState(false);
                        binding.swipeRefreshLayout.setRefreshing(false);
                    });
                }
                
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        toastManager.showToastShort("파일 로드 실패: " + e.getMessage());
                        showLoadingState(false);
                        binding.swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }
        }).start();
    }
    
    private List<AudioFile> scanAudioFiles() {
        List<AudioFile> audioFiles = new ArrayList<>();
        
        try {
            // 선택된 탭에 따라 디렉토리 결정
            File directory;
            if (currentTab == TAB_CONVERTED) {
                directory = fileManager.getConvertedDirectory(getContext());
            } else {
                directory = fileManager.getEditedDirectory(getContext());
            }
            
            // 디렉토리가 존재하지 않으면 빈 리스트 반환
            if (!directory.exists()) {
                return audioFiles;
            }
            
            // 파일 목록 가져오기
            List<File> files = fileManager.getFilesInDirectory(directory);
            
            // 오디오 파일만 필터링하고 AudioFile 객체로 변환
            for (File file : files) {
                if (isAudioFile(file)) {
                    AudioFile audioFile = convertFileToAudioFile(file);
                    if (audioFile != null) {
                        audioFiles.add(audioFile);
                    }
                }
            }
            
            // 파일을 수정 날짜 순으로 정렬 (최신 순)
            Collections.sort(audioFiles, new Comparator<AudioFile>() {
                @Override
                public int compare(AudioFile a1, AudioFile a2) {
                    if (a1.getModifiedDate() != null && a2.getModifiedDate() != null) {
                        return a2.getModifiedDate().compareTo(a1.getModifiedDate());
                    }
                    return 0;
                }
            });
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return audioFiles;
    }
    
    private boolean isAudioFile(File file) {
        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(".mp3") || 
               fileName.endsWith(".wav") || 
               fileName.endsWith(".aac") || 
               fileName.endsWith(".flac") || 
               fileName.endsWith(".ogg") || 
               fileName.endsWith(".m4a");
    }
    
    private AudioFile convertFileToAudioFile(File file) {
        try {
            AudioFile audioFile = new AudioFile(file.getName(), file.getAbsolutePath());
            
            // 파일 크기 설정
            audioFile.setFileSize(file.length());
            
            // 수정 날짜 설정
            audioFile.setModifiedDate(new Date(file.lastModified()));
            
            // 파일 확장자에서 포맷 추출
            String extension = getFileExtension(file.getName());
            audioFile.setFormat(extension.toUpperCase());
            
            // MediaMetadataRetriever를 사용해서 실제 음악 파일의 메타데이터 추출
            extractAudioMetadata(audioFile, file);
            
            return audioFile;
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
    
    /**
     * MediaMetadataRetriever를 사용해서 오디오 파일의 메타데이터 추출
     */
    private void extractAudioMetadata(AudioFile audioFile, File file) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(file.getAbsolutePath());
            
            // Duration 추출 (밀리초 단위)
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null && !durationStr.isEmpty()) {
                try {
                    long duration = Long.parseLong(durationStr);
                    audioFile.setDuration(duration);
                } catch (NumberFormatException e) {
                    audioFile.setDuration(0); // 파싱 실패 시 기본값
                }
            } else {
                audioFile.setDuration(0);
            }
            
            // Bitrate 추출
            String bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            if (bitrateStr != null && !bitrateStr.isEmpty()) {
                try {
                    int bitrate = Integer.parseInt(bitrateStr) / 1000; // bps를 kbps로 변환
                    audioFile.setBitrate(bitrate);
                } catch (NumberFormatException e) {
                    audioFile.setBitrate(128); // 파싱 실패 시 기본값
                }
            } else {
                audioFile.setBitrate(128); // 기본값
            }
            
            // Sample Rate 추출
            String sampleRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE);
            if (sampleRateStr != null && !sampleRateStr.isEmpty()) {
                try {
                    int sampleRate = Integer.parseInt(sampleRateStr);
                    audioFile.setSampleRate(sampleRate);
                } catch (NumberFormatException e) {
                    audioFile.setSampleRate(44100); // 파싱 실패 시 기본값
                }
            } else {
                audioFile.setSampleRate(44100); // 기본값
            }
            
        } catch (Exception e) {
            // MediaMetadataRetriever 예외 처리
            // 지원하지 않는 포맷이거나 손상된 파일의 경우 기본값 사용
            audioFile.setDuration(0);
            audioFile.setBitrate(128);
            audioFile.setSampleRate(44100);
            
            // 로그 기록
            if (getActivity() != null) {
                android.util.Log.w("LibraryFragment", "메타데이터 추출 실패: " + file.getName() + " - " + e.getMessage());
            }
        } finally {
            // 리소스 해제
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    android.util.Log.e("LibraryFragment", "MediaMetadataRetriever 해제 실패: " + e.getMessage());
                }
            }
        }
    }
    
    private void showLoadingState(boolean show) {
        if (show) {
            binding.layoutLoadingState.setVisibility(View.VISIBLE);
            binding.recyclerViewFiles.setVisibility(View.GONE);
            binding.layoutEmptyState.setVisibility(View.GONE);
        } else {
            binding.layoutLoadingState.setVisibility(View.GONE);
        }
    }
    
    
    private void updateEmptyState(boolean isEmpty) {
        if (isEmpty) {
            binding.recyclerViewFiles.setVisibility(View.GONE);
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            binding.layoutLoadingState.setVisibility(View.GONE);
        } else {
            binding.recyclerViewFiles.setVisibility(View.VISIBLE);
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.layoutLoadingState.setVisibility(View.GONE);
        }
    }
    
    private void showSortOptions() {
        String[] sortOptions = {"이름순 (A-Z)", "날짜순 (최신순)", "크기순 (큰 순)"};
        
        androidx.appcompat.app.AlertDialog.Builder builder = 
                new androidx.appcompat.app.AlertDialog.Builder(getContext());
        builder.setTitle("정렬 방식 선택");
        builder.setItems(sortOptions, (dialog, which) -> {
            sortFiles(which);
        });
        builder.show();
    }
    
    private void sortFiles(int sortType) {
        List<AudioFile> currentFiles = new ArrayList<>();
        
        // 현재 어댑터의 파일 목록 복사
        for (int i = 0; i < adapter.getItemCount(); i++) {
            AudioFile file = adapter.getAudioFile(i);
            if (file != null) {
                currentFiles.add(file);
            }
        }
        
        // 선택된 정렬 방식에 따라 정렬
        switch (sortType) {
            case 0: // 이름순 (A-Z)
                Collections.sort(currentFiles, new Comparator<AudioFile>() {
                    @Override
                    public int compare(AudioFile a1, AudioFile a2) {
                        return a1.getDisplayName().compareToIgnoreCase(a2.getDisplayName());
                    }
                });
                toastManager.showToastShort("이름순으로 정렬되었습니다");
                break;
                
            case 1: // 날짜순 (최신순)
                Collections.sort(currentFiles, new Comparator<AudioFile>() {
                    @Override
                    public int compare(AudioFile a1, AudioFile a2) {
                        if (a1.getModifiedDate() != null && a2.getModifiedDate() != null) {
                            return a2.getModifiedDate().compareTo(a1.getModifiedDate()); // 최신순
                        }
                        return 0;
                    }
                });
                toastManager.showToastShort("날짜순으로 정렬되었습니다");
                break;
                
            case 2: // 크기순 (큰 순)
                Collections.sort(currentFiles, new Comparator<AudioFile>() {
                    @Override
                    public int compare(AudioFile a1, AudioFile a2) {
                        return Long.compare(a2.getFileSize(), a1.getFileSize()); // 큰 순
                    }
                });
                toastManager.showToastShort("크기순으로 정렬되었습니다");
                break;
        }
        
        // 정렬된 목록을 어댑터에 설정
        adapter.setAudioFiles(currentFiles);
    }
    
    // AudioFileAdapter.OnItemClickListener 구현
    @Override
    public void onItemClick(AudioFile audioFile, int position) {
        toastManager.showToastShort("파일 선택: " + audioFile.getDisplayName());
        // TODO: 파일 상세 정보 표시 또는 재생
    }
    
    @Override
    public void onPlayClick(AudioFile audioFile, int position) {
        if (audioPlayerManager == null) {
            toastManager.showToastShort("플레이어 초기화 오류");
            return;
        }
        
        try {
            String filePath = audioFile.getFilePath();
            File audioFileOnDisk = new File(filePath);
            
            // 파일 존재 여부 확인
            if (!audioFileOnDisk.exists()) {
                toastManager.showToastShort("파일을 찾을 수 없습니다: " + audioFile.getDisplayName());
                return;
            }
            
            // 현재 재생 중인 파일과 동일한지 확인
            String currentPath = audioPlayerManager.getCurrentFilePath();
            
            if (filePath.equals(currentPath) && audioPlayerManager.isCurrentlyPlaying()) {
                // 같은 파일이고 재생 중이면 일시정지
                audioPlayerManager.pause();
                adapter.updatePlaybackState(filePath, false);
                toastManager.showToastShort("일시정지: " + audioFile.getDisplayName());
            } else if (filePath.equals(currentPath) && !audioPlayerManager.isCurrentlyPlaying()) {
                // 같은 파일이고 일시정지 상태면 다시 재생
                audioPlayerManager.play();
                adapter.updatePlaybackState(filePath, true);
                toastManager.showToastShort("재생 재개: " + audioFile.getDisplayName());
            } else {
                // 새로운 파일 재생
                audioPlayerManager.loadAudio(filePath);
                
                // 로드 완료 후 자동 재생을 위한 리스너 설정
                audioPlayerManager.setOnPlayerStateChangeListener(new AudioPlayerManager.OnPlayerStateChangeListener() {
                    @Override
                    public void onStateChanged(AudioPlayerManager.PlaybackState newState) {
                        if (newState == AudioPlayerManager.PlaybackState.PREPARED) {
                            audioPlayerManager.play();
                            adapter.updatePlaybackState(filePath, true);
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    toastManager.showToastShort("재생 시작: " + audioFile.getDisplayName());
                                });
                            }
                        } else if (newState == AudioPlayerManager.PlaybackState.STOPPED) {
                            adapter.updatePlaybackState(filePath, false);
                        }
                    }
                    
                    @Override
                    public void onError(String error) {
                        adapter.updatePlaybackState(filePath, false);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                toastManager.showToastShort("재생 오류: " + error);
                            });
                        }
                    }
                });
                
                toastManager.showToastShort("로딩 중: " + audioFile.getDisplayName());
            }
            
        } catch (Exception e) {
            toastManager.showToastShort("재생 실패: " + e.getMessage());
        }
    }
    
    @Override
    public void onMoreClick(AudioFile audioFile, int position) {
        // TODO: 더보기 메뉴 (공유, 삭제, 이름 변경 등)
        showFileOptionsMenu(audioFile, position);
    }
    
    // AudioFileAdapter.OnItemLongClickListener 구현
    @Override
    public void onItemLongClick(AudioFile audioFile, int position) {
        // 디버깅 로그 추가
        android.util.Log.d("LibraryFragment", "onItemLongClick 호출됨: " + audioFile.getDisplayName() + " at position: " + position);
        
        // 길게 눌렀을 때 컨텍스트 메뉴 또는 선택 모드
        showFileOptionsMenu(audioFile, position);
    }
    
    private void showFileOptionsMenu(AudioFile audioFile, int position) {
        String[] options = {"재생", "공유", "이름 변경", "삭제"};
        
        androidx.appcompat.app.AlertDialog.Builder builder = 
                new androidx.appcompat.app.AlertDialog.Builder(getContext());
        builder.setTitle(audioFile.getDisplayName());
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // 재생
                    onPlayClick(audioFile, position);
                    break;
                case 1: // 공유
                    shareFile(audioFile);
                    break;
                case 2: // 이름 변경
                    showRenameDialog(audioFile, position);
                    break;
                case 3: // 삭제
                    showDeleteConfirmDialog(audioFile, position);
                    break;
            }
        });
        builder.show();
    }
    
    private void shareFile(AudioFile audioFile) {
        try {
            File file = new File(audioFile.getFilePath());
            if (!file.exists()) {
                toastManager.showToastShort("파일이 존재하지 않습니다");
                return;
            }
            
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("audio/*");
            
            // FileProvider를 사용해서 파일 공유 (API 24+에서 필수)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    getContext(),
                    getContext().getPackageName() + ".fileprovider",
                    file
                );
                intent.putExtra(android.content.Intent.EXTRA_STREAM, fileUri);
                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                intent.putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.fromFile(file));
            }
            
            startActivity(android.content.Intent.createChooser(intent, "파일 공유"));
            
        } catch (Exception e) {
            toastManager.showToastShort("공유 실패: " + e.getMessage());
        }
    }
    
    private void showRenameDialog(AudioFile audioFile, int position) {
        android.widget.EditText editText = new android.widget.EditText(getContext());
        editText.setText(removeFileExtension(audioFile.getDisplayName()));
        editText.setSelection(editText.getText().length());
        
        androidx.appcompat.app.AlertDialog.Builder builder = 
                new androidx.appcompat.app.AlertDialog.Builder(getContext());
        builder.setTitle("파일 이름 변경");
        builder.setView(editText);
        builder.setPositiveButton("변경", (dialog, which) -> {
            String newName = editText.getText().toString().trim();
            if (!newName.isEmpty()) {
                renameFile(audioFile, newName, position);
            }
        });
        builder.setNegativeButton("취소", null);
        builder.show();
    }
    
    private void showDeleteConfirmDialog(AudioFile audioFile, int position) {
        androidx.appcompat.app.AlertDialog.Builder builder = 
                new androidx.appcompat.app.AlertDialog.Builder(getContext());
        builder.setTitle("파일 삭제");
        builder.setMessage("'" + audioFile.getDisplayName() + "' 파일을 삭제하시겠습니까?");
        builder.setPositiveButton("삭제", (dialog, which) -> {
            deleteFile(audioFile, position);
        });
        builder.setNegativeButton("취소", null);
        builder.show();
    }
    
    private void renameFile(AudioFile audioFile, String newName, int position) {
        try {
            File oldFile = new File(audioFile.getFilePath());
            String extension = getFileExtension(audioFile.getFileName());
            String newFileName = newName + "." + extension;
            File newFile = new File(oldFile.getParent(), newFileName);
            
            if (newFile.exists()) {
                toastManager.showToastShort("같은 이름의 파일이 이미 존재합니다");
                return;
            }
            
            boolean renamed = oldFile.renameTo(newFile);
            if (renamed) {
                // AudioFile 객체 업데이트
                audioFile.setFileName(newFileName);
                audioFile.setFilePath(newFile.getAbsolutePath());
                audioFile.setDisplayName(newFileName);
                
                // 어댑터에 변경 알림
                adapter.notifyItemChanged(position);
                toastManager.showToastShort("파일 이름이 변경되었습니다");
            } else {
                toastManager.showToastShort("파일 이름 변경에 실패했습니다");
            }
            
        } catch (Exception e) {
            toastManager.showToastShort("이름 변경 실패: " + e.getMessage());
        }
    }
    
    private void deleteFile(AudioFile audioFile, int position) {
        try {
            File file = new File(audioFile.getFilePath());
            boolean deleted = file.delete();
            
            if (deleted) {
                // 어댑터에서 아이템 제거
                adapter.removeAudioFile(position);
                toastManager.showToastShort("파일이 삭제되었습니다");
                
                // 빈 상태 체크
                updateEmptyState(adapter.getItemCount() == 0);
            } else {
                toastManager.showToastShort("파일 삭제에 실패했습니다");
            }
            
        } catch (Exception e) {
            toastManager.showToastShort("삭제 실패: " + e.getMessage());
        }
    }
    
    private String removeFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return fileName;
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // AudioPlayerManager 리소스 해제
        if (audioPlayerManager != null) {
            audioPlayerManager.release();
            audioPlayerManager = null;
        }
        
        binding = null;
    }
}