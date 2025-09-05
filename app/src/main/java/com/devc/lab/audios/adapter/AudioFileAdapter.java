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
        private ItemAudioFileBinding binding;
        
        public AudioFileViewHolder(@NonNull ItemAudioFileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        public void bind(AudioFile audioFile, int position) {
            // 디버깅 로그 추가
            android.util.Log.d("AudioFileAdapter", "바인딩: " + audioFile.getDisplayName() + " at position: " + position);
            
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
            
            // 메인 컨테이너에 클릭 리스너 설정 - 재생 버튼 제외한 영역
            View mainContainer = binding.getRoot().findViewById(R.id.layout_main_container);
            if (mainContainer != null) {
                // 메인 아이템 클릭 리스너 (파일 상세 정보 표시)
                mainContainer.setOnClickListener(v -> {
                    android.util.Log.d("AudioFileAdapter", "클릭 감지: " + audioFile.getDisplayName());
                    if (onItemClickListener != null) {
                        onItemClickListener.onItemClick(audioFile, position);
                    }
                });
                
                // 길게 누르기 - 더보기 메뉴 표시 (디버깅 로그 추가)
                mainContainer.setOnLongClickListener(v -> {
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
            } else {
                android.util.Log.e("AudioFileAdapter", "layout_main_container를 찾을 수 없습니다!");
            }
            
            // 재생 상태에 따른 아이콘 설정
            updatePlayButtonIcon(audioFile);
            
            // 재생 버튼 클릭 리스너
            binding.btnPlay.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    onItemClickListener.onPlayClick(audioFile, position);
                }
            });
            
            // 재생 버튼 길게 누르기 리스너 - 완전 정지 기능
            binding.btnPlay.setOnLongClickListener(v -> {
                android.util.Log.d("AudioFileAdapter", "플레이 버튼 길게 누르기: " + audioFile.getDisplayName());
                
                // 현재 재생 중인 파일인지 확인
                boolean isCurrentlyPlaying = audioFile.getFilePath().equals(currentPlayingFilePath) && isPlaying;
                
                if (isCurrentlyPlaying) {
                    showStopText(audioFile);
                    android.util.Log.d("AudioFileAdapter", "재생 중인 파일 - 정지 텍스트 표시");
                    return true;
                } else {
                    android.util.Log.d("AudioFileAdapter", "재생 중이 아님 - 길게 누르기 무시");
                    return false;
                }
            });
        }
        
        /**
         * 재생 상태에 따른 재생 버튼 아이콘 업데이트
         */
        private void updatePlayButtonIcon(AudioFile audioFile) {
            // 새로운 XML 구조에 맞게 ImageView와 TextView 찾기
            ImageView playIcon = binding.btnPlay.findViewById(R.id.iv_play_icon);
            TextView stopText = binding.btnPlay.findViewById(R.id.tv_stop_text);
            
            // 현재 파일이 재생 중인지 확인
            boolean isCurrentlyPlaying = audioFile.getFilePath().equals(currentPlayingFilePath) && isPlaying;
            
            if (playIcon != null) {
                // 정지 텍스트가 표시 중이 아닐 때만 아이콘 업데이트
                if (stopText != null && stopText.getVisibility() != View.VISIBLE) {
                    if (isCurrentlyPlaying) {
                        // 재생 중이면 일시정지 아이콘 표시
                        playIcon.setImageResource(R.drawable.ic_pause);
                    } else {
                        // 정지 상태이면 재생 아이콘 표시  
                        playIcon.setImageResource(R.drawable.ic_play);
                    }
                }
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
            
            binding.ivFileThumbnail.setImageResource(iconResource);
        }
        
        /**
         * 정지 텍스트 표시 및 UI 효과
         */
        private void showStopText(AudioFile audioFile) {
            ImageView playIcon = binding.btnPlay.findViewById(R.id.iv_play_icon);
            TextView stopText = binding.btnPlay.findViewById(R.id.tv_stop_text);
            
            if (playIcon != null && stopText != null) {
                // 아이콘 숨김, 텍스트 표시
                playIcon.setVisibility(View.INVISIBLE);
                stopText.setVisibility(View.VISIBLE);
                
                // TODO: 실제 오디오 정지 기능 호출
                // if (onItemClickListener != null) {
                //     onItemClickListener.onStopClick(audioFile);
                // }
                
                // 500ms 후 UI 복원
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                handler.postDelayed(() -> {
                    stopText.setVisibility(View.GONE);
                    playIcon.setVisibility(View.VISIBLE);
                    
                    // 재생 상태 업데이트 (정지 후이므로 재생 아이콘 표시)
                    if (playIcon instanceof ImageView) {
                        ((ImageView) playIcon).setImageResource(R.drawable.ic_play);
                    }
                    
                    android.util.Log.d("AudioFileAdapter", "정지 텍스트 숨김 및 UI 복원");
                }, 500);
                
                android.util.Log.d("AudioFileAdapter", "정지 텍스트 표시 완료");
            }
        }
    }
}