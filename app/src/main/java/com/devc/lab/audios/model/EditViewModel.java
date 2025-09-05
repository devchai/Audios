package com.devc.lab.audios.model;

import android.app.Application;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.devc.lab.audios.manager.AudioTrimManager;
import com.devc.lab.audios.manager.LoggerManager;

/**
 * 편집 화면의 ViewModel
 */
public class EditViewModel extends AndroidViewModel {
    
    // LiveData
    private final MutableLiveData<Uri> selectedFileUri = new MutableLiveData<>();
    private final MutableLiveData<String> fileName = new MutableLiveData<>();
    private final MutableLiveData<Long> audioDuration = new MutableLiveData<>(0L);
    private final MutableLiveData<Float> trimStartPosition = new MutableLiveData<>(0f);
    private final MutableLiveData<Float> trimEndPosition = new MutableLiveData<>(1f);
    private final MutableLiveData<Boolean> isProcessing = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> processingProgress = new MutableLiveData<>(0);
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    private final MutableLiveData<Float> playbackPosition = new MutableLiveData<>(0f);
    
    // Manager
    private AudioTrimManager audioTrimManager;
    
    public EditViewModel(@NonNull Application application) {
        super(application);
        initManagers();
    }
    
    private void initManagers() {
        audioTrimManager = AudioTrimManager.getInstance();
        audioTrimManager.init(getApplication());
        setupTrimManagerCallbacks();
    }
    
    private void setupTrimManagerCallbacks() {
        audioTrimManager.setOnStartListener(() -> {
            isProcessing.postValue(true);
            processingProgress.postValue(0);
            statusMessage.postValue("자르기 시작...");
            LoggerManager.logger("자르기 작업 시작");
        });
        
        audioTrimManager.setOnProgressListener(progress -> {
            processingProgress.postValue(progress);
            statusMessage.postValue("자르기 중... " + progress + "%");
        });
        
        audioTrimManager.setOnCompletionListener(outputPath -> {
            isProcessing.postValue(false);
            processingProgress.postValue(100);
            statusMessage.postValue("자르기 완료!");
            LoggerManager.logger("자르기 완료: " + outputPath);
        });
        
        audioTrimManager.setOnErrorListener(error -> {
            isProcessing.postValue(false);
            statusMessage.postValue("오류: " + error);
            LoggerManager.logger("자르기 오류: " + error);
        });
    }
    
    /**
     * 파일 선택
     */
    public void selectFile(Uri uri, String name) {
        selectedFileUri.setValue(uri);
        fileName.setValue(name);
        // 자르기 위치 초기화
        trimStartPosition.setValue(0f);
        trimEndPosition.setValue(1f);
        LoggerManager.logger("파일 선택: " + name);
    }
    
    /**
     * 오디오 길이 설정
     */
    public void setAudioDuration(long durationMs) {
        audioDuration.setValue(durationMs);
        LoggerManager.logger("오디오 길이 설정: " + durationMs + "ms");
    }
    
    /**
     * 자르기 시작 위치 설정
     */
    public void setTrimStartPosition(float position) {
        position = Math.max(0f, Math.min(position, getTrimEndPosition().getValue() - 0.05f));
        trimStartPosition.setValue(position);
        LoggerManager.logger("자르기 시작 위치: " + position);
    }
    
    /**
     * 자르기 끝 위치 설정
     */
    public void setTrimEndPosition(float position) {
        position = Math.max(getTrimStartPosition().getValue() + 0.05f, Math.min(1f, position));
        trimEndPosition.setValue(position);
        LoggerManager.logger("자르기 끝 위치: " + position);
    }
    
