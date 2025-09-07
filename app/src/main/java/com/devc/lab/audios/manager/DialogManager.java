package com.devc.lab.audios.manager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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

    public DialogManager(Context context) {
        this.context = context;
    }

    public void showConfirmationDialog(String title, String message, DialogInterface.OnClickListener positiveListener, DialogInterface.OnClickListener negativeListener) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Yes", positiveListener)
                .setNegativeButton("No", negativeListener)
                .show();
    }

    public void showMessageDialog(String title, String message, DialogInterface.OnClickListener positiveListener) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", positiveListener)
                .show();
    }

    public void showProgressDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        if (progressDialog != null && progressDialog.isShowing()){
            progressDialog.dismiss();
        }

        // 프로그래스바 생성
        progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
        progressBar.setProgress(0);


        builder.setView(progressBar);

        // 대화상자 설정
        builder.setCancelable(false);
        progressDialog = builder.create();
        progressDialog.setMessage("변환중...");
        progressDialog.show();
    }
    
    public void showConversionProgressDialog(String fileName, ConversionSettings settings) {
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
            conversionProgressDialog.dismiss();
            // TODO: 백그라운드 변환 기능 구현
        });
        
        // 취소 버튼 클릭 리스너
        progressBinding.btnCancelConversion.setOnClickListener(v -> {
            // TODO: 변환 취소 기능 구현
            conversionProgressDialog.dismiss();
        });
        
        conversionProgressDialog.show();
    }
    
    public void updateConversionProgress(int progress) {
        if (progressBinding != null) {
            progressBinding.progressBar.setProgress(progress);
            progressBinding.tvProgressPercent.setText(progress + "%");
            
            // 단계별 상태 메시지 업데이트
            if (progress < 20) {
                progressBinding.tvCurrentStep.setText("파일 분석 중...");
            } else if (progress < 50) {
                progressBinding.tvCurrentStep.setText("오디오 추출 중...");
            } else if (progress < 80) {
                progressBinding.tvCurrentStep.setText("포맷 변환 중...");
            } else if (progress < 100) {
                progressBinding.tvCurrentStep.setText("파일 저장 중...");
            } else {
                progressBinding.tvCurrentStep.setText("변환 완료!");
            }
        }
    }
    
    public void updateEstimatedTime(String timeText) {
        if (progressBinding != null) {
            progressBinding.tvEstimatedTime.setText("남은 시간: " + timeText);
        }
    }
    
    public void updateFileSizeInfo(String originalSize, String outputSize) {
        if (progressBinding != null) {
            String sizeInfo = "원본: " + originalSize + " • 출력: " + outputSize;
            progressBinding.tvFileSizeInfo.setText(sizeInfo);
        }
    }

    public void updateProgress(int progress) {
        if (progressBar != null) {
            progressBar.setProgress(progress);
        }
        // 새로운 변환 다이얼로그 업데이트
        updateConversionProgress(progress);
    }

    public void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        if (conversionProgressDialog != null && conversionProgressDialog.isShowing()) {
            conversionProgressDialog.dismiss();
        }
    }
}