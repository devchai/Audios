package com.devc.lab.audios.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.devc.lab.audios.R;
import com.devc.lab.audios.databinding.ItemAudioFileSpotifyBinding;
import com.devc.lab.audios.model.AudioFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AudioFileAdapter extends RecyclerView.Adapter<AudioFileAdapter.AudioFileViewHolder> {
    
    private List<AudioFile> audioFiles;
    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;
    private Context context;
    private SimpleDateFormat dateFormat;
    
    // 재생 상태 추적
    private String currentPlayingFilePath = null;
    private boolean isPlaying = false;
    
    public interface OnItemClickListener {
        void onItemClick(AudioFile audioFile, int position);
        void onPlayClick(AudioFile audioFile, int position);
        void onMoreClick(AudioFile audioFile, int position);
    }
    
    public interface OnItemLongClickListener {
        void onItemLongClick(AudioFile audioFile, int position);
    }
    
    public AudioFileAdapter(Context context) {
        this.context = context;
        this.audioFiles = new ArrayList<>();
        this.dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault());
    }
    
    @NonNull
    @Override
    public AudioFileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemAudioFileSpotifyBinding binding = ItemAudioFileSpotifyBinding.inflate(inflater, parent, false);
        return new AudioFileViewHolder(binding);
    }
    
    @Override
    public void onBindViewHolder(@NonNull AudioFileViewHolder holder, int position) {
        AudioFile audioFile = audioFiles.get(position);
        holder.bind(audioFile, position);
    }
    
    @Override
    public int getItemCount() {
        return audioFiles.size();
    }
    
    public void setAudioFiles(List<AudioFile> audioFiles) {
        this.audioFiles = audioFiles;
        notifyDataSetChanged();
    }
    
    public void addAudioFile(AudioFile audioFile) {
        this.audioFiles.add(0, audioFile); // 맨 앞에 추가
        notifyItemInserted(0);
    }
    
    public void removeAudioFile(int position) {
        if (position >= 0 && position < audioFiles.size()) {
            audioFiles.remove(position);
            notifyItemRemoved(position);
        }
    }
    
    public AudioFile getAudioFile(int position) {
        if (position >= 0 && position < audioFiles.size()) {
            return audioFiles.get(position);
        }
        return null;
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }
    
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.onItemLongClickListener = listener;
    }
    
    /**
     * 재생 상태 업데이트 및 해당 아이템 UI 갱신
     */
    public void updatePlaybackState(String filePath, boolean isPlaying) {
        String oldPlayingPath = this.currentPlayingFilePath;
        boolean oldPlayingState = this.isPlaying;
        
        this.currentPlayingFilePath = filePath;
        this.isPlaying = isPlaying;
        
        // 이전에 재생 중이던 아이템 업데이트
        if (oldPlayingPath != null && !oldPlayingPath.equals(filePath)) {
            int oldPosition = findPositionByFilePath(oldPlayingPath);
            if (oldPosition != -1) {
                notifyItemChanged(oldPosition);
            }
        }
        
        // 현재 재생 중인 아이템 업데이트
        if (filePath != null) {
            int currentPosition = findPositionByFilePath(filePath);
            if (currentPosition != -1) {
                notifyItemChanged(currentPosition);
            }
        }
        
        // 상태가 변경되었으나 같은 파일인 경우에도 업데이트
        if (filePath != null && filePath.equals(oldPlayingPath) && oldPlayingState != isPlaying) {
            int position = findPositionByFilePath(filePath);
            if (position != -1) {
                notifyItemChanged(position);
            }
        }
    }
    
    /**
     * 파일 경로로 리스트 내 위치 찾기
     */
    private int findPositionByFilePath(String filePath) {
        if (filePath == null) return -1;
        
        for (int i = 0; i < audioFiles.size(); i++) {
            AudioFile audioFile = audioFiles.get(i);
            if (audioFile != null && filePath.equals(audioFile.getFilePath())) {
                return i;
            }
        }
        return -1;
    }
    
    class AudioFileViewHolder extends RecyclerView.ViewHolder {
        private ItemAudioFileSpotifyBinding binding;
        
        public AudioFileViewHolder(@NonNull ItemAudioFileSpotifyBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        public void bind(AudioFile audioFile, int position) {
            // 디버깅 로그 추가
            android.util.Log.d("AudioFileAdapter", "바인딩: " + audioFile.getDisplayName() + " at position: " + position);
            
            // 파일 이름 설정
            binding.fileName.setText(audioFile.getDisplayName());
            
            // 파일 크기 설정
            binding.fileSize.setText(audioFile.getFormattedFileSize());
            
            // 파일 길이 설정
            binding.fileDuration.setText(audioFile.getFormattedDuration());
            
            // 파일 날짜 설정
            binding.fileDate.setText(audioFile.getModifiedDate() != null ? 
                    dateFormat.format(audioFile.getModifiedDate()) : "알 수 없음");
            
            // 포맷 배지 설정
            String extension = audioFile.getFileExtension().toUpperCase();
            binding.formatTag.setText(extension);
            
            // 품질 태그 설정 (비트레이트 기반)
            setQualityTag(audioFile);
            
            // 썸네일/아이콘 설정 (포맷별로 다른 아이콘 사용 가능)
            setFileIcon(audioFile.getFormat());
            
            // 메인 컨테이너에 클릭 리스너 설정 (전체 아이템 영역)
            binding.getRoot().setOnClickListener(v -> {
                android.util.Log.d("AudioFileAdapter", "클릭 감지: " + audioFile.getDisplayName());
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(audioFile, position);
                }
            });
            
            // 길게 누르기 - 더보기 메뉴 표시
            binding.getRoot().setOnLongClickListener(v -> {
                android.util.Log.d("AudioFileAdapter", "길게 누르기 감지: " + audioFile.getDisplayName());
                if (onItemLongClickListener != null) {
                    android.util.Log.d("AudioFileAdapter", "onItemLongClickListener 호출");
                    onItemLongClickListener.onItemLongClick(audioFile, position);
                    return true;
                } else {
                    android.util.Log.e("AudioFileAdapter", "onItemLongClickListener가 null입니다!");
                    return false;
                }
            });
            
            // 재생 상태에 따른 아이콘 설정
            updatePlayButtonIcon(audioFile);
            
            // 재생 버튼 클릭 리스너
            binding.playButton.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onPlayClick(audioFile, position);
                }
            });
            
            // 더보기 버튼 클릭 리스너
            binding.moreOptions.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onMoreClick(audioFile, position);
                }
            });
            
            // 재생 버튼 길게 누르기 리스너 제거 (Spotify 스타일에는 없음)
        }
        
        /**
         * 재생 상태에 따른 재생 버튼 아이콘 업데이트 (Spotify 스타일)
         */
        private void updatePlayButtonIcon(AudioFile audioFile) {
            // 현재 파일이 재생 중인지 확인
            boolean isCurrentlyPlaying = audioFile.getFilePath().equals(currentPlayingFilePath) && isPlaying;
            
            // Spotify 스타일: 단순한 ImageView로 아이콘 변경
            if (isCurrentlyPlaying) {
                // 재생 중이면 일시정지 아이콘 표시
                binding.playButton.setImageResource(R.drawable.ic_pause);
            } else {
                // 정지 상태이면 재생 아이콘 표시  
                binding.playButton.setImageResource(R.drawable.ic_play);
            }
        }
        
        private void setFileIcon(String format) {
            // 포맷별로 다른 아이콘 설정
            int iconResource = R.drawable.ic_audio; // 기본 아이콘
            
            if (format != null) {
                switch (format.toLowerCase()) {
                    case "mp3":
                        iconResource = R.drawable.ic_audio;
                        break;
                    case "wav":
                        iconResource = R.drawable.ic_audio;
                        break;
                    case "aac":
                        iconResource = R.drawable.ic_audio;
                        break;
                    default:
                        iconResource = R.drawable.ic_audio;
                        break;
                }
            }
            
            binding.fileIcon.setImageResource(iconResource);
        }
        
        /**
         * 비트레이트에 따른 품질 태그 설정
         */
        private void setQualityTag(AudioFile audioFile) {
            // 비트레이트 정보가 있으면 표시
            long bitrate = audioFile.getBitrate();
            if (bitrate > 0) {
                String bitrateText = (bitrate / 1000) + "kbps";
                binding.qualityTag.setText(bitrateText);
                binding.qualityTag.setVisibility(View.VISIBLE);
                
                // 품질에 따른 색상 설정
                if (bitrate >= 320000) { // 320kbps 이상
                    binding.qualityTag.setTextColor(context.getColor(R.color.quality_high));
                } else if (bitrate >= 192000) { // 192kbps 이상
                    binding.qualityTag.setTextColor(context.getColor(R.color.quality_medium));
                } else { // 192kbps 미만
                    binding.qualityTag.setTextColor(context.getColor(R.color.quality_low));
                }
            } else {
                // 비트레이트 정보가 없으면 숨김
                binding.qualityTag.setVisibility(View.GONE);
            }
        }
    }
}