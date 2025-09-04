package com.devc.lab.audios.activity;

import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;
// import com.arthenica.mobileffmpeg.FFmpeg;  // 임시 비활성화
import com.devc.lab.audios.R;
import com.devc.lab.audios.adapter.MainViewPagerAdapter;
import com.devc.lab.audios.ads.AdConstants;
import com.devc.lab.audios.ads.SubAdlibAdViewAdmob;
import com.devc.lab.audios.base.BaseActivity;
import com.devc.lab.audios.databinding.ActivityMainBinding;
import com.devc.lab.audios.manager.*;
import com.devc.lab.audios.model.MainViewModel;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.material.tabs.TabLayoutMediator;
import androidx.lifecycle.ViewModelProvider;
// import com.mocoplex.adlib.AdlibConfig;
// import com.mocoplex.adlib.AdlibManager;
import io.github.inflationx.viewpump.ViewPumpContextWrapper;

import java.io.*;

public class MainActivity extends BaseActivity<ActivityMainBinding> {
    
    // 권한 요청 완료 콜백 인터페이스
    public interface PermissionRequestCallback {
        void onPermissionRequestCompleted();
    }
    
    // ViewModel - MVVM 아키텍처의 중심
    private MainViewModel viewModel;
    
    // UI 관련 Manager들 (ViewModel이 비즈니스 로직 Manager들을 관리)
    private DialogManager dialogManager;
    private ToastManager toastManager;
    
    // 권한 요청 중복 방지를 위한 플래그
    private boolean isPermissionRequestInProgress = false;
    private boolean isInitialPermissionCheckDone = false;

    // private AdlibManager adlibManager;  // 임시 비활성화


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LoggerManager.logger("Call MainActivity");
        
