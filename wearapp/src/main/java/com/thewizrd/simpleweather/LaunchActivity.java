package com.thewizrd.simpleweather;

import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;

import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.shared_resources.utils.Settings;

public class LaunchActivity extends WearableActivity {

    private static final String TAG = "LaunchActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.WearAppTheme);
        super.onCreate(savedInstanceState);

        Intent intent = null;

        try {
            if (Settings.isWeatherLoaded()) {
                intent = new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            } else {
                intent = new Intent(this, SetupActivity.class);
            }
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e, "SimpleWeather: %s: error loading", TAG);
        } finally {
            if (intent == null) {
                intent = new Intent(this, SetupActivity.class);
            }

            // Navigate
            startActivity(intent);
            finishAffinity();
        }
    }
}
