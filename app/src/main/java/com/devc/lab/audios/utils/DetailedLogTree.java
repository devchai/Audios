package com.devc.lab.audios.utils;

import android.util.Log;
import androidx.annotation.NonNull;
import timber.log.Timber;
import java.util.Locale;

public class DetailedLogTree extends Timber.Tree {
    @Override
    protected void log(int priority, String tag, @NonNull String message, Throwable t) {
        // 스택 트레이스에서 호출 정보 가져오기
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        if (stackTrace.length > 5) {
            StackTraceElement callingElement = stackTrace[5]; // 인덱스는 호출 스택에 따라 조정 필요
            String fullClassName = callingElement.getClassName();
            String methodName = callingElement.getMethodName();
            int lineNumber = callingElement.getLineNumber();

            // 전체 패키지 경로에서 클래스명만 추출
            String className = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);

            // 새로운 형식: "클래스명-메소드(라인) \t 메시지" (탭으로 정렬)
            // Android Studio Logcat에서 탭을 사용하여 메시지 부분을 깔끔하게 정렬
            String formattedMessage = String.format(Locale.US, "%s-%s(%d) : %s",
                                                    className, methodName, lineNumber, message);
            // 원래의 로그 메커니즘을 사용하여 로그 출력
            Log.println(priority, tag, formattedMessage);
        }
    }
}
