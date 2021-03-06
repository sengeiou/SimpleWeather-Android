package com.thewizrd.shared_resources.weatherdata.here;

import android.util.Log;

import com.ibm.icu.util.ULocale;
import com.thewizrd.shared_resources.AsyncTask;
import com.thewizrd.shared_resources.locationdata.LocationData;
import com.thewizrd.shared_resources.locationdata.here.HERELocationProvider;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.shared_resources.utils.Settings;
import com.thewizrd.shared_resources.utils.StringUtils;
import com.thewizrd.shared_resources.utils.WeatherException;
import com.thewizrd.shared_resources.utils.WeatherUtils;
import com.thewizrd.shared_resources.utils.here.HEREOAuthUtils;
import com.thewizrd.shared_resources.weatherdata.Forecast;
import com.thewizrd.shared_resources.weatherdata.Weather;
import com.thewizrd.shared_resources.weatherdata.WeatherAPI;
import com.thewizrd.shared_resources.weatherdata.WeatherAlert;
import com.thewizrd.shared_resources.weatherdata.WeatherIcons;
import com.thewizrd.shared_resources.weatherdata.WeatherProviderImpl;
import com.thewizrd.shared_resources.weatherdata.nws.alerts.NWSAlertProvider;

import org.threeten.bp.ZoneOffset;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

public final class HEREWeatherProvider extends WeatherProviderImpl {

    public HEREWeatherProvider() {
        super();
        locationProvider = new HERELocationProvider();
    }

    @Override
    public String getWeatherAPI() {
        return WeatherAPI.HERE;
    }

    @Override
    public boolean supportsWeatherLocale() {
        return true;
    }

    @Override
    public boolean isKeyRequired() {
        return false;
    }

    @Override
    public boolean supportsAlerts() {
        return true;
    }

    @Override
    public boolean needsExternalAlertData() {
        return false;
    }

    @Override
    public boolean isKeyValid(String key) {
        return false;
    }

    @Override
    public String getAPIKey() {
        return null;
    }

    @Override
    public Weather getWeather(String location_query) throws WeatherException {
        Weather weather = null;

        String queryAPI = null;
        URL weatherURL = null;
        HttpURLConnection client = null;

        ULocale uLocale = ULocale.forLocale(Locale.getDefault());
        String locale = localeToLangCode(uLocale.getLanguage(), uLocale.toLanguageTag());

        queryAPI = "https://weather.ls.hereapi.com/weather/1.0/report.json";

        queryAPI += "?product=alerts&product=forecast_7days_simple" +
                "&product=forecast_hourly&product=forecast_astronomy&product=observation&oneobservation=true&%s" +
                "&language=%s&metric=false";

        WeatherException wEx = null;

        try {
            weatherURL = new URL(String.format(queryAPI, location_query, locale));

            client = (HttpURLConnection) weatherURL.openConnection();
            client.setConnectTimeout(Settings.CONNECTION_TIMEOUT);
            client.setReadTimeout(Settings.READ_TIMEOUT);

            // Add headers to request
            String authorization = new AsyncTask<String>().await(new Callable<String>() {
                @Override
                public String call() {
                    return HEREOAuthUtils.getBearerToken(false);
                }
            });
            client.addRequestProperty("Authorization", authorization);

            // Connect to webstream
            InputStream stream = client.getInputStream();

            // Reset exception
            wEx = null;

            // Load weather
            Rootobject root = null;
            root = JSONParser.deserializer(stream, Rootobject.class);

            // Check for errors
            if (root.getType() != null) {
                switch (root.getType()) {
                    case "Invalid Request":
                        wEx = new WeatherException(WeatherUtils.ErrorStatus.QUERYNOTFOUND);
                        break;
                    case "Unauthorized":
                        wEx = new WeatherException(WeatherUtils.ErrorStatus.INVALIDAPIKEY);
                        break;
                    default:
                        break;
                }
            }

            // End Stream
            stream.close();

            weather = new Weather(root);

            // Add weather alerts if available
            if (root.getAlerts() != null && root.getAlerts().getAlerts().size() > 0) {
                if (weather.getWeatherAlerts() == null)
                    weather.setWeatherAlerts(new ArrayList<WeatherAlert>(root.getAlerts().getAlerts().size()));

                for (AlertsItem result : root.getAlerts().getAlerts()) {
                    weather.getWeatherAlerts().add(new WeatherAlert(result));
                }
            }
        } catch (Exception ex) {
            weather = null;
            if (ex instanceof IOException) {
                wEx = new WeatherException(WeatherUtils.ErrorStatus.NETWORKERROR);
            }
            Logger.writeLine(Log.ERROR, ex, "HEREWeatherProvider: error getting weather data");
        } finally {
            if (client != null)
                client.disconnect();
        }

        if (wEx == null && (weather == null || !weather.isValid())) {
            wEx = new WeatherException(WeatherUtils.ErrorStatus.NOWEATHER);
        } else if (weather != null) {
            if (supportsWeatherLocale())
                weather.setLocale(locale);

            weather.setQuery(location_query);
        }

        if (wEx != null)
            throw wEx;

        return weather;
    }

