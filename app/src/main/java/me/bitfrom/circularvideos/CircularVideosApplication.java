package me.bitfrom.circularvideos;

import android.app.Application;
import android.content.Context;

import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import timber.log.Timber;

public class CircularVideosApplication extends Application {

    private RefWatcher refWatcher;

    @Override
    public void onCreate() {
        super.onCreate();
        initTimber();
        initLeakCanary();
    }

    public static RefWatcher getRefWatcher(Context context) {
        CircularVideosApplication application = (CircularVideosApplication)
                context.getApplicationContext();
        return application.refWatcher;
    }

    private void initTimber() {
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
    }

    private void initLeakCanary() {
        refWatcher = LeakCanary.install(this);
    }
}