package com.thewizrd.simpleweather.weather.weatherunderground;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.thewizrd.simpleweather.WeatherLoadedListener;
import com.thewizrd.simpleweather.utils.FileUtils;
import com.thewizrd.simpleweather.utils.JSONParser;
import com.thewizrd.simpleweather.utils.Settings;
import com.thewizrd.simpleweather.utils.WeatherException;
import com.thewizrd.simpleweather.utils.WeatherUtils;
import com.thewizrd.simpleweather.weather.weatherunderground.data.Rootobject;
import com.thewizrd.simpleweather.weather.weatherunderground.data.WUWeather;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class WUDataLoader {

    private WeatherLoadedListener mCallBack;

    private String location_query = null;
    private WUWeather weather = null;
    private int locationIdx = 0;
    private File filesDir = null;
    private File weatherFile = null;
    private Context mContext;

    public WUDataLoader(Context context, WeatherLoadedListener listener, String query, int idx) {
        location_query = query;
        locationIdx = idx;

        mContext = context;
        filesDir = mContext.getFilesDir();
        mCallBack = listener;
    }

    private void getWeatherData() throws WeatherException {
        String queryAPI = "http://api.wunderground.com/api/" + Settings.getAPIKEY()
                + "/astronomy/conditions/forecast10day";
        String options = ".json";
        WeatherException wEx = null;
        int counter = 0;

        do {
            try {
                URL queryURL = new URL(queryAPI + location_query + options);
                URLConnection client = queryURL.openConnection();
                InputStream stream = client.getInputStream();
                // Reset exception
                wEx = null;

                // Read to buffer
                ByteArrayOutputStream buffStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                while ((length = stream.read(buffer)) != -1) {
                    buffStream.write(buffer, 0, length);
                }

                // Load data
                String response = buffStream.toString("UTF-8");
                Rootobject root = (Rootobject) JSONParser.deserializer(response, Rootobject.class);

                // Check for errors
                if (root.response.error != null)
                {
                    switch (root.response.error.type)
                    {
                        case "querynotfound":
                            wEx = new WeatherException(WeatherUtils.ErrorStatus.QUERYNOTFOUND);
                            break;
                        case "keynotfound":
                            wEx = new WeatherException(WeatherUtils.ErrorStatus.INVALIDAPIKEY);
                            break;
                        default:
                            break;
                    }
                }

                // Load weather
                weather = new WUWeather(root);

                // Close
                buffStream.close();
                stream.close();

                if (weather != null)
                    saveWeatherData();
            } catch (UnknownHostException uHEx) {
                weather = null;
                wEx = new WeatherException(WeatherUtils.ErrorStatus.NETWORKERROR);
            } catch (Exception e) {
                weather = null;
                e.printStackTrace();
            }

            if (weather == null)
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            counter++;
        } while (weather == null && counter < 5);

        // Load old data if available and we can't get new data
        if (weather == null)
        {
            loadSavedWeatherData(weatherFile, true);
        }

        // Throw error if still null
        if (weather == null && wEx != null) {
            throw wEx;
        } else if (weather == null && wEx == null) {
            throw new WeatherException(WeatherUtils.ErrorStatus.NOWEATHER);
        }
    }

    public void loadWeatherData(final boolean forceRefresh) throws IOException {
        if (weatherFile == null) {
            weatherFile = new File(filesDir, "weather" + locationIdx + ".json");

            if (!weatherFile.exists() && !weatherFile.createNewFile())
                throw new IOException("Unable to load weather data");
        }

        new Thread() {
            @Override
            public void run() {
                if (forceRefresh) {
                    try {
                        getWeatherData();
                    } catch (final WeatherException e) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(mContext, e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
                else {
                    try {
                        loadWeatherData();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        mCallBack.onWeatherLoaded(locationIdx, weather);
                    }
                });
            }
        }.start();
    }

    private void loadWeatherData() throws IOException {
        if (weatherFile == null) {
            weatherFile = new File(filesDir, "weather" + locationIdx + ".json");

            if (!weatherFile.exists() && !weatherFile.createNewFile())
                throw new IOException("Unable to load weather data");
        }

        boolean gotData = loadSavedWeatherData(weatherFile);

        if (!gotData) {
            try {
                getWeatherData();
            } catch (final WeatherException e) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mContext, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private boolean loadSavedWeatherData(File file, boolean _override) {
        if (_override) {
            if (!file.exists() || file.length() == 0)
                return false;

            try {
                String weatherJson = FileUtils.readFile(file);
                weather = (WUWeather) JSONParser.deserializer(weatherJson, WUWeather.class);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return weather != null;
        }
        else
            return loadSavedWeatherData(file);
    }

    private boolean loadSavedWeatherData(File file) {
        if (!file.exists() || file.length() == 0)
            return false;

        try {
            String weatherJson = FileUtils.readFile(file);
            weather = (WUWeather) JSONParser.deserializer(weatherJson, WUWeather.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (weather == null)
            return false;

        // Weather data expiration
        int ttl = 60;

        Date updateTime = weather.update_time;
        Date now = Calendar.getInstance().getTime();
        long span = now.getTime() - updateTime.getTime();
        TimeUnit inMins = TimeUnit.MINUTES;
        long minSpan = inMins.convert(span, TimeUnit.MILLISECONDS);

        // Check file age
        return minSpan < ttl;
    }

    private void saveWeatherData() throws IOException {
        if (weatherFile == null) {
            weatherFile = new File(filesDir, "weather" + locationIdx + ".json");

            if (!weatherFile.exists() && !weatherFile.createNewFile())
                throw new IOException("Unable to save weather data");
        }

        JSONParser.serializer(weather, weatherFile);
    }

    public WUWeather getWeather() { return weather; }
}
