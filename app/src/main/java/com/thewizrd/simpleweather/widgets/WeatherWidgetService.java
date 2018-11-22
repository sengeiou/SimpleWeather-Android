package com.thewizrd.simpleweather.widgets;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.AlarmClock;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.support.v4.content.ContextCompat;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.thewizrd.shared_resources.AsyncTask;
import com.thewizrd.shared_resources.AsyncTaskEx;
import com.thewizrd.shared_resources.CallableEx;
import com.thewizrd.shared_resources.controls.LocationQueryViewModel;
import com.thewizrd.shared_resources.utils.ConversionMethods;
import com.thewizrd.shared_resources.utils.ImageUtils;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.shared_resources.utils.Settings;
import com.thewizrd.shared_resources.utils.StringUtils;
import com.thewizrd.shared_resources.weatherdata.Forecast;
import com.thewizrd.shared_resources.weatherdata.LocationData;
import com.thewizrd.shared_resources.weatherdata.Weather;
import com.thewizrd.shared_resources.weatherdata.WeatherAPI;
import com.thewizrd.shared_resources.weatherdata.WeatherDataLoader;
import com.thewizrd.shared_resources.weatherdata.WeatherManager;
import com.thewizrd.simpleweather.App;
import com.thewizrd.simpleweather.MainActivity;
import com.thewizrd.simpleweather.R;
import com.thewizrd.simpleweather.notifications.WeatherNotificationBuilder;
import com.thewizrd.simpleweather.wearable.WearableDataListenerService;
import com.thewizrd.simpleweather.weatheralerts.WeatherAlertHandler;

