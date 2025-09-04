package com.devc.lab.audios.base;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModel;
import androidx.viewbinding.ViewBinding;
import android.os.Bundle;
// ViewPump 임시 비활성화
// import io.github.inflationx.viewpump.ViewPumpContextWrapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class BaseActivity<B extends ViewBinding> extends AppCompatActivity {

    protected B binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = getViewBinding();
        setContentView(binding.getRoot());

        setup();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        // ViewPump 임시 비활성화 - 기본 Context 사용
        super.attachBaseContext(newBase);
        // super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase));
    }

    @SuppressWarnings("unchecked")
    private B getViewBinding() {
        Type superclass = getClass().getGenericSuperclass();
        assert superclass != null;
        Class<B> clazz = (Class<B>) ((ParameterizedType) superclass).getActualTypeArguments()[0];
        try {
            Method method = clazz.getMethod("inflate", android.view.LayoutInflater.class);
            return (B) method.invoke(null, getLayoutInflater());
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("ViewBinding creation failed", e);
        }
    }

    protected abstract void setup();
}