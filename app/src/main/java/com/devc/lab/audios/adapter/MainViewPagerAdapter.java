package com.devc.lab.audios.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.devc.lab.audios.fragment.ConvertFragment;
import com.devc.lab.audios.fragment.LibraryFragment;
import com.devc.lab.audios.fragment.EditFragment;

public class MainViewPagerAdapter extends FragmentStateAdapter {
    
    public static final int TAB_CONVERT = 0;
    public static final int TAB_LIBRARY = 1;
    public static final int TAB_EDIT = 2;
    public static final int TAB_COUNT = 3;
    
    public MainViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }
    
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case TAB_CONVERT:
                return ConvertFragment.newInstance();
            case TAB_LIBRARY:
                return LibraryFragment.newInstance();
            case TAB_EDIT:
                return EditFragment.newInstance();
            default:
                return ConvertFragment.newInstance();
        }
    }
    
    @Override
    public int getItemCount() {
        return TAB_COUNT;
    }
}