import org.threeten.bp.Duration;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class WeatherWidgetService extends JobIntentService {
    private static String TAG = "WeatherWidgetService";

    public static final String ACTION_REFRESHWIDGET = "SimpleWeather.Droid.action.REFRESH_WIDGET";
    public static final String ACTION_RESIZEWIDGET = "SimpleWeather.Droid.action.RESIZE_WIDGET";
    public static final String ACTION_UPDATECLOCK = "SimpleWeather.Droid.action.UPDATE_CLOCK";
    public static final String ACTION_UPDATEDATE = "SimpleWeather.Droid.action.UPDATE_DATE";
    public static final String ACTION_UPDATEWEATHER = "SimpleWeather.Droid.action.UPDATE_WEATHER";

    public static final String ACTION_STARTALARM = "SimpleWeather.Droid.action.START_ALARM";
    public static final String ACTION_CANCELALARM = "SimpleWeather.Droid.action.CANCEL_ALARM";
    public static final String ACTION_UPDATEALARM = "SimpleWeather.Droid.action.UPDATE_ALARM";

    public static final String ACTION_REFRESHNOTIFICATION = "SimpleWeather.Droid.action.REFRESH_NOTIFICATION";
    public static final String ACTION_REMOVENOTIFICATION = "SimpleWeather.Droid.action.REMOVE_NOTIFICATION";

    public static final String ACTION_STARTCLOCK = "SimpleWeather.Droid.action.START_CLOCKALARM";
    public static final String ACTION_CANCELCLOCK = "SimpleWeather.Droid.action.CANCEL_CLOCKALARM";

    public static final String ACTION_SHOWALERTS = "SimpleWeather.Droid.action.SHOW_ALERTS";

    private static final int JOB_ID = 1000;

    private Context mContext;
    private AppWidgetManager mAppWidgetManager;
    private static boolean mAlarmStarted = false;
    private static BroadcastReceiver mTickReceiver;

    private WeatherManager wm;

    // Weather Widget Providers
    private WeatherWidgetProvider1x1 mAppWidget1x1 =
            WeatherWidgetProvider1x1.getInstance();
    private WeatherWidgetProvider2x2 mAppWidget2x2 =
            WeatherWidgetProvider2x2.getInstance();
    private WeatherWidgetProvider4x1 mAppWidget4x1 =
            WeatherWidgetProvider4x1.getInstance();
    private WeatherWidgetProvider4x2 mAppWidget4x2 =
            WeatherWidgetProvider4x2.getInstance();

    private static final int FORECAST_LENGTH = 3; // 3-day
    private static final int MEDIUM_FORECAST_LENGTH = 4; // 4-day
    private static final int WIDE_FORECAST_LENGTH = 5; // 5-day

    private CancellationTokenSource cts;

    public static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, WeatherWidgetService.class,
                JOB_ID, work);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mContext = getApplicationContext();
        mAppWidgetManager = AppWidgetManager.getInstance(mContext);
        wm = WeatherManager.getInstance();

        final Thread.UncaughtExceptionHandler oldHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                Logger.writeLine(Log.ERROR, e, "%s: Unhandled Exception %s", TAG, e == null ? null : e.getMessage());

                if (oldHandler != null) {
                    oldHandler.uncaughtException(t, e);
                } else {
                    System.exit(2);
                }
            }
        });

        cts = new CancellationTokenSource();
    }

    @Override
    public void onDestroy() {
        if (Settings.showOngoingNotification() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            WeatherNotificationBuilder.showRefresh(false);

        Logger.writeLine(Log.INFO, "%s: destroying service and cancelling tasks...", TAG);

        try {
            if (cts != null) cts.cancel();
        } catch (Exception ex) {
            Logger.writeLine(Log.ERROR, ex, "%s: Error cancelling task...", TAG);
        }

        super.onDestroy();
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        try {
            if (ACTION_REFRESHWIDGET.equals(intent.getAction())) {
                final int[] appWidgetIds = intent.getIntArrayExtra(WeatherWidgetProvider.EXTRA_WIDGET_IDS);
                WidgetType widgetType = WidgetType.valueOf(intent.getIntExtra(WeatherWidgetProvider.EXTRA_WIDGET_TYPE, -1));

                switch (widgetType) {
                    case Widget1x1:
                        new AsyncTask<Void>().await(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                refreshWidget(mAppWidget1x1, appWidgetIds);
                                return null;
                            }
                        });
                        break;
                    case Widget2x2:
                        new AsyncTask<Void>().await(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                refreshWidget(mAppWidget2x2, appWidgetIds);
                                return null;
                            }
                        });
                        break;
                    case Widget4x1:
                        new AsyncTask<Void>().await(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                refreshWidget(mAppWidget4x1, appWidgetIds);
                                return null;
                            }
                        });
                        break;
                    case Widget4x2:
                        new AsyncTask<Void>().await(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                refreshWidget(mAppWidget4x2, appWidgetIds);
                                return null;
                            }
                        });
                        break;
                    // We don't know the widget type to update,
                    // so just update all
                    case Unknown:
                    default:
                        new AsyncTask<Void>().await(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                refreshWidgets();
                                return null;
                            }
                        });
                        break;
                }
            } else if (ACTION_RESIZEWIDGET.equals(intent.getAction())) {
                final int appWidgetId = intent.getIntExtra(WeatherWidgetProvider.EXTRA_WIDGET_ID, -1);
                WidgetType widgetType = WidgetType.valueOf(intent.getIntExtra(WeatherWidgetProvider.EXTRA_WIDGET_TYPE, -1));
                final Bundle newOptions = intent.getBundleExtra(WeatherWidgetProvider.EXTRA_WIDGET_OPTIONS);

                switch (widgetType) {
                    case Widget1x1:
                    default:
                        // Widget resizes itself; no need to adjust
                        break;
                    case Widget2x2:
                        new AsyncTask<Void>().await(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                resizeWidget(mAppWidget2x2, appWidgetId, newOptions);
                                return null;
                            }
                        });
                        break;
                    case Widget4x1:
                        new AsyncTask<Void>().await(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                resizeWidget(mAppWidget4x1, appWidgetId, newOptions);
                                return null;
                            }
                        });
                        break;
                    case Widget4x2:
                        new AsyncTask<Void>().await(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                resizeWidget(mAppWidget4x2, appWidgetId, newOptions);
                                return null;
                            }
                        });
                        break;
                }
            } else if (ACTION_STARTALARM.equals(intent.getAction())) {
                // Start alarm if it hasn't started already
                startAlarm(mContext);
            } else if (ACTION_CANCELALARM.equals(intent.getAction())) {
                // Cancel all alarms if no widgets exist
                cancelAlarms(mContext);
            } else if (ACTION_UPDATEALARM.equals(intent.getAction())) {
                // Refresh interval was changed
                // Update alarm
                updateAlarm(mContext);
            } else if (ACTION_STARTCLOCK.equals(intent.getAction())) {
                // Schedule clock updates
                startTickReceiver(mContext);
            } else if (ACTION_CANCELCLOCK.equals(intent.getAction())) {
                // Cancel clock alarm
                cancelClockAlarm(mContext);
            } else if (ACTION_UPDATECLOCK.equals(intent.getAction())) {
                // Update clock widget instances
                int[] appWidgetIds = intent.getIntArrayExtra(WeatherWidgetProvider.EXTRA_WIDGET_IDS);
                refreshClock(appWidgetIds);
            } else if (ACTION_UPDATEDATE.equals(intent.getAction())) {
                // Update clock widget instances
                int[] appWidgetIds = intent.getIntArrayExtra(WeatherWidgetProvider.EXTRA_WIDGET_IDS);
                refreshDate(appWidgetIds);
            } else if (ACTION_REFRESHNOTIFICATION.equals(intent.getAction())) {
                if (Settings.isWeatherLoaded()) {
                    Weather weather = new AsyncTask<Weather>().await(new Callable<Weather>() {
                        @Override
                        public Weather call() throws Exception {
                            return getWeather();
                        }
                    });

                    if (Settings.showOngoingNotification() && weather != null)
                        WeatherNotificationBuilder.updateNotification(weather);
                }
            } else if (ACTION_REMOVENOTIFICATION.equals(intent.getAction())) {
                WeatherNotificationBuilder.removeNotification();
            } else if (ACTION_UPDATEWEATHER.equals(intent.getAction())) {
                if (Settings.isWeatherLoaded()) {
                    // Send broadcast to signal update
                    if (widgetsExist(App.getInstance().getAppContext()))
                        sendBroadcast(new Intent(WeatherWidgetProvider.ACTION_SHOWREFRESH));
                    // NOTE: Don't try to show refresh for pre-M devices
                    // If app gets killed, instance of notif is lost & view is reset
                    // and might get stuck
                    if (Settings.showOngoingNotification() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        WeatherNotificationBuilder.showRefresh(true);

                    if (widgetsExist(App.getInstance().getAppContext()))
                        new AsyncTask<Void>().await(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                refreshWidgets();
                                return null;
                            }
                        });

                    // Update for home
                    final Weather weather = new AsyncTask<Weather>().await(new Callable<Weather>() {
                        @Override
                        public Weather call() throws Exception {
                            return getWeather();
                        }
                    });
                    if (weather != null) {
                        if (Settings.showOngoingNotification())
                            WeatherNotificationBuilder.updateNotification(weather);
                        if (Settings.useAlerts() && wm.supportsAlerts() && weather != null) {
                            AsyncTask.run(new Runnable() {
                                @Override
                                public void run() {
                                    WeatherAlertHandler.postAlerts(Settings.getHomeData(), weather.getWeatherAlerts());
                                }
                            });
                        }
                    }
                }
            }

            Logger.writeLine(Log.INFO, "%s: Intent Action = %s", TAG, intent.getAction());
        } catch (Exception ex) {
            Logger.writeLine(Log.ERROR, ex, "%s: exception occurred...", TAG);
        }
    }

    private static PendingIntent getAlarmIntent(Context context) {
        Intent intent = new Intent(context, WeatherWidgetBroadcastReceiver.class)
                .setAction(ACTION_UPDATEWEATHER);

        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    private static void updateAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        int interval = Settings.getRefreshInterval();

        boolean startNow = !mAlarmStarted;
        long intervalMillis = Duration.ofMinutes(interval).toMillis();
        long triggerAtTime = SystemClock.elapsedRealtime() + intervalMillis;

        if (startNow) {
            enqueueWork(context, new Intent(context, WeatherWidgetService.class)
                    .setAction(ACTION_UPDATEWEATHER));
        }

        PendingIntent pendingIntent = getAlarmIntent(context);
        am.cancel(pendingIntent);
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, triggerAtTime, intervalMillis, pendingIntent);
        mAlarmStarted = true;

        Logger.writeLine(Log.INFO, "%s: Updated alarm", TAG);
    }

    private void cancelAlarms(Context context) {
        // Cancel alarm if dependent features are turned off
        if (!widgetsExist(context) && !Settings.showOngoingNotification() && !Settings.useAlerts()) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            am.cancel(getAlarmIntent(context));
            mAlarmStarted = false;

            Logger.writeLine(Log.INFO, "%s: Canceled alarm", TAG);
        }
    }

    private void startAlarm(Context context) {
        // Start alarm if dependent features are enabled
        if (!mAlarmStarted && (widgetsExist(context) || Settings.showOngoingNotification() || Settings.useAlerts())) {
            updateAlarm(context);
            mAlarmStarted = true;
        }
    }

    private static PendingIntent getClockRefreshIntent(Context context) {
        Intent intent = new Intent(context, WeatherWidgetBroadcastReceiver.class)
                .setAction(ACTION_UPDATECLOCK);

        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    private static void startTickReceiver(Context context) {
        stopTickReceiver(context);

        mTickReceiver = new TickReceiver();
        context.registerReceiver(mTickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

        Logger.writeLine(Log.INFO, "%s: Started tick receiver", TAG);
    }

    private static void stopTickReceiver(Context context) {
        if (mTickReceiver != null) {
            context.unregisterReceiver(mTickReceiver);
            mTickReceiver = null;

            Logger.writeLine(Log.INFO, "%s: Unregistered tick receiver", TAG);
        }
    }

    private static void cancelClockAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(getClockRefreshIntent(context));

        Logger.writeLine(Log.INFO, "%s: Canceled clock alarm", TAG);
    }

    static class TickReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && Intent.ACTION_TIME_TICK.equals(intent.getAction())) {
                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                PendingIntent pendingIntent = getClockRefreshIntent(context);
                am.cancel(pendingIntent);
                am.setRepeating(AlarmManager.RTC, System.currentTimeMillis(), 60000, pendingIntent);

                stopTickReceiver(context);

                Logger.writeLine(Log.INFO, "%s: Receieved tick in receiver", TAG);
            }
        }
    }

    private boolean widgetsExist(Context context) {
        return mAppWidget1x1.hasInstances(context) || mAppWidget2x2.hasInstances(context) || mAppWidget4x1.hasInstances(context) || mAppWidget4x2.hasInstances(context);
    }

    private void resizeWidget(WeatherWidgetProvider provider, int appWidgetId, Bundle newOptions) throws InterruptedException {
        if (Settings.isWeatherLoaded())
            rebuildForecast(provider, appWidgetId, newOptions);
    }

    private void refreshWidget(final WeatherWidgetProvider provider, int[] appWidgetIds) throws InterruptedException {
        if (appWidgetIds == null || appWidgetIds.length == 0)
            appWidgetIds = mAppWidgetManager.getAppWidgetIds(provider.getComponentName());

        final int[] finalAppWidgetIds = appWidgetIds;
        new AsyncTaskEx<Void, InterruptedException>().await(new CallableEx<Void, InterruptedException>() {
            @Override
            public Void call() throws InterruptedException {
                if (Settings.isWeatherLoaded()) {
                    for (int id : finalAppWidgetIds) {
                        LocationData locData = WidgetUtils.getLocationData(id);

                        if (cts.getToken().isCancellationRequested())
                            throw new InterruptedException();

                        Weather weather = null;

                        if (locData != null) {
                            if (cts.getToken().isCancellationRequested())
                                throw new InterruptedException();

                            if (locData.equals(Settings.getHomeData())) {
                                weather = getWeather();
                            } else {
                                WeatherDataLoader wloader = new WeatherDataLoader(locData);
                                wloader.loadWeatherData(false);
                                weather = wloader.getWeather();
                            }

                            if (weather != null) {
                                // Save weather data
                                WidgetUtils.saveWeatherData(id, weather);

                                // Build the widget update for provider
                                RemoteViews views = buildUpdate(mContext, provider, id, locData, weather);
                                // Push update for this widget to the home screen
                                mAppWidgetManager.updateAppWidget(id, views);
                                buildForecast(provider, weather, id);
                            }
                        }
                    }
                }

                if (provider.getWidgetType() == WidgetType.Widget4x2) {
                    refreshClock(finalAppWidgetIds);
                    refreshDate(finalAppWidgetIds);
                }
                return null;
            }
        });
    }

    private void refreshWidgets() throws InterruptedException {
        new AsyncTaskEx<Void, InterruptedException>().await(new CallableEx<Void, InterruptedException>() {
            @Override
            public Void call() throws InterruptedException {
                if (Settings.isWeatherLoaded()) {
                    // Build the widget update for available providers
                    // Add widget providers here
                    if (mAppWidget1x1.hasInstances(WeatherWidgetService.this)) {
                        int[] appWidgetIds = mAppWidgetManager.getAppWidgetIds(mAppWidget1x1.getComponentName());

                        for (int id : appWidgetIds) {
                            LocationData locData = WidgetUtils.getLocationData(id);

                            if (cts.getToken().isCancellationRequested())
                                throw new InterruptedException();

                            Weather weather = null;

                            if (locData != null) {
                                if (cts.getToken().isCancellationRequested())
                                    throw new InterruptedException();

                                if (locData.equals(Settings.getHomeData())) {
                                    weather = getWeather();
                                } else {
                                    WeatherDataLoader wloader = new WeatherDataLoader(locData);
                                    wloader.loadWeatherData(false);
                                    weather = wloader.getWeather();
                                }

                                if (weather != null) {
                                    // Save weather data
                                    WidgetUtils.saveWeatherData(id, weather);

                                    // Build the widget update for provider
                                    RemoteViews views = buildUpdate(mContext, mAppWidget1x1, id, locData, weather);
                                    // Push update for this widget to the home screen
                                    mAppWidgetManager.updateAppWidget(id, views);
                                    buildForecast(mAppWidget1x1, weather, id);
                                }
                            }
                        }
                    }

                    if (mAppWidget2x2.hasInstances(WeatherWidgetService.this)) {
                        int[] appWidgetIds = mAppWidgetManager.getAppWidgetIds(mAppWidget2x2.getComponentName());

                        for (int id : appWidgetIds) {
                            LocationData locData = WidgetUtils.getLocationData(id);

                            if (cts.getToken().isCancellationRequested())
                                throw new InterruptedException();

                            Weather weather = null;

                            if (locData != null) {
                                if (cts.getToken().isCancellationRequested())
                                    throw new InterruptedException();

                                if (locData.equals(Settings.getHomeData())) {
                                    weather = getWeather();
                                } else {
                                    WeatherDataLoader wloader = new WeatherDataLoader(locData);
                                    wloader.loadWeatherData(false);
                                    weather = wloader.getWeather();
                                }

                                if (weather != null) {
                                    // Save weather data
                                    WidgetUtils.saveWeatherData(id, weather);

                                    // Build the widget update for provider
                                    RemoteViews views = buildUpdate(mContext, mAppWidget2x2, id, locData, weather);
                                    // Push update for this widget to the home screen
                                    mAppWidgetManager.updateAppWidget(id, views);
                                    buildForecast(mAppWidget2x2, weather, id);
                                }
                            }
                        }
                    }

                    if (mAppWidget4x1.hasInstances(WeatherWidgetService.this)) {
                        int[] appWidgetIds = mAppWidgetManager.getAppWidgetIds(mAppWidget4x1.getComponentName());

                        for (int id : appWidgetIds) {
                            LocationData locData = WidgetUtils.getLocationData(id);

                            if (cts.getToken().isCancellationRequested())
                                throw new InterruptedException();

                            Weather weather = null;

                            if (locData != null) {
                                if (cts.getToken().isCancellationRequested())
                                    throw new InterruptedException();

                                if (locData.equals(Settings.getHomeData())) {
                                    weather = getWeather();
                                } else {
                                    WeatherDataLoader wloader = new WeatherDataLoader(locData);
                                    wloader.loadWeatherData(false);
                                    weather = wloader.getWeather();
                                }

                                if (weather != null) {
                                    // Save weather data
                                    WidgetUtils.saveWeatherData(id, weather);

                                    // Build the widget update for provider
                                    RemoteViews views = buildUpdate(mContext, mAppWidget4x1, id, locData, weather);
                                    // Push update for this widget to the home screen
                                    mAppWidgetManager.updateAppWidget(id, views);
                                    buildForecast(mAppWidget4x1, weather, id);
                                }
                            }
                        }
                    }

                    if (mAppWidget4x2.hasInstances(WeatherWidgetService.this)) {
                        int[] appWidgetIds = mAppWidgetManager.getAppWidgetIds(mAppWidget4x2.getComponentName());

                        for (int id : appWidgetIds) {
                            LocationData locData = WidgetUtils.getLocationData(id);

                            if (cts.getToken().isCancellationRequested())
                                throw new InterruptedException();

                            Weather weather = null;

                            if (locData != null) {
                                if (cts.getToken().isCancellationRequested())
                                    throw new InterruptedException();

                                if (locData.equals(Settings.getHomeData())) {
                                    weather = getWeather();
                                } else {
                                    WeatherDataLoader wloader = new WeatherDataLoader(locData);
                                    wloader.loadWeatherData(false);
                                    weather = wloader.getWeather();
                                }

                                if (weather != null) {
                                    // Save weather data
                                    WidgetUtils.saveWeatherData(id, weather);

                                    // Build the widget update for provider
                                    RemoteViews views = buildUpdate(mContext, mAppWidget4x2, id, locData, weather);
                                    // Push update for this widget to the home screen
                                    mAppWidgetManager.updateAppWidget(id, views);
                                    buildForecast(mAppWidget4x2, weather, id);
                                }
                            }
                        }

                        refreshClock(appWidgetIds);
                        refreshDate(appWidgetIds);
                    }
                }
                return null;
            }
        });
    }

    private void refreshClock(int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0)
            appWidgetIds = mAppWidgetManager.getAppWidgetIds(mAppWidget4x2.getComponentName());

        // Update 4x2 clock widgets
        RemoteViews views = new RemoteViews(mContext.getPackageName(), mAppWidget4x2.getWidgetLayoutId());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // TextClock
            views.setCharSequence(R.id.clock_panel, "setFormat12Hour",
                    mContext.getText(R.string.main_widget_12_hours_format));
            views.setCharSequence(R.id.clock_panel, "setFormat24Hour",
                    mContext.getText(R.string.clock_24_hours_format));
        } else {
            // TextView
            LocalDateTime now = LocalDateTime.now();

            SpannableString timeStr;
            String timeformat = now.format(DateTimeFormatter.ofPattern("h:mma"));
            int end = timeformat.length() - 2;

            if (DateFormat.is24HourFormat(App.getInstance().getAppContext())) {
                timeformat = now.format(DateTimeFormatter.ofPattern("HH:mm"));
                end = timeformat.length() - 1;
                timeStr = new SpannableString(timeformat);
            } else {
                timeStr = new SpannableString(timeformat);
                timeStr.setSpan(new TextAppearanceSpan("sans-serif", Typeface.BOLD, 16,
                                ContextCompat.getColorStateList(mContext, android.R.color.white),
                                ContextCompat.getColorStateList(mContext, android.R.color.white)),
                        end, timeformat.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            views.setTextViewText(R.id.clock_panel, timeStr);
        }

        mAppWidgetManager.partiallyUpdateAppWidget(appWidgetIds, views);

        Logger.writeLine(Log.INFO, "%s: Refreshed clock", TAG);
    }

    private void refreshDate(int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0)
            appWidgetIds = mAppWidgetManager.getAppWidgetIds(mAppWidget4x2.getComponentName());

        // Update 4x2 clock widgets
        RemoteViews views = new RemoteViews(mContext.getPackageName(), mAppWidget4x2.getWidgetLayoutId());
        views.setTextViewText(R.id.date_panel, LocalDateTime.now().format(DateTimeFormatter.ofPattern("eee, MMM dd")));
        mAppWidgetManager.partiallyUpdateAppWidget(appWidgetIds, views);

        Logger.writeLine(Log.INFO, "%s: Refreshed date", TAG);
    }

    private static PendingIntent getDefaultCalendarIntent(Context context) {
        Intent onClickIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR);
        return PendingIntent.getActivity(context, 0, onClickIntent, 0);
    }

    private static PendingIntent getDefaultClockIntent(Context context) {
        String clockAction = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ?
                AlarmClock.ACTION_SHOW_ALARMS : AlarmClock.ACTION_SET_ALARM;
        Intent onClickIntent = new Intent(clockAction);
        return PendingIntent.getActivity(context, 0, onClickIntent, 0);
    }

    private RemoteViews buildUpdate(Context context, WeatherWidgetProvider provider, int appWidgetId, LocationData location, Weather weather) {
        // Build an update that holds the updated widget contents
        RemoteViews updateViews = new RemoteViews(context.getPackageName(), provider.getWidgetLayoutId());

        // Progress bar
        updateViews.setViewVisibility(R.id.refresh_button, View.VISIBLE);
        updateViews.setViewVisibility(R.id.refresh_progress, View.GONE);

        // Set on click refresh intent
        setOnRefreshIntent(context, provider, appWidgetId, updateViews);

        // Temperature
        String temp = Settings.isFahrenheit() ?
                Math.round(weather.getCondition().getTempF()) + "\uf045" : Math.round(weather.getCondition().getTempC()) + "\uf03c";
        int tempTextSize = 72;
        if (provider.getWidgetType() == WidgetType.Widget2x2 || provider.getWidgetType() == WidgetType.Widget4x2)
            tempTextSize = 96;
        updateViews.setImageViewBitmap(R.id.condition_temp,
                ImageUtils.weatherIconToBitmap(context, temp, tempTextSize));

        // Location Name
        updateViews.setTextViewText(R.id.location_name, weather.getLocation().getName());
        // Update Time
        String updatetext = getUpdateTimeText(Settings.getUpdateTime(), false);
        updateViews.setTextViewText(R.id.update_time, updatetext);

        // Background
        int color = wm.getWeatherBackgroundColor(weather);
        updateViews.setInt(R.id.widgetBackground, "setBackgroundColor", color);

        // WeatherIcon
        updateViews.setImageViewResource(R.id.weather_icon,
                wm.getWeatherIconResource(weather.getCondition().getIcon()));

        // Set data for larger widgets
        if (provider.getWidgetType() != WidgetType.Widget1x1) {
            // Condition text
            updateViews.setTextViewText(R.id.condition_weather, weather.getCondition().getWeather());

            // Details
            if (provider.getWidgetType() == WidgetType.Widget2x2 || provider.getWidgetType() == WidgetType.Widget4x2) {
                // Feels like temp
                updateViews.setTextViewText(R.id.condition_feelslike,
                        (Settings.isFahrenheit() ?
                                Math.round(weather.getCondition().getFeelslikeF()) : Math.round(weather.getCondition().getFeelslikeC())) + "º");

                // Wind
                updateViews.setTextViewText(R.id.condition_wind,
                        (Settings.isFahrenheit() ?
                                Math.round(weather.getCondition().getWindMph()) + " mph" : Math.round(weather.getCondition().getWindKph()) + " kph"));

                // Show precipitation % if available
                if (weather.getPrecipitation() != null) {
                    updateViews.setViewVisibility(R.id.condition_pop_panel, View.VISIBLE);
                    updateViews.setTextViewText(R.id.condition_pop, weather.getPrecipitation().getPop() + "%");

                    if (WeatherAPI.OPENWEATHERMAP.equals(Settings.getAPI()) || WeatherAPI.METNO.equals(Settings.getAPI()))
                        updateViews.setImageViewResource(R.id.condition_pop_label, R.drawable.ic_cloudy);
                    else
                        updateViews.setImageViewResource(R.id.condition_pop_label, R.drawable.ic_raindrop);
                } else {
                    updateViews.setViewVisibility(R.id.condition_pop_panel, View.GONE);
                }

                // Open default clock/calendar app
                if (provider.getWidgetType() == WidgetType.Widget4x2) {
                    updateViews.setOnClickPendingIntent(R.id.date_panel, getDefaultCalendarIntent(context));
                    updateViews.setOnClickPendingIntent(R.id.clock_panel, getDefaultClockIntent(context));
                }
            }
        }

        setOnClickIntent(context, location, updateViews);

        return updateViews;
    }

    private static void setOnRefreshIntent(Context context, WeatherWidgetProvider provider, int appWidgetId, RemoteViews updateViews) {
        Intent refreshIntent = new Intent(context, WeatherWidgetBroadcastReceiver.class)
                .setAction(ACTION_REFRESHWIDGET)
                .putExtra(WeatherWidgetProvider.EXTRA_WIDGET_IDS, new int[]{appWidgetId})
                .putExtra(WeatherWidgetProvider.EXTRA_WIDGET_TYPE, provider.getWidgetType().getValue());
        PendingIntent refreshPendingIntent =
                PendingIntent.getBroadcast(context, appWidgetId, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (updateViews != null)
            updateViews.setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent);
    }

    private static void setOnClickIntent(Context context, LocationData location, RemoteViews updateViews) {
        // When user clicks on widget, launch to WeatherNow page
        Intent onClickIntent = new Intent(context.getApplicationContext(), MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        if (!Settings.getHomeData().equals(location))
            onClickIntent.putExtra("shortcut-data", location == null ? null : location.toJson());

        PendingIntent clickPendingIntent =
                PendingIntent.getActivity(context, location.hashCode(), onClickIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (updateViews != null)
            updateViews.setOnClickPendingIntent(R.id.widgetBackground, clickPendingIntent);
    }

    // TODO: Merge into function below
    private void rebuildForecast(final WeatherWidgetProvider provider, final int appWidgetId, final Bundle newOptions) throws InterruptedException {
        new AsyncTaskEx<Void, InterruptedException>().await(new CallableEx<Void, InterruptedException>() {
            @Override
            public Void call() throws InterruptedException {
                if (cts.getToken().isCancellationRequested()) throw new InterruptedException();

                Weather weather = WidgetUtils.getWeatherData(appWidgetId);

                if (cts.getToken().isCancellationRequested()) throw new InterruptedException();

                if (weather == null) {
                    LocationData locData = WidgetUtils.getLocationData(appWidgetId);

                    if (cts.getToken().isCancellationRequested()) throw new InterruptedException();

                    if (locData != null) {
                        if (cts.getToken().isCancellationRequested())
                            throw new InterruptedException();

                        WeatherDataLoader wloader = new WeatherDataLoader(locData);
                        wloader.loadWeatherData(false);
                        weather = wloader.getWeather();
                        if (weather != null)
                            WidgetUtils.saveWeatherData(appWidgetId, weather);
                        else
                            return null;
                    }
                }

                RemoteViews updateViews = new RemoteViews(mContext.getPackageName(), provider.getWidgetLayoutId());

                // Widget dimensions
                int minHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
                int minWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
                int maxHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
                int maxWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH);
                int maxCellHeight = getCellsForSize(maxHeight);
                int maxCellWidth = getCellsForSize(maxWidth);
                int cellHeight = getCellsForSize(minHeight);
                int cellWidth = getCellsForSize(minWidth);

                // Determine forecast size
                int forecastLength = getForecastLength(provider.getWidgetType(), cellWidth);
                if (weather.getForecast().length < forecastLength)
                    forecastLength = weather.getForecast().length;

                if (provider.getWidgetType() == WidgetType.Widget4x1) {
                    if (cellWidth > 3)
                        updateViews.setViewVisibility(R.id.condition_weather, View.VISIBLE);
                    else
                        updateViews.setViewVisibility(R.id.condition_weather, View.GONE);
                } else if (provider.getWidgetType() == WidgetType.Widget4x2) {
                    float clockSize = mContext.getResources().getDimension(R.dimen.clock_text_size);
                    float dateSize = mContext.getResources().getDimension(R.dimen.date_text_size);
                    float scale = (cellHeight != maxCellHeight) ? 1.25f : 1f;

                    updateViews.setTextViewTextSize(R.id.clock_panel, TypedValue.COMPLEX_UNIT_PX, clockSize * scale);
                    updateViews.setTextViewTextSize(R.id.date_panel, TypedValue.COMPLEX_UNIT_PX, dateSize * scale);
                }

                updateViews.removeAllViews(R.id.forecast_layout);
                buildForecastPanel(updateViews, provider, weather, forecastLength, cellHeight == maxCellHeight);
                mAppWidgetManager.partiallyUpdateAppWidget(appWidgetId, updateViews);
                return null;
            }
        });
    }

    private void buildForecast(WeatherWidgetProvider provider, Weather weather) {
        int[] appWidgetIds = mAppWidgetManager.getAppWidgetIds(provider.getComponentName());
        buildForecast(provider, weather, appWidgetIds);
    }

    private void buildForecast(WeatherWidgetProvider provider, Weather weather, int appWidgetId) {
        buildForecast(provider, weather, new int[]{appWidgetId});
    }

    private void buildForecast(WeatherWidgetProvider provider, Weather weather, int[] appWidgetIds) {
        if (weather == null)
            return;

        for (int i = 0; i < appWidgetIds.length; i++) {
            RemoteViews updateViews = new RemoteViews(mContext.getPackageName(), provider.getWidgetLayoutId());
            updateViews.removeAllViews(R.id.forecast_layout);

            Bundle newOptions = mAppWidgetManager.getAppWidgetOptions(appWidgetIds[i]);

            // Widget dimensions
            int minHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
            int minWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            int maxHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
            int maxWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH);
            int maxCellHeight = getCellsForSize(maxHeight);
            int maxCellWidth = getCellsForSize(maxWidth);
            int cellHeight = getCellsForSize(minHeight);
            int cellWidth = getCellsForSize(minWidth);

            // Determine forecast size
            int forecastLength = getForecastLength(provider.getWidgetType(), cellWidth);
            if (weather.getForecast().length < forecastLength)
                forecastLength = weather.getForecast().length;

            if (provider.getWidgetType() == WidgetType.Widget4x1) {
                if (cellWidth > 3)
                    updateViews.setViewVisibility(R.id.condition_weather, View.VISIBLE);
                else
                    updateViews.setViewVisibility(R.id.condition_weather, View.GONE);
            } else if (provider.getWidgetType() == WidgetType.Widget4x2) {
                float clockSize = mContext.getResources().getDimension(R.dimen.clock_text_size);
                float dateSize = mContext.getResources().getDimension(R.dimen.date_text_size);
                float scale = (cellHeight != maxCellHeight) ? 1.25f : 1f;

                updateViews.setTextViewTextSize(R.id.clock_panel, TypedValue.COMPLEX_UNIT_PX, clockSize * scale);
                updateViews.setTextViewTextSize(R.id.date_panel, TypedValue.COMPLEX_UNIT_PX, dateSize * scale);
            }

            buildForecastPanel(updateViews, provider, weather, forecastLength, cellHeight == maxCellHeight);
            mAppWidgetManager.partiallyUpdateAppWidget(appWidgetIds[i], updateViews);
        }
    }

    private void buildForecastPanel(
            RemoteViews updateViews, WeatherWidgetProvider provider, Weather weather,
            int forecastLength, boolean forceSmall) {
        if (weather == null)
            return;

        for (int i = 0; i < forecastLength; i++) {
            Forecast forecast = weather.getForecast()[i];

            RemoteViews forecastPanel = null;
            if (provider.getWidgetType() == WidgetType.Widget4x1)
                forecastPanel = new RemoteViews(mContext.getPackageName(), R.layout.app_widget_forecast_panel);
            else if (provider.getWidgetType() == WidgetType.Widget2x2 || forceSmall)
                forecastPanel = new RemoteViews(mContext.getPackageName(), R.layout.app_widget_forecast_panel_small);
            else
                forecastPanel = new RemoteViews(mContext.getPackageName(), R.layout.app_widget_forecast_panel_medium);

            forecastPanel.setTextViewText(R.id.forecast_date, forecast.getDate().format(DateTimeFormatter.ofPattern("eee")));
            forecastPanel.setImageViewResource(R.id.forecast_icon,
                    wm.getWeatherIconResource(forecast.getIcon()));
            forecastPanel.setTextViewText(R.id.forecast_hi,
                    (Settings.isFahrenheit() ? Math.round(Double.valueOf(forecast.getHighF())) : Math.round(Double.valueOf(forecast.getHighC()))) + "º");
            forecastPanel.setTextViewText(R.id.forecast_lo,
                    (Settings.isFahrenheit() ? Math.round(Double.valueOf(forecast.getLowF())) : Math.round(Double.valueOf(forecast.getLowC()))) + "º");

            updateViews.addView(R.id.forecast_layout, forecastPanel);
        }
    }

    /**
     * Returns number of cells needed for given size of the widget.
     *
     * @param size Widget size in dp.
     * @return Size in number of cells.
     */
    private static int getCellsForSize(int size) {
        // The hardwired sizes in this function come from the hardwired formula found in
        // Android's UI guidelines for widget design:
        // http://developer.android.com/guide/practices/ui_guidelines/widget_design.html
        return (size + 30) / 70;
    }

    private static int getForecastLength(WidgetType widgetType, int cellWidth) {
        int forecastLength = (widgetType == WidgetType.Widget4x2) ? WIDE_FORECAST_LENGTH : FORECAST_LENGTH;

        if (cellWidth < 2) {
            if (widgetType == WidgetType.Widget4x1)
                forecastLength = 0;
        } else if (cellWidth == 2) {
            if (widgetType == WidgetType.Widget4x1)
                forecastLength = 1;
        } else if (cellWidth == 3) {
            if (widgetType == WidgetType.Widget4x1)
                forecastLength = 2;
            else if (widgetType == WidgetType.Widget2x2)
                forecastLength = MEDIUM_FORECAST_LENGTH;
        } else if (cellWidth == 4) {
            if (widgetType == WidgetType.Widget2x2 || widgetType == WidgetType.Widget4x2)
                forecastLength = WIDE_FORECAST_LENGTH;
        } else if (cellWidth > 4) {
            if (widgetType == WidgetType.Widget4x1)
                forecastLength = MEDIUM_FORECAST_LENGTH;
            else if (widgetType == WidgetType.Widget2x2 || widgetType == WidgetType.Widget4x2)
                forecastLength = WIDE_FORECAST_LENGTH;
        }

        return forecastLength;
    }

    private String getUpdateTimeText(LocalDateTime now, boolean shortFormat) {
        String timeformat = now.format(DateTimeFormatter.ofPattern("h:mm a")).toLowerCase();

        if (DateFormat.is24HourFormat(App.getInstance().getAppContext()))
            timeformat = now.format(DateTimeFormatter.ofPattern("HH:mm")).toLowerCase();

        String updatetime = String.format("%s %s", now.format(DateTimeFormatter.ofPattern("eee")), timeformat);

        if (shortFormat)
            return updatetime;
        else
            return String.format("%s %s", mContext.getString(R.string.update_prefix).toLowerCase(), updatetime);
    }

    private Weather getWeather() {
        return new AsyncTask<Weather>().await(new Callable<Weather>() {
            @Override
            public Weather call() throws Exception {
                Weather weather = null;

                try {
                    if (Settings.useFollowGPS())
                        updateLocation();

                    if (cts.getToken().isCancellationRequested()) throw new InterruptedException();

                    WeatherDataLoader wloader = new WeatherDataLoader(Settings.getHomeData());
                    wloader.loadWeatherData(false);

                    weather = wloader.getWeather();

                    if (weather != null) {
                        // Re-schedule alarm at selected interval from now
                        updateAlarm(App.getInstance().getAppContext());
                        Settings.setUpdateTime(LocalDateTime.now());
                    }
                } catch (InterruptedException cancelEx) {
                    Logger.writeLine(Log.ERROR, cancelEx, "%s: GetWeather cancelled", TAG);
                    return null;
                } catch (Exception ex) {
                    Logger.writeLine(Log.ERROR, ex, "%s: GetWeather error", TAG);
                    return null;
                }

                return weather;
            }
        });
    }

    private boolean updateLocation() {
        return new AsyncTask<Boolean>().await(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                boolean locationChanged = false;

                if (Settings.useFollowGPS()) {
                    if (ContextCompat.checkSelfPermission(App.getInstance().getAppContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(App.getInstance().getAppContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return false;
                    }

                    LocationManager locMan = (LocationManager) App.getInstance().getAppContext().getSystemService(Context.LOCATION_SERVICE);
                    boolean isGPSEnabled = locMan.isProviderEnabled(LocationManager.GPS_PROVIDER);
                    boolean isNetEnabled = locMan.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

                    Location location = null;

                    if (isGPSEnabled || isNetEnabled && !cts.getToken().isCancellationRequested()) {
                        Criteria locCriteria = new Criteria();
                        locCriteria.setAccuracy(Criteria.ACCURACY_COARSE);
                        locCriteria.setCostAllowed(false);
                        locCriteria.setPowerRequirement(Criteria.POWER_LOW);
                        String provider = locMan.getBestProvider(locCriteria, true);
                        location = locMan.getLastKnownLocation(provider);

                        if (location != null && !cts.getToken().isCancellationRequested()) {
                            LocationData lastGPSLocData = Settings.getLastGPSLocData();

                            if (cts.getToken().isCancellationRequested()) return locationChanged;

                            // Check previous location difference
                            if (lastGPSLocData.getQuery() != null &&
                                    Math.abs(ConversionMethods.calculateHaversine(lastGPSLocData.getLatitude(), lastGPSLocData.getLongitude(),
                                            location.getLatitude(), location.getLongitude())) < 1600) {
                                return false;
                            }

                            LocationQueryViewModel query_vm = null;

                            TaskCompletionSource<LocationQueryViewModel> tcs = new TaskCompletionSource<>(cts.getToken());
                            tcs.setResult(wm.getLocation(location));
                            try {
                                query_vm = Tasks.await(tcs.getTask());
                            } catch (ExecutionException e) {
                                Logger.writeLine(Log.ERROR, e.getCause());
                            } catch (InterruptedException e) {
                                return locationChanged;
                            }

                            if (StringUtils.isNullOrEmpty(query_vm.getLocationQuery()))
                                query_vm = new LocationQueryViewModel();

                            if (StringUtils.isNullOrWhitespace(query_vm.getLocationQuery())) {
                                // Stop since there is no valid query
                                return false;
                            }

                            if (cts.getToken().isCancellationRequested()) return locationChanged;

                            // Save oldkey
                            String oldkey = lastGPSLocData.getQuery();

                            // Save location as last known
                            lastGPSLocData.setData(query_vm, location);
                            Settings.saveLastGPSLocData(lastGPSLocData);

                            WearableDataListenerService.enqueueWork(App.getInstance().getAppContext(),
                                    new Intent(App.getInstance().getAppContext(), WearableDataListenerService.class)
                                            .setAction(WearableDataListenerService.ACTION_SENDLOCATIONUPDATE));

                            // Update widget ids for location
                            if (oldkey != null && WidgetUtils.exists(oldkey)) {
                                WidgetUtils.updateWidgetIds(oldkey, lastGPSLocData);
                            }

                            locationChanged = true;
                        }
                    }
                }

                return locationChanged;
            }
        });
    }
}