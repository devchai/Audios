package com.devc.lab.audios.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import com.devc.lab.audios.R;
import com.devc.lab.audios.manager.LoggerManager;
import java.util.ArrayList;
import java.util.List;

/**
 * 오디오 파형을 시각화하고 자르기 영역을 선택할 수 있는 커스텀 뷰
 */
public class WaveformView extends View {
    
    // 페인트 객체들
    private Paint waveformPaint;
    private Paint selectedAreaPaint;
    private Paint trimHandlePaint;
    private Paint centerLinePaint;
    private Paint playheadPaint;
    
    // 파형 데이터
    private float[] waveformData;
    private int waveformLength = 0;
    
    // 자르기 영역
    private float trimStartPosition = 0f;
    private float trimEndPosition = 1f;
    private boolean isDraggingStart = false;
    private boolean isDraggingEnd = false;
    
    // 재생 위치
    private float playheadPosition = 0f;
    
    // 뷰 크기
    private int viewWidth;
    private int viewHeight;
    
    // 콜백
    private OnTrimPositionChangeListener trimListener;
    
    // 핸들 크기
    private static final int HANDLE_WIDTH = 20;
    private static final int HANDLE_TOUCH_AREA = 60;
    
    public interface OnTrimPositionChangeListener {
        void onTrimStartChanged(float position);
        void onTrimEndChanged(float position);
        void onTrimPositionChanged(float start, float end);
    }
    
    public WaveformView(Context context) {
        super(context);
        init(context);
    }
    
    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public WaveformView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        // 파형 그리기 페인트
        waveformPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        waveformPaint.setColor(context.getResources().getColor(R.color.md_theme_primary, null));
        waveformPaint.setStyle(Paint.Style.FILL);
        
        // 선택 영역 페인트
        selectedAreaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedAreaPaint.setColor(context.getResources().getColor(R.color.md_theme_primary, null));
        selectedAreaPaint.setAlpha(50);
        selectedAreaPaint.setStyle(Paint.Style.FILL);
        
        // 자르기 핸들 페인트
        trimHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trimHandlePaint.setColor(context.getResources().getColor(R.color.md_theme_primary, null));
        trimHandlePaint.setStyle(Paint.Style.FILL);
        trimHandlePaint.setStrokeWidth(4f);
        
        // 중앙선 페인트
        centerLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerLinePaint.setColor(Color.GRAY);
        centerLinePaint.setAlpha(100);
        centerLinePaint.setStyle(Paint.Style.STROKE);
        centerLinePaint.setStrokeWidth(1f);
        
        // 재생 헤드 페인트
        playheadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playheadPaint.setColor(Color.RED);
        playheadPaint.setStyle(Paint.Style.STROKE);
        playheadPaint.setStrokeWidth(3f);
        
