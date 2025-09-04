package com.devc.lab.audios.activity;

import android.animation.Animator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import com.daimajia.androidanimations.library.Techniques;
import com.daimajia.androidanimations.library.YoYo;
import com.devc.lab.audios.base.BaseActivity;
import com.devc.lab.audios.databinding.ActivitySplashBinding;

public class SplashActivity extends BaseActivity<ActivitySplashBinding> {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        

        startAnimation();
    }

    @Override
    protected void setup() {

    }

    private void startAnimation() {
        YoYo.with(Techniques.FadeIn)
                .duration(1000) // 1초 동안 fadeIn
                .onEnd(fadeInAnimator -> {
                    // FadeIn 애니메이션 종료 후 FadeOut 시작
                    YoYo.with(Techniques.FadeOut)
                            .duration(1000) // 1초 동안 fadeOut
                            .onEnd(fadeOutAnimator -> {
                                // FadeOut 애니메이션 종료 후 onFinishAnimation 호출
                                onFinishAnimation();
                            })
                            .playOn(binding.ivTitleLogo);
                })
                .playOn(binding.ivTitleLogo);
    }

    private void onFinishAnimation() {
        // 500ms 후에 MainActivity로 전환
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish(); // SplashActivity 종료
        }, 500);
    }
}