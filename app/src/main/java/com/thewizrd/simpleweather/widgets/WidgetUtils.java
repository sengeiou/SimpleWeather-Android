package com.thewizrd.simpleweather.widgets;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Build;
import android.util.Log;
import android.util.SparseArray;
import android.widget.RemoteViews;

import androidx.annotation.IdRes;

import com.google.android.gms.common.util.ArrayUtils;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.thewizrd.shared_resources.locationdata.LocationData;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.shared_resources.utils.Settings;
import com.thewizrd.shared_resources.utils.StringUtils;
import com.thewizrd.shared_resources.weatherdata.Weather;
import com.thewizrd.simpleweather.App;

import java.io.File;
import java.io.FilenameFilter;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WidgetUtils {
    // Shared Settings
    private static SharedPreferences widgetPrefs = App.getInstance().getAppContext().getSharedPreferences("appwidgets", Context.MODE_PRIVATE);
    private static SharedPreferences.Editor editor = widgetPrefs.edit();

    // Widget Prefs
    private static final int CurrentPrefsVersion = 3;

    // Keys
    private static final String KEY_VERSION = "key_version";
    private static final String KEY_WEATHERDATA = "key_weatherdata";
    private static final String KEY_LOCATIONDATA = "key_locationdata";
    private static final String KEY_LOCATIONQUERY = "key_locationquery";
    private static final String KEY_WIDGETBACKGROUND = "key_widgetbackground";

    static {
        init();
    }

    enum WidgetBackground {
        CURRENT_CONDITIONS(0),
        WHITE(1),
        BLACK(2),
        TRANSPARENT(3);

        private final int value;

        public int getValue() {
            return value;
        }

        private WidgetBackground(int value) {
            this.value = value;
        }

        private static SparseArray<WidgetBackground> map = new SparseArray<>();

        static {
            for (WidgetBackground errorStatus : values()) {
                map.put(errorStatus.value, errorStatus);
            }
        }

        public static WidgetBackground valueOf(int value) {
            return map.get(value);
        }
    }

    private static void init() {
        // Check prefs
        if (getVersion() < CurrentPrefsVersion) {
            switch (getVersion()) {
                case -1:
                    // First time, so load all current widgets under Home location
                    if (Settings.isWeatherLoaded()) {
                        LocationData homeLocation = Settings.getHomeData();
                        saveIds(homeLocation.getQuery(), getAllWidgetIds());
                    }

                    Map<String, ?> widgetMap = widgetPrefs.getAll();
                    for (String key : widgetMap.keySet()) {
                        String json = (String) widgetMap.get(key);
                        if (json.contains("System.Collections.Generic.List"))
                            json = json.replace("\"$type\":\"System.Collections.Generic.List`1[[System.Int32, mscorlib]], mscorlib\",\"$values\":", "");
                        if (json.startsWith("{") && json.endsWith("}")) {
                            int length = json.length();
                            json = json.substring(1, length - 1);
                        }
                        editor.putString(key, json).commit();
                    }
                    break;
                case 0:
                case 1:
                    widgetMap = widgetPrefs.getAll();
                    for (String key : widgetMap.keySet()) {
                        String json = (String) widgetMap.get(key);
                        if (json.contains("System.Collections.Generic.List"))
                            json = json.replace("\"$type\":\"System.Collections.Generic.List`1[[System.Int32, mscorlib]], mscorlib\",\"$values\":", "");
                        if (json.startsWith("{") && json.endsWith("}")) {
                            int length = json.length();
                            json = json.substring(1, length - 1);
                        }
                        editor.putString(key, json).commit();
                    }
                    break;
                case 2:
                    if (Settings.isWeatherLoaded()) {
                        LocationData homeLocation = Settings.getHomeData();
                        widgetMap = widgetPrefs.getAll();
                        for (String key : widgetMap.keySet()) {
                            if (key.equals(homeLocation.getQuery())) {
                                String listJson = (String) widgetMap.get(key);
                                editor.putString("GPS", listJson).commit();
                                editor.remove(key).commit();
                                break;
                            }
                        }
                    }
                    break;
                default:
                    break;
            }

            // Set to latest version
            setVersion(CurrentPrefsVersion);
        }
    }

    private static int getVersion() {
        return Integer.valueOf(widgetPrefs.getString(KEY_VERSION, "-1"));
    }

    private static void setVersion(int value) {
        editor.putString(KEY_VERSION, Integer.toString(value));
        editor.commit();
    }

    private static List<Integer> getAllWidgetIds() {
        AppWidgetManager mAppWidgetManager = AppWidgetManager.getInstance(App.getInstance().getAppContext());
        WeatherWidgetProvider1x1 mAppWidget1x1 = WeatherWidgetProvider1x1.getInstance();
        WeatherWidgetProvider2x2 mAppWidget2x2 = WeatherWidgetProvider2x2.getInstance();
        WeatherWidgetProvider4x1 mAppWidget4x1 = WeatherWidgetProvider4x1.getInstance();
        WeatherWidgetProvider4x2 mAppWidget4x2 = WeatherWidgetProvider4x2.getInstance();

        List<Integer> currentIds = new ArrayList<>();
        currentIds.addAll(ArrayUtils.toArrayList(
                ArrayUtils.toWrapperArray(mAppWidgetManager.getAppWidgetIds(mAppWidget1x1.getComponentName()))));
        currentIds.addAll(ArrayUtils.toArrayList(
                ArrayUtils.toWrapperArray(mAppWidgetManager.getAppWidgetIds(mAppWidget2x2.getComponentName()))));
        currentIds.addAll(ArrayUtils.toArrayList(
                ArrayUtils.toWrapperArray(mAppWidgetManager.getAppWidgetIds(mAppWidget4x1.getComponentName()))));
        currentIds.addAll(ArrayUtils.toArrayList(
                ArrayUtils.toWrapperArray(mAppWidgetManager.getAppWidgetIds(mAppWidget4x2.getComponentName()))));

        return currentIds;
    }

    public static void addWidgetId(String location_query, int widgetId) {
        String listJson = widgetPrefs.getString(location_query, "");
        if (StringUtils.isNullOrWhitespace(listJson)) {
            List<Integer> newlist = Collections.singletonList(widgetId);
            saveIds(location_query, newlist);
        } else {
            Type intArrListType = new TypeToken<ArrayList<Integer>>() {
            }.getType();
            ArrayList<Integer> idList = JSONParser.deserializer(listJson, intArrListType);
            if (idList != null && !idList.contains(widgetId)) {
                idList.add(widgetId);
                saveIds(location_query, idList);
            }
        }

        cleanupWidgetData();
        cleanupWidgetIds();
    }

    public static void removeWidgetId(String location_query, int widgetId) {
        String listJson = widgetPrefs.getString(location_query, "");
        if (!StringUtils.isNullOrWhitespace(listJson)) {
            Type intArrListType = new TypeToken<ArrayList<Integer>>() {
            }.getType();
            ArrayList<Integer> idList = JSONParser.deserializer(listJson, intArrListType);
            if (idList != null) {
                idList.remove(Integer.valueOf(widgetId));

                if (idList.size() == 0)
                    editor.remove(location_query).commit();
                else
                    saveIds(location_query, idList);
            }
        }
        deletePreferences(widgetId);
    }

    private static void deletePreferences(int widgetId) {
        getEditor(widgetId).clear().commit();

        Context context = App.getInstance().getAppContext();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.deleteSharedPreferences(String.format(Locale.ROOT, "appwidget_%d", widgetId));
        } else {
            String parentPath = context.getFilesDir().getParent();
            String sharedPrefsPath = String.format(Locale.ROOT, "%s/shared_prefs/appwidget_%d.xml", parentPath, widgetId);
            File sharedPrefsFile = new File(sharedPrefsPath);

            if (sharedPrefsFile.exists() &&
                    sharedPrefsFile.canWrite() && sharedPrefsFile.getParentFile().canWrite()) {
                sharedPrefsFile.delete();
            }
        }
    }

    public static void updateWidgetIds(String oldQuery, LocationData newLocation) {
        String listJson = widgetPrefs.getString(oldQuery, "");
        editor.remove(oldQuery);
        editor.putString(newLocation.getQuery(), listJson).commit();

        for (int id : getWidgetIds(newLocation.getQuery())) {
            saveLocationData(id, newLocation);
        }

        cleanupWidgetData();
        cleanupWidgetIds();
    }

    public static int[] getWidgetIds(String location_query) {
        String listJson = widgetPrefs.getString(location_query, "");
        if (!StringUtils.isNullOrWhitespace(listJson)) {
            Type intArrListType = new TypeToken<ArrayList<Integer>>() {
            }.getType();
            ArrayList<Integer> idList = JSONParser.deserializer(listJson, intArrListType);
            if (idList != null) {
                return ArrayUtils.toPrimitiveArray(idList);
            }
        }

        return new int[0];
    }

    public static boolean exists(String location_query) {
        String listJson = widgetPrefs.getString(location_query, "");
        if (!StringUtils.isNullOrWhitespace(listJson)) {
            Type intArrListType = new TypeToken<ArrayList<Integer>>() {
            }.getType();
            ArrayList<Integer> idList = JSONParser.deserializer(listJson, intArrListType);
            if (idList != null && idList.size() > 0) {
                return true;
            }
        }

        return false;
    }

    public static boolean exists(int appWidgetId) {
        LocationData locData = getLocationData(appWidgetId);
        if (locData != null) {
            String listJson = widgetPrefs.getString(locData.getQuery(), "");
            if (!StringUtils.isNullOrWhitespace(listJson)) {
                Type intArrListType = new TypeToken<ArrayList<Integer>>() {
                }.getType();
                ArrayList<Integer> idList = JSONParser.deserializer(listJson, intArrListType);
                if (idList != null) {
                    return idList.contains(appWidgetId);
                }
            }
        }

        return false;
    }

    private static boolean saveIds(String key, List<Integer> idList) {
        String json = JSONParser.serializer(idList, ArrayList.class);
        return editor.putString(key, json)
                .commit();
    }

    private static SharedPreferences getPreferences(int appWidgetId) {
        return App.getInstance().getAppContext().getSharedPreferences(String.format(Locale.ROOT, "appwidget_%d", appWidgetId), Context.MODE_PRIVATE);
    }

    private static SharedPreferences.Editor getEditor(int appWidgetId) {
        return getPreferences(appWidgetId).edit();
    }

    public static void saveAllData(int appWidgetId, LocationData location, Weather weather) {
        SharedPreferences.Editor editor = getEditor(appWidgetId);

        String locJson = location == null ? null : location.toJson();
        String weatherJson = weather == null ? null : weather.toJson();

        if (locJson != null)
            editor.putString(KEY_LOCATIONDATA, locJson);
        if (weatherJson != null)
            editor.putString(KEY_WEATHERDATA, weatherJson);
        editor.commit();
    }

    static void saveLocationData(int appWidgetId, LocationData location) {
        SharedPreferences.Editor editor = getEditor(appWidgetId);

        String locJson = location == null ? null : location.toJson();

        if (locJson != null)
            editor.putString(KEY_LOCATIONDATA, locJson);
        editor.commit();
    }

    public static void saveWeatherData(int appWidgetId, Weather weather) {
        SharedPreferences.Editor editor = getEditor(appWidgetId);

        String weatherJson = weather == null ? null : weather.toJson();

        if (weatherJson != null)
            editor.putString(KEY_WEATHERDATA, weatherJson);
        editor.commit();
    }

    static LocationData getLocationData(int appWidgetId) {
        SharedPreferences prefs = getPreferences(appWidgetId);
        String locDataJson = prefs.getString(KEY_LOCATIONDATA, null);

        if (StringUtils.isNullOrWhitespace(locDataJson)) {
            return null;
        } else {
            JsonReader jsonTextReader = new JsonReader(new StringReader(locDataJson));
            return LocationData.fromJson(jsonTextReader);
        }
    }

    static Weather getWeatherData(int appWidgetId) {
        SharedPreferences prefs = getPreferences(appWidgetId);
        String weatherDataJson = prefs.getString(KEY_WEATHERDATA, null);

        if (StringUtils.isNullOrWhitespace(weatherDataJson)) {
            return null;
        } else {
            JsonReader jsonTextReader = new JsonReader(new StringReader(weatherDataJson));
            return Weather.fromJson(jsonTextReader);
        }
    }

    public static void cleanupWidgetIds() {
        List<LocationData> locs = new ArrayList<>(Settings.getLocationData());
        LocationData homeData = Settings.getLastGPSLocData();
        if (homeData != null) locs.add(homeData);
        List<String> currLocQueries = new ArrayList<>();
        for (LocationData loc : locs) {
            currLocQueries.add(loc.getQuery());
        }
        Map<String, ?> widgetMap = widgetPrefs.getAll();
        for (String key : widgetMap.keySet()) {
            if (!KEY_VERSION.equals(key) && !"GPS".equals(key) && !currLocQueries.contains(key))
                editor.remove(key);
        }
        editor.commit();
    }

    public static void cleanupWidgetData() {
        List<Integer> currentIds = getAllWidgetIds();

        Context context = App.getInstance().getAppContext();
        String parentPath = context.getFilesDir().getParent();
        String sharedPrefsPath = String.format(Locale.ROOT, "%s/shared_prefs", parentPath);
        File sharedPrefsFolder = new File(sharedPrefsPath);
        File[] appWidgetFiles = sharedPrefsFolder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                String lowerCaseName = name.toLowerCase();
                return lowerCaseName.startsWith("appwidget_") && lowerCaseName.endsWith(".xml");
            }
        });

        for (File file : appWidgetFiles) {
            String fileName = file.getName();
            String idString = "";
            if (!StringUtils.isNullOrWhitespace(fileName)) {
                idString = fileName.replace("appwidget_", "")
                        .replace(".xml", "");

                try {
                    Integer id = Integer.valueOf(idString);

                    if (!currentIds.contains(id) && file.exists() && file.canWrite() && file.getParentFile().canWrite()) {
                        file.delete();
                    }
                } catch (Exception ex) {
                    Logger.writeLine(Log.ERROR, ex);
                }
            }
        }
    }

    public static boolean isGPS(int widgetId) {
        String listJson = widgetPrefs.getString("GPS", "");
        if (!StringUtils.isNullOrWhitespace(listJson)) {
            Type intArrListType = new TypeToken<ArrayList<Integer>>() {
            }.getType();
            ArrayList<Integer> idList = JSONParser.deserializer(listJson, intArrListType);
            if (idList != null && idList.size() > 0) {
                return idList.contains(widgetId);
            }
        }

        return false;
    }

    public static WidgetBackground getWidgetBackground(int widgetId) {
        SharedPreferences prefs = getPreferences(widgetId);

        String value = prefs.getString(KEY_WIDGETBACKGROUND, "0");
        if (StringUtils.isNullOrWhitespace(value))
            value = "0";

        switch (Integer.valueOf(value)) {
            case 0:
            default:
                return WidgetBackground.CURRENT_CONDITIONS;
            case 1:
                return WidgetBackground.WHITE;
            case 2:
                return WidgetBackground.BLACK;
            case 3:
                return WidgetBackground.TRANSPARENT;
        }
    }

    public static void setWidgetBackground(int widgetId, int value) {
        SharedPreferences.Editor editor = getEditor(widgetId);

        editor.putString(KEY_WIDGETBACKGROUND, Integer.toString(value));
        editor.commit();
    }

    // https://stackoverflow.com/a/53930384
    public static void setProgessBarTint(RemoteViews view, @IdRes int viewId, int color) {
        Method setTintMethod = null;
        try {
            setTintMethod = RemoteViews.class.getMethod("setProgressTintList", int.class, ColorStateList.class);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        if (setTintMethod != null) {
            try {
                setTintMethod.invoke(view, viewId, ColorStateList.valueOf(color));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    public static void deleteWidget(int id) {
        if (isGPS(id)) {
            removeWidgetId("GPS", id);
        } else {
            LocationData locData = getLocationData(id);
            if (locData != null) {
                removeWidgetId(locData.getQuery(), id);
            }
        }
    }
}
