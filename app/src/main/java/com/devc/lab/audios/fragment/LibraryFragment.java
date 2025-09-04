package com.devc.lab.audios.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.devc.lab.audios.databinding.FragmentLibraryBinding;
import com.devc.lab.audios.adapter.AudioFileAdapter;
import com.devc.lab.audios.model.AudioFile;
import com.devc.lab.audios.manager.FileManager;
import com.devc.lab.audios.manager.ToastManager;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LibraryFragment extends Fragment implements AudioFileAdapter.OnItemClickListener, AudioFileAdapter.OnItemLongClickListener {
    private FragmentLibraryBinding binding;
    private AudioFileAdapter adapter;
    private FileManager fileManager;
    private ToastManager toastManager;
    
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
        
        // 어댑터 설정
        adapter = new AudioFileAdapter(getContext());
        adapter.setOnItemClickListener(this);
        adapter.setOnItemLongClickListener(this);
        
        // RecyclerView 설정
        binding.recyclerViewFiles.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerViewFiles.setAdapter(adapter);
        
        // 새로고침 리스너
        binding.swipeRefreshLayout.setOnRefreshListener(this::loadFiles);
    }
    
    private void loadFiles() {
        // 임시로 샘플 데이터를 생성 (실제 구현에서는 FileManager를 통해 파일 스캔)
        List<AudioFile> sampleFiles = createSampleFiles();
        adapter.setAudioFiles(sampleFiles);
        
        // 빈 목록 처리
        updateEmptyState(sampleFiles.isEmpty());
        
        binding.swipeRefreshLayout.setRefreshing(false);
    }
    
    private List<AudioFile> createSampleFiles() {
        List<AudioFile> files = new ArrayList<>();
        
        // 샘플 파일 1
        AudioFile file1 = new AudioFile("converted_video1.mp3", "/storage/emulated/0/Audios/converted_video1.mp3");
        file1.setFileSize(5 * 1024 * 1024); // 5MB
        file1.setDuration(3 * 60 * 1000 + 24 * 1000); // 3분 24초
        file1.setFormat("MP3");
        file1.setBitrate(128);
        file1.setModifiedDate(new Date());
        files.add(file1);
        
        // 샘플 파일 2
        AudioFile file2 = new AudioFile("my_song.wav", "/storage/emulated/0/Audios/my_song.wav");
        file2.setFileSize(25 * 1024 * 1024); // 25MB
        file2.setDuration(4 * 60 * 1000 + 15 * 1000); // 4분 15초
        file2.setFormat("WAV");
        file2.setBitrate(320);
        file2.setModifiedDate(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)); // 어제
        files.add(file2);
        
        // 샘플 파일 3
        AudioFile file3 = new AudioFile("audio_extract.aac", "/storage/emulated/0/Audios/audio_extract.aac");
        file3.setFileSize(8 * 1024 * 1024); // 8MB
        file3.setDuration(2 * 60 * 1000 + 45 * 1000); // 2분 45초
        file3.setFormat("AAC");
        file3.setBitrate(256);
        file3.setModifiedDate(new Date(System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000)); // 3일 전
        files.add(file3);
        
        return files;
    }
    
    private void updateEmptyState(boolean isEmpty) {
        if (isEmpty) {
            binding.recyclerViewFiles.setVisibility(View.GONE);
            // 빈 상태 뷰 표시 (추후 구현)
        } else {
            binding.recyclerViewFiles.setVisibility(View.VISIBLE);
        }
    }
    
    // AudioFileAdapter.OnItemClickListener 구현
    @Override
    public void onItemClick(AudioFile audioFile, int position) {
        toastManager.showToastShort("파일 선택: " + audioFile.getDisplayName());
        // TODO: 파일 상세 정보 표시 또는 재생
    }
    
    @Override
    public void onPlayClick(AudioFile audioFile, int position) {
        toastManager.showToastShort("재생: " + audioFile.getDisplayName());
        // TODO: 오디오 플레이어 연동
    }
    
    @Override
    public void onMoreClick(AudioFile audioFile, int position) {
        // TODO: 더보기 메뉴 (공유, 삭제, 이름 변경 등)
        showFileOptionsMenu(audioFile, position);
    }
    
    // AudioFileAdapter.OnItemLongClickListener 구현
    @Override
    public void onItemLongClick(AudioFile audioFile, int position) {
        // 길게 눌렀을 때 컨텍스트 메뉴 또는 선택 모드
        showFileOptionsMenu(audioFile, position);
    }
    
    private void showFileOptionsMenu(AudioFile audioFile, int position) {
        // TODO: 파일 옵션 메뉴 다이얼로그 구현
        toastManager.showToastShort("파일 옵션: " + audioFile.getDisplayName());
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}