        // ViewModel 및 Observer를 onCreate에서 바로 초기화
        initViewModel();
        initManagers();

        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
            }
        });

        
        // initAdlib();  // 임시 비활성화
    }

    /* 임시 비활성화
    private void initAdlib() {
        // adlibr.com 에서 발급받은 api 키를 입력합니다.
        // ADLIB - API - KEY 설정
        // 각 애드립 액티비티에 애드립 앱 키값을 필수로 넣어주어야 합니다.
        adlibManager = new AdlibManager(AdConstants.adlib_key);
        adlibManager.onCreate(this);
        // 테스트 광고 노출로, 상용일 경우 꼭 제거해야 합니다.
        adlibManager.setAdlibTestMode(AdConstants.adlib_test_mode);

        // 미디에이션 스케쥴 관련 설정
        bindPlatform();

        // 이벤트 핸들러 등록
        adlibManager.setAdsHandler(new Handler() {
            public void handleMessage(Message message) {
                try {
                    switch (message.what) {
                        case AdlibManager.DID_SUCCEED:

                            LoggerManager.logger("[Banner] onReceiveAd " + (String) message.obj);
                            break;
                        case AdlibManager.DID_ERROR:
                            LoggerManager.logger("[Banner] onFailedToReceiveAd " + (String) message.obj);
                            break;
                        case AdlibManager.BANNER_FAILED:
                            LoggerManager.logger("[Banner] All Failed.");
                            break;
                    }
                } catch (Exception e) {

                }
            }
        });

        adlibManager.setAdsContainer(R.id.ads);
    }
    */

    protected void onResume() {
        // adlibManager.onResume(this);  // 임시 비활성화
        super.onResume();
    }

    protected void onPause() {
        // adlibManager.onPause(this);  // 임시 비활성화
        super.onPause();
    }

    protected void onDestroy() {
        // adlibManager.onDestroy(this);  // 임시 비활성화
        super.onDestroy();
    }

    /* 임시 비활성화
    private void bindPlatform() {
        AdlibConfig.getInstance().bindPlatform("ADMOB", "com.devc.lab.audios.ads.SubAdlibAdViewAdmob");
    }
    */

    @Override
    protected void setup() {
        setupViewPager();
        setupTabLayout();
    }
    
    private void setupViewPager() {
        MainViewPagerAdapter adapter = new MainViewPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);
    }
    
    private void setupTabLayout() {
        String[] tabTitles = {
            getString(R.string.tab_convert),
            getString(R.string.tab_library),
            getString(R.string.tab_edit)
        };
        
        new TabLayoutMediator(binding.tabLayout, binding.viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        ).attach();
    }

    /**
     * UI 전용 Manager들 초기화 (ViewModel과 별개)
     */
    private void initManagers() {
        initDialogManager();
        initToastManager();
    }
    
    /**
     * ViewModel 초기화 및 LiveData 관찰 설정
     * onCreate에서 호출되어 CREATED 상태에서 observer 등록
     */
    private void initViewModel() {
        // ViewModel 인스턴스 생성
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        
        // LiveData 관찰 설정 (onCreate에서 바로 실행)
        observeViewModelData();
        
        // ViewModel과 Activity 연결 (observer 등록 후 실행)
        viewModel.initializeWithActivity(this);
        
        LoggerManager.logger("MainActivity - ViewModel 초기화 완료");
    }
    
    /**
     * ViewModel LiveData 관찰 설정
     */
    private void observeViewModelData() {
        // 상태 메시지 관찰
        viewModel.getStatusMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                LoggerManager.logger("Status: " + message);
                // 필요시 UI에 상태 표시
            }
        });
        
        // 에러 메시지 관찰
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                toastManager.showToastLong(error);
                LoggerManager.logger("Error: " + error);
            }
        });
        
        // 변환 진행률 관찰
        viewModel.getConversionProgress().observe(this, progress -> {
            if (progress != null) {
                dialogManager.updateProgress(progress);
            }
        });
        
        // 변환 상태 관찰
        viewModel.getIsConverting().observe(this, isConverting -> {
            if (isConverting != null) {
                if (isConverting) {
                    dialogManager.showProgressDialog();
                } else {
                    dialogManager.dismissProgressDialog();
                }
            }
        });
        
        // 변환 완료 파일 관찰
        viewModel.getConvertedFilePath().observe(this, filePath -> {
            if (filePath != null && !filePath.isEmpty()) {
                String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
                dialogManager.showMessageDialog(
                    getString(R.string.app_name), 
                    "변환 완료!\n파일: " + fileName, 
                    (dialog, which) -> {
                        // 추가 액션이 필요하면 여기에 구현
                    }
                );
            }
        });
        
        // 권한 상태 관찰 - 중복 요청 방지 로직 추가
        viewModel.getHasRequiredPermissions().observe(this, hasPermissions -> {
            if (hasPermissions != null && !hasPermissions) {
                // 초기 권한 확인 시에만 자동 요청하고, 이미 요청 진행 중이면 무시
                if (!isInitialPermissionCheckDone && !isPermissionRequestInProgress) {
                    isPermissionRequestInProgress = true;
                    isInitialPermissionCheckDone = true;
                    
                    // 권한 요청 시작
                    viewModel.requestPermissions(new PermissionRequestCallback() {
                        @Override
                        public void onPermissionRequestCompleted() {
                            isPermissionRequestInProgress = false;
                        }
                    });
                }
            } else if (hasPermissions != null && hasPermissions) {
                // 권한이 허용되면 플래그 리셋
                isPermissionRequestInProgress = false;
            }
        });
        
        // 선택된 파일 관찰
        viewModel.getSelectedFileName().observe(this, fileName -> {
            if (fileName != null && !fileName.isEmpty()) {
                LoggerManager.logger("파일 선택됨: " + fileName);
            }
        });
        
        // 플레이어 상태 관찰  
        viewModel.getPlaybackState().observe(this, playbackState -> {
            if (playbackState != null) {
                LoggerManager.logger("플레이어 상태: " + playbackState.name());
            }
        });
    }

    private void initToastManager() {
        toastManager = new ToastManager(this);
    }

    private void initDialogManager() {
        dialogManager = new DialogManager(this);
    }


    /////////////////////////////////////////////////////////////////////
    // 공개 메서드들 (Fragment에서 ViewModel 접근용)
    /////////////////////////////////////////////////////////////////////
    
    /**
     * Fragment에서 ViewModel 접근을 위한 Getter
     */
    public MainViewModel getMainViewModel() {
        return viewModel;
    }
    
    /**
     * Fragment에서 DialogManager 접근을 위한 Getter
     */
    public DialogManager getDialogManager() {
        return dialogManager;
    }
    
    /**
     * Fragment에서 ToastManager 접근을 위한 Getter
     */
    public ToastManager getToastManager() {
        return toastManager;
    }
    
    // Note: 기존의 onRequestPermissionsResult와 FFmpeg 콜백들은
    // ViewModel에서 ActivityResultLauncher와 콜백 시스템으로 대체되었습니다.
    // Fragment들은 getMainViewModel()을 통해 ViewModel에 접근할 수 있습니다.
}