    @Override
    public Weather getWeather(LocationData location) throws WeatherException {
        Weather weather = super.getWeather(location);

        ZoneOffset offset = location.getTzOffset();

        if (weather.getWeatherAlerts() != null && weather.getWeatherAlerts().size() > 0) {
            for (WeatherAlert alert : weather.getWeatherAlerts()) {
                if (!alert.getDate().getOffset().equals(offset)) {
                    alert.setDate(alert.getDate().withZoneSameLocal(offset));
                }

                if (!alert.getExpiresDate().getOffset().equals(offset)) {
                    alert.setExpiresDate(alert.getExpiresDate().withZoneSameLocal(offset));
                }
            }
        } else if ("US".equals(location.getCountryCode())) {
            List<WeatherAlert> alerts = new ArrayList<>(new NWSAlertProvider().getAlerts(location));

            if (weather.getWeatherAlerts() != null)
                alerts.addAll(weather.getWeatherAlerts());

            weather.setWeatherAlerts(alerts);
        }

        // Update tz for weather properties
        weather.setUpdateTime(weather.getUpdateTime().withZoneSameInstant(location.getTzOffset()));

        for (Forecast forecast : weather.getForecast()) {
            forecast.setDate(forecast.getDate().plusSeconds(offset.getTotalSeconds()));
        }

        return weather;
    }

    @Override
    public List<WeatherAlert> getAlerts(LocationData location) {
        List<WeatherAlert> alerts = null;

        String queryAPI = null;
        URL weatherURL = null;
        HttpURLConnection client = null;

        ULocale uLocale = ULocale.forLocale(Locale.getDefault());
        String locale = localeToLangCode(uLocale.getLanguage(), uLocale.toLanguageTag());

        queryAPI = "https://weather.ls.hereapi.com/weather/1.0/report.json?product=alerts&%s" +
                "&language=%s&metric=false";

        try {
            weatherURL = new URL(String.format(queryAPI, location.getQuery(), locale));

            client = (HttpURLConnection) weatherURL.openConnection();
            client.setConnectTimeout(Settings.CONNECTION_TIMEOUT);
            client.setReadTimeout(Settings.READ_TIMEOUT);

            // Add headers to request
            String authorization = new AsyncTask<String>().await(new Callable<String>() {
                @Override
                public String call() {
                    return HEREOAuthUtils.getBearerToken(false);
                }
            });
            client.addRequestProperty("Authorization", authorization);

            // Connect to webstream
            InputStream stream = client.getInputStream();

            // Load data
            Rootobject root = null;
            // TODO: async task it
            root = JSONParser.deserializer(stream, Rootobject.class);

            alerts = new ArrayList<>(root.getAlerts().getAlerts().size());

            for (AlertsItem result : root.getAlerts().getAlerts()) {
                alerts.add(new WeatherAlert(result));
            }

            // End Stream
            stream.close();
        } catch (Exception ex) {
            alerts = new ArrayList<>();
            Logger.writeLine(Log.ERROR, ex, "HEREWeatherProvider: error getting weather alert data");
        } finally {
            if (client != null)
                client.disconnect();
        }

        if (alerts == null)
            alerts = new ArrayList<>();

        return alerts;
    }

    @Override
    public String updateLocationQuery(Weather weather) {
        return String.format(Locale.ROOT, "latitude=%s&longitude=%s", weather.getLocation().getLatitude(), weather.getLocation().getLongitude());
    }

    @Override
    public String updateLocationQuery(LocationData location) {
        return String.format(Locale.ROOT, "latitude=%s&longitude=%s", location.getLatitude(), location.getLongitude());
    }

    @Override
    public String localeToLangCode(String iso, String name) {
        return name;
    }

    @Override
    public String getWeatherIcon(String icon) {
        boolean isNight = false;

        if (icon.startsWith("N_") || icon.contains("night_"))
            isNight = true;

        return getWeatherIcon(isNight, icon);
    }

