package com.devc.lab.audios.base;

import android.app.Application;
// FFmpeg BuildConfig 임시 비활성화
// import com.arthenica.mobileffmpeg.BuildConfig;
// import com.devc.lab.audios.BuildConfig; // BuildConfig 작성 전에는 비활성화
import com.devc.lab.audios.R;
import com.devc.lab.audios.utils.DetailedLogTree;
import com.devc.lab.audios.utils.TypefaceUtil;
import io.github.inflationx.calligraphy3.CalligraphyConfig;
import io.github.inflationx.calligraphy3.CalligraphyInterceptor;
import io.github.inflationx.viewpump.ViewPump;
import timber.log.Timber;

public class AudiosApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        initLogger();

        initFont();
    }

    private void initLogger() {
        // BuildConfig 비활성화 때문에 임시 주석처리
        // if (BuildConfig.DEBUG) {
        //     Timber.plant(new DetailedLogTree());
        // }
        
        // 개발 중이므로 일단 디버그 모드로 로그 출력

//        Timber.plant(new Timber.DebugTree());
        Timber.plant(new DetailedLogTree());
    }

    private void initFont() {
        // 임시로 폰트 설정 비활성화 - 기본 시스템 폰트 사용
        /*
        ViewPump.init(ViewPump.builder()
                .addInterceptor(new CalligraphyInterceptor(
                        new CalligraphyConfig.Builder()
                                .setDefaultFontPath("font/cafe24.ttf")
                                .build()))
                .build());
        */
    }
}
