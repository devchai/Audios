package com.devc.lab.audios.manager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.widget.ProgressBar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.devc.lab.audios.R;
import com.devc.lab.audios.databinding.DialogConversionProgressBinding;
import com.devc.lab.audios.model.ConversionSettings;

public class DialogManager {

    private Context context;
    private AlertDialog progressDialog;
    private ProgressBar progressBar;
    private androidx.appcompat.app.AlertDialog conversionProgressDialog;
    private DialogConversionProgressBinding progressBinding;
    private Handler mainHandler; // 메인 스레드에서 UI 업데이트 보장

    public DialogManager(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Context가 유효한지 확인 (메소드 하단에 추가)
     * @return Context가 유효하고 Activity가 활성 상태면 true
     */
    private boolean isContextValid() {
        if (context == null) {
            return false;
        }
        
        // Activity 인스턴스인 경우 생명주기 체크
        if (context instanceof android.app.Activity) {
            android.app.Activity activity = (android.app.Activity) context;
            return !activity.isFinishing() && !activity.isDestroyed();
        }
        
        // 다른 Context 타입인 경우 기본적으로 유효하다고 가정
        return true;
    }

    public void showConfirmationDialog(String title, String message, DialogInterface.OnClickListener positiveListener, DialogInterface.OnClickListener negativeListener) {
        if (!isContextValid()) {
            LoggerManager.logger("⚠️ DialogManager - Context가 유효하지 않음. 확인 다이얼로그 표시 취소");
            return;
        }

        try {
            new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("Yes", positiveListener)
                    .setNegativeButton("No", negativeListener)
                    .show();
        } catch (Exception e) {
            LoggerManager.logger("❌ 확인 다이얼로그 생성 실패: " + e.getMessage());
        }
    }

    public void showMessageDialog(String title, String message, DialogInterface.OnClickListener positiveListener) {
        if (!isContextValid()) {
            LoggerManager.logger("⚠️ DialogManager - Context가 유효하지 않음. 메시지 다이얼로그 표시 취소");
            return;
        }
        
        try {
            new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", positiveListener)
                    .show();
        } catch (Exception e) {
            LoggerManager.logger("❌ 메시지 다이얼로그 생성 실패: " + e.getMessage());
        }
    }

    public void showProgressDialog() {
        if (!isContextValid()) {
            LoggerManager.logger("⚠️ DialogManager - Context가 유효하지 않음. 단순 진행 다이얼로그 표시 취소");
            return;
        }
        
        try {
            // 기존 다이얼로그 정리
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }

            // 프로그래스바 생성
            progressBar = new ProgressBar(context);
            progressBar.setIndeterminate(false);
            progressBar.setMax(100);
            progressBar.setProgress(0);

            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setView(progressBar);
            builder.setCancelable(false);
            
            progressDialog = builder.create();
            progressDialog.setMessage("변환중...");
            
            if (isContextValid()) {
                progressDialog.show();
                LoggerManager.logger("⚙️ 단순 진행 다이얼로그 표시");
            }
            
        } catch (Exception e) {
            LoggerManager.logger("❌ 단순 진행 다이얼로그 생성 실패: " + e.getMessage());
        }
    }
    
    public void showConversionProgressDialog(String fileName, ConversionSettings settings) {
        // Context 유효성 체크
        if (!isContextValid()) {
            LoggerManager.logger("⚠️ DialogManager - Context가 유효하지 않음. 다이얼로그 표시 취소");
            return;
        }
        
        try {
            // 기존 다이얼로그 정리
            dismissProgressDialog();
            
            LayoutInflater inflater = LayoutInflater.from(context);
            progressBinding = DialogConversionProgressBinding.inflate(inflater);
            
            // 파일 정보 설정
            progressBinding.tvFileName.setText(fileName);
            String conversionInfo = settings.getFormat().name() + " (" + settings.getBitrate() + " kbps)";
            progressBinding.tvConversionInfo.setText(conversionInfo);
            
            // 초기 진행 상태 설정
            progressBinding.progressBar.setProgress(0);
            progressBinding.tvProgressPercent.setText("0%");
            progressBinding.tvEstimatedTime.setText("예상 시간: 계산 중...");
            progressBinding.tvCurrentStep.setText("변환 준비 중...");
            
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                    .setView(progressBinding.getRoot())
                    .setCancelable(false);
                    
            conversionProgressDialog = builder.create();
            
            // 백그라운드 버튼 클릭 리스너
            progressBinding.btnBackground.setOnClickListener(v -> {
                if (conversionProgressDialog != null && conversionProgressDialog.isShowing()) {
                    conversionProgressDialog.dismiss();
                }
                // TODO: 백그라운드 변환 기능 구현
            });
            
            // 취소 버튼 클릭 리스너
            progressBinding.btnCancelConversion.setOnClickListener(v -> {
                // TODO: 변환 취소 기능 구현
                if (conversionProgressDialog != null && conversionProgressDialog.isShowing()) {
                    conversionProgressDialog.dismiss();
                }
            });
            
            // Context 다시 한 번 체크 후 표시
            if (isContextValid()) {
                conversionProgressDialog.show();
                LoggerManager.logger("⚙️ 변환 진행 다이얼로그 표시 - 파일: " + fileName);
            }
            
        } catch (Exception e) {
            LoggerManager.logger("❌ 변환 다이얼로그 생성 실패: " + e.getMessage());
        }
    }
    
