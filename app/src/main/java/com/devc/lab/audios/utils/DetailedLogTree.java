package com.devc.lab.audios.utils;

import android.util.Log;
import androidx.annotation.NonNull;
import timber.log.Timber;

public class DetailedLogTree extends Timber.Tree {
    @Override
    protected void log(int priority, String tag, @NonNull String message, Throwable t) {
        // 스택 트레이스에서 호출 정보 가져오기
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        if (stackTrace.length > 5) {
            StackTraceElement callingElement = stackTrace[5]; // 인덱스는 호출 스택에 따라 조정 필요
            String className = callingElement.getClassName();
            String methodName = callingElement.getMethodName();
            int lineNumber = callingElement.getLineNumber();

            String formattedMessage = String.format("[%s.%s() Line:%d] %s",
                                                    className, methodName, lineNumber, message);
            // 원래의 로그 메커니즘을 사용하여 로그 출력
            Log.println(priority, tag, formattedMessage);
        }
    }
}