    @Override
    public String getWeatherIcon(boolean isNight, String icon) {
        String weatherIcon = "";

        if (icon.contains("mostly_sunny") || icon.contains("mostly_clear") || icon.contains("partly_cloudy")
                || icon.contains("passing_clounds") || icon.contains("more_sun_than_clouds") || icon.contains("scattered_clouds")
                || icon.contains("decreasing_cloudiness") || icon.contains("clearing_skies") || icon.contains("overcast")
                || icon.contains("low_clouds") || icon.contains("passing_clouds"))
            if (isNight)
                weatherIcon = WeatherIcons.NIGHT_ALT_PARTLY_CLOUDY;
            else
                weatherIcon = WeatherIcons.DAY_SUNNY_OVERCAST;
        else if (icon.contains("cloudy") || icon.contains("a_mixture_of_sun_and_clouds") || icon.contains("increasing_cloudiness")
                || icon.contains("breaks_of_sun_late") || icon.contains("afternoon_clouds") || icon.contains("morning_clouds")
                || icon.contains("partly_sunny") || icon.contains("more_clouds_than_sun") || icon.contains("broken_clouds"))
            if (isNight)
                weatherIcon = WeatherIcons.NIGHT_ALT_CLOUDY;
            else
                weatherIcon = WeatherIcons.DAY_CLOUDY;
        else if (icon.contains("high_level_clouds") || icon.contains("high_clouds"))
            if (isNight)
                weatherIcon = WeatherIcons.NIGHT_ALT_CLOUDY_HIGH;
            else
                weatherIcon = WeatherIcons.DAY_CLOUDY_HIGH;
        else if (icon.contains("flurries") || icon.contains("snowstorm") || icon.contains("blizzard"))
            weatherIcon = WeatherIcons.SNOW_WIND;
        else if (icon.contains("fog"))
            weatherIcon = WeatherIcons.FOG;
        else if (icon.contains("hazy") || icon.contains("haze"))
            if (isNight)
                weatherIcon = WeatherIcons.WINDY;
            else
                weatherIcon = WeatherIcons.DAY_HAZE;
        else if (icon.contains("sleet") || icon.contains("snow_changing_to_an_icy_mix") || icon.contains("an_icy_mix_changing_to_snow")
                || icon.contains("rain_changing_to_snow"))
            weatherIcon = WeatherIcons.SLEET;
        else if (icon.contains("mixture_of_precip") || icon.contains("icy_mix") || icon.contains("snow_changing_to_rain")
                || icon.contains("snow_rain_mix") || icon.contains("freezing_rain"))
            weatherIcon = WeatherIcons.RAIN_MIX;
        else if (icon.contains("hail"))
            weatherIcon = WeatherIcons.HAIL;
        else if (icon.contains("snow"))
            weatherIcon = WeatherIcons.SNOW;
        else if (icon.contains("sprinkles") || icon.contains("drizzle"))
            weatherIcon = WeatherIcons.SPRINKLE;
        else if (icon.contains("light_rain") || icon.contains("showers"))
            weatherIcon = WeatherIcons.SHOWERS;
        else if (icon.contains("rain") || icon.contains("flood"))
            weatherIcon = WeatherIcons.RAIN;
        else if (icon.contains("tstorms") || icon.contains("thunderstorms") || icon.contains("thundershowers")
                || icon.contains("tropical_storm"))
            weatherIcon = WeatherIcons.THUNDERSTORM;
        else if (icon.contains("smoke"))
            weatherIcon = WeatherIcons.SMOKE;
        else if (icon.contains("tornado"))
            weatherIcon = WeatherIcons.TORNADO;
        else if (icon.contains("hurricane"))
            weatherIcon = WeatherIcons.HURRICANE;
        else if (icon.contains("sandstorm"))
            weatherIcon = WeatherIcons.SANDSTORM;
        else if (icon.contains("duststorm"))
            weatherIcon = WeatherIcons.DUST;
        else if (icon.contains("clear") || icon.contains("sunny"))
            if (isNight)
                weatherIcon = WeatherIcons.NIGHT_CLEAR;
            else
                weatherIcon = WeatherIcons.DAY_SUNNY;
        else if (icon.contains("cw_no_report_icon") || icon.startsWith("night_"))
            if (isNight)
                weatherIcon = WeatherIcons.NIGHT_CLEAR;
            else
                weatherIcon = WeatherIcons.DAY_SUNNY;

        if (StringUtils.isNullOrWhitespace(weatherIcon)) {
            // Not Available
            weatherIcon = WeatherIcons.NA;
        }

        return weatherIcon;
    }
}
