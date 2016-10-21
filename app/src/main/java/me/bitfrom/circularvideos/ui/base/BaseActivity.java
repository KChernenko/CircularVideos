package me.bitfrom.circularvideos.ui.base;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.squareup.leakcanary.RefWatcher;

import me.bitfrom.circularvideos.CircularVideosApplication;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RefWatcher refWatcher = CircularVideosApplication.getRefWatcher(this);
        refWatcher.watch(this);
    }
}