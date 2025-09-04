package com.devc.lab.audios.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.devc.lab.audios.R;
import com.devc.lab.audios.databinding.ItemAudioFileBinding;
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
        ItemAudioFileBinding binding = ItemAudioFileBinding.inflate(inflater, parent, false);
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
    
    class AudioFileViewHolder extends RecyclerView.ViewHolder {
        private ItemAudioFileBinding binding;
        
        public AudioFileViewHolder(@NonNull ItemAudioFileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        public void bind(AudioFile audioFile, int position) {
            // 파일 이름 설정
            binding.tvFileName.setText(audioFile.getDisplayName());
            
            // 파일 크기 설정
            String sizeText = context.getString(R.string.file_size, audioFile.getFormattedFileSize());
            binding.tvFileSize.setText(sizeText);
            
            // 파일 길이 설정
            String durationText = context.getString(R.string.file_duration, audioFile.getFormattedDuration());
            binding.tvFileDuration.setText(durationText);
            
            // 파일 날짜 설정
            String dateText = context.getString(R.string.file_date, 
                    audioFile.getModifiedDate() != null ? 
                    dateFormat.format(audioFile.getModifiedDate()) : "알 수 없음");
            binding.tvFileDate.setText(dateText);
            
            // 포맷 배지 설정
            String extension = audioFile.getFileExtension().toUpperCase();
            binding.tvFormatBadge.setText(extension);
            
            // 썸네일/아이콘 설정 (포맷별로 다른 아이콘 사용 가능)
            setFileIcon(audioFile.getFormat());
            
            // 클릭 리스너 설정
            binding.getRoot().setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onItemClick(audioFile, position);
                }
            });
            
            binding.getRoot().setOnLongClickListener(v -> {
                if (onItemLongClickListener != null) {
                    onItemLongClickListener.onItemLongClick(audioFile, position);
                }
                return true;
            });
            
            // 재생 버튼 클릭 리스너
            binding.btnPlay.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onPlayClick(audioFile, position);
                }
            });
            
            // 더보기 버튼 클릭 리스너
            binding.btnMore.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onMoreClick(audioFile, position);
                }
            });
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
            
            binding.ivFileThumbnail.setImageResource(iconResource);
        }
    }
}