    public void updateConversionProgress(int progress) {
        // 메인 스레드에서 안전하게 UI 업데이트 실행
        if (mainHandler != null) {
            mainHandler.post(() -> updateConversionProgressInternal(progress));
        } else {
            updateConversionProgressInternal(progress);
        }
    }
    
    private void updateConversionProgressInternal(int progress) {
        // 1단계: Context 유효성 체크
        if (!isContextValid()) {
            LoggerManager.logger("⚠️ DialogManager - Context가 유효하지 않음 (진행률: " + progress + "%). UI 업데이트 취소");
            return;
        }
        
        // 2단계: progressBinding 유효성 체크
        if (progressBinding == null) {
            LoggerManager.logger("⚠️ DialogManager - progressBinding이 null임 (진행률: " + progress + "%). UI 업데이트 취소");
            return;
        }
        
        // 3단계: 다이얼로그 상태 체크
        if (conversionProgressDialog == null) {
            LoggerManager.logger("⚠️ DialogManager - conversionProgressDialog이 null임 (진행률: " + progress + "%). UI 업데이트 취소");
            return;
        }
        
        if (!conversionProgressDialog.isShowing()) {
            LoggerManager.logger("⚠️ DialogManager - 다이얼로그가 표시되지 않음 (진행률: " + progress + "%). UI 업데이트 취소");
            return;
        }
        
        try {
            // 진행률 범위 제한 (0-100)
            int safeProgress = Math.max(0, Math.min(100, progress));
            
            // UI 업데이트 실행
            progressBinding.progressBar.setProgress(safeProgress);
            progressBinding.tvProgressPercent.setText(safeProgress + "%");
            
            // 단계별 상태 메시지 업데이트
            String currentStep = getProgressStepMessage(safeProgress);
            progressBinding.tvCurrentStep.setText(currentStep);
            
            LoggerManager.logger("📈 변환 진행률 업데이트 성공: " + safeProgress + "% - " + currentStep);
            
        } catch (Exception e) {
            LoggerManager.logger("❌ 진행률 업데이트 실패: " + e.getMessage() + " (진행률: " + progress + "%)");
        }
    }
    
    /**
     * 진행률에 따른 단계 메시지 반환
     */
    private String getProgressStepMessage(int progress) {
        if (progress < 20) {
            return "파일 분석 중...";
        } else if (progress < 50) {
            return "오디오 추출 중...";
        } else if (progress < 80) {
            return "포맷 변환 중...";
        } else if (progress < 100) {
            return "파일 저장 중...";
        } else {
            return "변환 완료!";
        }
    }
    
    public void updateEstimatedTime(String timeText) {
        if (mainHandler != null) {
            mainHandler.post(() -> updateEstimatedTimeInternal(timeText));
        } else {
            updateEstimatedTimeInternal(timeText);
        }
    }
    
    private void updateEstimatedTimeInternal(String timeText) {
        if (isContextValid() && progressBinding != null) {
            try {
                progressBinding.tvEstimatedTime.setText("남은 시간: " + timeText);
            } catch (Exception e) {
                LoggerManager.logger("❌ 예상 시간 업데이트 실패: " + e.getMessage());
            }
        }
    }
    
    public void updateFileSizeInfo(String originalSize, String outputSize) {
        if (mainHandler != null) {
            mainHandler.post(() -> updateFileSizeInfoInternal(originalSize, outputSize));
        } else {
            updateFileSizeInfoInternal(originalSize, outputSize);
        }
    }
    
    private void updateFileSizeInfoInternal(String originalSize, String outputSize) {
        if (isContextValid() && progressBinding != null) {
            try {
                String sizeInfo = "원본: " + originalSize + " • 출력: " + outputSize;
                progressBinding.tvFileSizeInfo.setText(sizeInfo);
            } catch (Exception e) {
                LoggerManager.logger("❌ 파일 크기 정보 업데이트 실패: " + e.getMessage());
            }
        }
    }

    public void updateProgress(int progress) {
        // 이제 새로운 변환 다이얼로그만 사용하므로 updateConversionProgress로 위임
        updateConversionProgress(progress);
        
        // 레거시 지원: 기존 progressBar가 있는 경우에만 업데이트
        if (progressBar != null) {
            try {
                int safeProgress = Math.max(0, Math.min(100, progress));
                progressBar.setProgress(safeProgress);
            } catch (Exception e) {
                LoggerManager.logger("❌ ProgressBar 업데이트 실패: " + e.getMessage());
            }
        }
    }

    public void dismissProgressDialog() {
        try {
            // 기존 단순 다이얼로그 정리
            if (progressDialog != null) {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                    LoggerManager.logger("🖯️ 기존 progress 다이얼로그 닫음");
                }
                progressDialog = null;
            }
            
            // 변환 진행 다이얼로그 정리
            if (conversionProgressDialog != null) {
                if (conversionProgressDialog.isShowing()) {
                    conversionProgressDialog.dismiss();
                    LoggerManager.logger("🖯️ 변환 진행 다이얼로그 닫음");
                }
                conversionProgressDialog = null;
            }
            
            // 바인딩 참조 정리
            if (progressBinding != null) {
                progressBinding = null;
                LoggerManager.logger("🧹 ProgressBinding 참조 해제");
            }
            
            // ProgressBar 참조 정리
            progressBar = null;
            
        } catch (Exception e) {
            LoggerManager.logger("❌ 다이얼로그 정리 실패: " + e.getMessage());
        }
    }
}