    /**
     * 자르기 실행
     */
    public void performTrim() {
        Uri uri = selectedFileUri.getValue();
        if (uri == null) {
            statusMessage.setValue("파일이 선택되지 않았습니다");
            return;
        }
        
        Long duration = audioDuration.getValue();
        if (duration == null || duration == 0) {
            statusMessage.setValue("오디오 길이를 확인할 수 없습니다");
            return;
        }
        
        Float startPos = trimStartPosition.getValue();
        Float endPos = trimEndPosition.getValue();
        
        if (startPos == null || endPos == null) {
            statusMessage.setValue("자르기 위치가 설정되지 않았습니다");
            return;
        }
        
        // 출력 파일명 생성
        String originalName = fileName.getValue();
        if (originalName == null) originalName = "audio";
        
        String baseName = originalName.contains(".") ? 
            originalName.substring(0, originalName.lastIndexOf(".")) : originalName;
        String extension = originalName.contains(".") ? 
            originalName.substring(originalName.lastIndexOf(".")) : ".mp3";
        
        String outputFileName = "trimmed_" + baseName + "_" + System.currentTimeMillis() + extension;
        
        // 자르기 실행
        audioTrimManager.trimAudioByRatio(uri, startPos, endPos, duration, outputFileName);
        
        LoggerManager.logger(String.format("자르기 실행: %s (%.1f%% ~ %.1f%%)", 
            outputFileName, startPos * 100, endPos * 100));
    }
    
    /**
     * 자르기 시간 텍스트 가져오기
     */
    public String getTrimTimeText() {
        Long duration = audioDuration.getValue();
        Float startPos = trimStartPosition.getValue();
        Float endPos = trimEndPosition.getValue();
        
        if (duration == null || startPos == null || endPos == null) {
            return "00:00 - 00:00";
        }
        
        long startMs = (long) (startPos * duration);
        long endMs = (long) (endPos * duration);
        
        String startTime = audioTrimManager.millisToTimeString(startMs);
        String endTime = audioTrimManager.millisToTimeString(endMs);
        
        return startTime + " - " + endTime;
    }
    
    /**
     * 자르기 길이 텍스트 가져오기
     */
    public String getTrimDurationText() {
        Long duration = audioDuration.getValue();
        Float startPos = trimStartPosition.getValue();
        Float endPos = trimEndPosition.getValue();
        
        if (duration == null || startPos == null || endPos == null) {
            return "00:00";
        }
        
        long trimDurationMs = (long) ((endPos - startPos) * duration);
        return audioTrimManager.millisToTimeString(trimDurationMs);
    }
    
    /**
     * 재생 상태 설정
     */
    public void setPlaying(boolean playing) {
        isPlaying.setValue(playing);
    }
    
    /**
     * 재생 위치 설정
     */
    public void setPlaybackPosition(float position) {
        playbackPosition.setValue(position);
    }
    
    /**
     * 초기화
     */
    public void reset() {
        selectedFileUri.setValue(null);
        fileName.setValue(null);
        audioDuration.setValue(0L);
        trimStartPosition.setValue(0f);
        trimEndPosition.setValue(1f);
        isProcessing.setValue(false);
        processingProgress.setValue(0);
        statusMessage.setValue("");
        isPlaying.setValue(false);
        playbackPosition.setValue(0f);
    }
    
    // Getter 메서드들 (LiveData)
    
    public LiveData<Uri> getSelectedFileUri() {
        return selectedFileUri;
    }
    
    public LiveData<String> getFileName() {
        return fileName;
    }
    
    public LiveData<Long> getAudioDuration() {
        return audioDuration;
    }
    
    public LiveData<Float> getTrimStartPosition() {
        return trimStartPosition;
    }
    
    public LiveData<Float> getTrimEndPosition() {
        return trimEndPosition;
    }
    
    public LiveData<Boolean> getIsProcessing() {
        return isProcessing;
    }
    
    public LiveData<Integer> getProcessingProgress() {
        return processingProgress;
    }
    
    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }
    
    public LiveData<Boolean> getIsPlaying() {
        return isPlaying;
    }
    
    public LiveData<Float> getPlaybackPosition() {
        return playbackPosition;
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        audioTrimManager.clearListeners();
    }
}