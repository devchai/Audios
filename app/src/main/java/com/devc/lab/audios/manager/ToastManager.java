package com.devc.lab.audios.manager;

import android.content.Context;
import android.widget.Toast;

public class ToastManager {

    private Context context;

    public ToastManager(Context context) {
        this.context = context;
    }

    public void showToastShort(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public void showToastLong(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    // 필요에 따라 추가적인 메소드 정의 가능
}