        // 샘플 파형 데이터 생성
        generateSampleWaveform();
    }
    
    /**
     * 샘플 파형 데이터 생성 (테스트용)
     */
    private void generateSampleWaveform() {
        waveformLength = 200;
        waveformData = new float[waveformLength];
        
        for (int i = 0; i < waveformLength; i++) {
            // 사인파와 랜덤값을 조합하여 실제 오디오처럼 보이는 파형 생성
            float sin = (float) Math.sin(i * 0.1) * 0.3f;
            float random = (float) (Math.random() * 0.4);
            waveformData[i] = Math.abs(sin + random);
        }
    }
    
    /**
     * 실제 오디오 데이터로 파형 설정
     */
    public void setWaveformData(float[] data) {
        this.waveformData = data;
        this.waveformLength = data != null ? data.length : 0;
        invalidate();
        
        LoggerManager.logger("파형 데이터 설정: " + waveformLength + " samples");
    }
    
    /**
     * 오디오 바이트 배열에서 파형 데이터 생성
     */
    public void setAudioBytes(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length == 0) {
            LoggerManager.logger("오디오 바이트 배열이 비어있음");
            return;
        }
        
        // 다운샘플링하여 화면에 표시할 수 있는 수준으로 축소
        int samplesPerPixel = audioBytes.length / 500; // 500개 포인트로 축소
        if (samplesPerPixel < 1) samplesPerPixel = 1;
        
        List<Float> waveform = new ArrayList<>();
        
        for (int i = 0; i < audioBytes.length; i += samplesPerPixel) {
            int sum = 0;
            int count = 0;
            
            // 구간 내 평균값 계산
            for (int j = 0; j < samplesPerPixel && (i + j) < audioBytes.length; j += 2) {
                // 16비트 오디오 샘플 변환
                short sample = (short) ((audioBytes[i + j + 1] << 8) | (audioBytes[i + j] & 0xFF));
                sum += Math.abs(sample);
                count++;
            }
            
            if (count > 0) {
                float amplitude = (float) sum / count / Short.MAX_VALUE;
                waveform.add(amplitude);
            }
        }
        
        // 배열로 변환
        waveformData = new float[waveform.size()];
        for (int i = 0; i < waveform.size(); i++) {
            waveformData[i] = waveform.get(i);
        }
        waveformLength = waveformData.length;
        
        invalidate();
        LoggerManager.logger("오디오에서 파형 생성 완료: " + waveformLength + " points");
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (viewWidth == 0 || viewHeight == 0) return;
        
        // 중앙선 그리기
        float centerY = viewHeight / 2f;
        canvas.drawLine(0, centerY, viewWidth, centerY, centerLinePaint);
        
        // 파형 그리기
        if (waveformData != null && waveformLength > 0) {
            drawWaveform(canvas);
        }
        
        // 선택 영역 표시
        drawSelectedArea(canvas);
        
        // 자르기 핸들 그리기
        drawTrimHandles(canvas);
        
        // 재생 헤드 그리기
        drawPlayhead(canvas);
    }
    
    private void drawWaveform(Canvas canvas) {
        float barWidth = (float) viewWidth / waveformLength;
        float centerY = viewHeight / 2f;
        
        for (int i = 0; i < waveformLength; i++) {
            float x = i * barWidth;
            float height = waveformData[i] * viewHeight * 0.8f; // 최대 높이의 80%
            
            // 위아래로 대칭되는 막대 그리기
            RectF rect = new RectF(
                x, 
                centerY - height / 2,
                x + barWidth * 0.8f,
                centerY + height / 2
            );
            
            canvas.drawRect(rect, waveformPaint);
        }
    }
    
    private void drawSelectedArea(Canvas canvas) {
        float startX = trimStartPosition * viewWidth;
        float endX = trimEndPosition * viewWidth;
        
        // 선택된 영역에 반투명 오버레이
        canvas.drawRect(startX, 0, endX, viewHeight, selectedAreaPaint);
    }
    
    private void drawTrimHandles(Canvas canvas) {
        float startX = trimStartPosition * viewWidth;
        float endX = trimEndPosition * viewWidth;
        
        // 시작 핸들
        drawHandle(canvas, startX, true);
        
        // 끝 핸들
        drawHandle(canvas, endX, false);
    }
    
    private void drawHandle(Canvas canvas, float x, boolean isStart) {
        // 핸들 막대
        canvas.drawLine(x, 0, x, viewHeight, trimHandlePaint);
        
        // 핸들 그립 (위아래에 작은 원)
        float radius = HANDLE_WIDTH / 2f;
        canvas.drawCircle(x, radius + 10, radius, trimHandlePaint);
        canvas.drawCircle(x, viewHeight - radius - 10, radius, trimHandlePaint);
        
        // 핸들 방향 표시 (삼각형)
        Path triangle = new Path();
        if (isStart) {
            triangle.moveTo(x, viewHeight / 2f);
            triangle.lineTo(x + 15, viewHeight / 2f - 10);
            triangle.lineTo(x + 15, viewHeight / 2f + 10);
        } else {
            triangle.moveTo(x, viewHeight / 2f);
            triangle.lineTo(x - 15, viewHeight / 2f - 10);
            triangle.lineTo(x - 15, viewHeight / 2f + 10);
        }
        triangle.close();
        canvas.drawPath(triangle, trimHandlePaint);
    }
    
    private void drawPlayhead(Canvas canvas) {
        if (playheadPosition >= 0 && playheadPosition <= 1) {
            float x = playheadPosition * viewWidth;
            canvas.drawLine(x, 0, x, viewHeight, playheadPaint);
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float position = x / viewWidth;
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                return handleTouchDown(x);
                
            case MotionEvent.ACTION_MOVE:
                return handleTouchMove(position);
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return handleTouchUp();
        }
        
        return super.onTouchEvent(event);
    }
    
    private boolean handleTouchDown(float x) {
        float startX = trimStartPosition * viewWidth;
        float endX = trimEndPosition * viewWidth;
        
        // 터치 영역 확인
        if (Math.abs(x - startX) < HANDLE_TOUCH_AREA) {
            isDraggingStart = true;
            return true;
        } else if (Math.abs(x - endX) < HANDLE_TOUCH_AREA) {
            isDraggingEnd = true;
            return true;
        }
        
        return false;
    }
    
    private boolean handleTouchMove(float position) {
        position = Math.max(0, Math.min(1, position)); // 0-1 범위로 제한
        
        if (isDraggingStart) {
            if (position < trimEndPosition - 0.05f) { // 최소 5% 간격 유지
                trimStartPosition = position;
                invalidate();
                notifyTrimPositionChanged();
            }
            return true;
        } else if (isDraggingEnd) {
            if (position > trimStartPosition + 0.05f) { // 최소 5% 간격 유지
                trimEndPosition = position;
                invalidate();
                notifyTrimPositionChanged();
            }
            return true;
        }
        
        return false;
    }
    
    private boolean handleTouchUp() {
        isDraggingStart = false;
        isDraggingEnd = false;
        return true;
    }
    
    private void notifyTrimPositionChanged() {
        if (trimListener != null) {
            if (isDraggingStart) {
                trimListener.onTrimStartChanged(trimStartPosition);
            } else if (isDraggingEnd) {
                trimListener.onTrimEndChanged(trimEndPosition);
            }
            trimListener.onTrimPositionChanged(trimStartPosition, trimEndPosition);
        }
    }
    
    // Getter & Setter 메서드들
    
    public void setTrimListener(OnTrimPositionChangeListener listener) {
        this.trimListener = listener;
    }
    
    public float getTrimStartPosition() {
        return trimStartPosition;
    }
    
    public float getTrimEndPosition() {
        return trimEndPosition;
    }
    
    public void setTrimStartPosition(float position) {
        this.trimStartPosition = Math.max(0, Math.min(position, trimEndPosition - 0.05f));
        invalidate();
    }
    
    public void setTrimEndPosition(float position) {
        this.trimEndPosition = Math.max(trimStartPosition + 0.05f, Math.min(1, position));
        invalidate();
    }
    
    public void setPlayheadPosition(float position) {
        this.playheadPosition = Math.max(0, Math.min(1, position));
        invalidate();
    }
    
    /**
     * 자르기 영역 초기화
     */
    public void resetTrimPositions() {
        trimStartPosition = 0f;
        trimEndPosition = 1f;
        invalidate();
        
        if (trimListener != null) {
            trimListener.onTrimPositionChanged(trimStartPosition, trimEndPosition);
        }
    }
}