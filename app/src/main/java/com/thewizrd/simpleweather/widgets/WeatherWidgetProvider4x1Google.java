package com.thewizrd.simpleweather.widgets;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

import com.thewizrd.simpleweather.App;
import com.thewizrd.simpleweather.R;

import static com.thewizrd.simpleweather.widgets.WidgetType.Widget4x1Google;

public class WeatherWidgetProvider4x1Google extends WeatherWidgetProvider {
    private static WeatherWidgetProvider4x1Google sInstance;

    public static synchronized WeatherWidgetProvider4x1Google getInstance() {
        if (sInstance == null)
            sInstance = new WeatherWidgetProvider4x1Google();

        return sInstance;
    }

    // Overrides
    @Override
    public WidgetType getWidgetType() {
        return Widget4x1Google;
    }

    @Override
    public int getWidgetLayoutId() {
        return R.layout.app_widget_4x1_google;
    }

    @Override
    protected String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public ComponentName getComponentName() {
        return new ComponentName(App.getInstance().getAppContext(), getClassName());
    }

    @Override
    public boolean hasInstances(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new ComponentName(context, getClassName()));
        return (appWidgetIds.length > 0);
    }
}
