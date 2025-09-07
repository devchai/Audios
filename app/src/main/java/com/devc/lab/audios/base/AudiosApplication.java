package com.devc.lab.audios.base;

import android.app.Application;
// FFmpeg BuildConfig 임시 비활성화
// import com.arthenica.mobileffmpeg.BuildConfig;
// import com.devc.lab.audios.BuildConfig; // BuildConfig 작성 전에는 비활성화
import com.devc.lab.audios.R;
import com.devc.lab.audios.manager.NativeMediaInfoManager;
import com.devc.lab.audios.manager.NativeAudioExtractorManager;
import com.devc.lab.audios.manager.NativeAudioTrimManager;
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
        
        initNativeMediaManager();
        
        initNativeAudioExtractor();
        
        initNativeAudioTrimManager();

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

    private void initNativeMediaManager() {
        // Native 미디어 정보 관리자 초기화
        NativeMediaInfoManager.getInstance().init(this);
        Timber.d("NativeMediaInfoManager 초기화 완료");
    }
    
    private void initNativeAudioExtractor() {
        // Phase 2: Native 오디오 추출 관리자 초기화
        NativeAudioExtractorManager.getInstance().init(this);
        Timber.d("NativeAudioExtractorManager 초기화 완료");
    }
    
    private void initNativeAudioTrimManager() {
        // Phase 3: Native 오디오 자르기 관리자 초기화
        NativeAudioTrimManager.getInstance().init(this);
        Timber.d("NativeAudioTrimManager 초기화 완료");
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
