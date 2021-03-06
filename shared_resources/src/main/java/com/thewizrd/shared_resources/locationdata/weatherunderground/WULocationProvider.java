package com.thewizrd.shared_resources.locationdata.weatherunderground;

import android.util.Log;

import com.thewizrd.shared_resources.controls.LocationQueryViewModel;
import com.thewizrd.shared_resources.locationdata.LocationProviderImpl;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.shared_resources.utils.Settings;
import com.thewizrd.shared_resources.utils.StringUtils;
import com.thewizrd.shared_resources.utils.WeatherException;
import com.thewizrd.shared_resources.utils.WeatherUtils;
import com.thewizrd.shared_resources.weatherdata.WeatherAPI;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class WULocationProvider extends LocationProviderImpl {

    @Override
    public String getLocationAPI() {
        return WeatherAPI.WEATHERUNDERGROUND;
    }

    @Override
    public boolean supportsLocale() {
        return true;
    }

    @Override
    public boolean isKeyRequired() {
        return false;
    }

    @Override
    public Collection<LocationQueryViewModel> getLocations(String ac_query, String weatherAPI) {
        List<LocationQueryViewModel> locations = null;

        String queryAPI = "https://autocomplete.wunderground.com/aq?query=";
        String options = "&h=0&cities=1";
        HttpURLConnection client = null;
        // Limit amount of results shown
        int maxResults = 10;

        try {
            // Connect to webstream
            URL queryURL = new URL(queryAPI + URLEncoder.encode(ac_query, "UTF-8") + options);
            client = (HttpURLConnection) queryURL.openConnection();
            client.setConnectTimeout(Settings.CONNECTION_TIMEOUT);
            client.setReadTimeout(Settings.READ_TIMEOUT);

            InputStream stream = client.getInputStream();

            // Load data
            locations = new ArrayList<>();
            AC_Rootobject root = JSONParser.deserializer(stream, AC_Rootobject.class);

            for (AC_RESULTS result : root.getResults()) {
                // Filter: only store city results
                if (!"city".equals(result.getType()))
                    continue;

                locations.add(new LocationQueryViewModel(result, weatherAPI));

                // Limit amount of results
                maxResults--;
                if (maxResults <= 0)
                    break;
            }

            // End Stream
            stream.close();
        } catch (Exception ex) {
            locations = new ArrayList<>();
            Logger.writeLine(Log.ERROR, ex, "WeatherUndergroundProvider: error getting locations");
        } finally {
            if (client != null)
                client.disconnect();
        }

        if (locations == null || locations.size() == 0) {
            locations = Collections.singletonList(new LocationQueryViewModel());
        }

        return locations;
    }

    @Override
    public LocationQueryViewModel getLocation(WeatherUtils.Coordinate coord, String weatherAPI) throws WeatherException {
        LocationQueryViewModel location = null;

        String queryAPI = "https://api.wunderground.com/auto/wui/geo/GeoLookupXML/index.xml?query=";
        String options = "";
        String query = String.format(Locale.ROOT, "%s,%s", coord.getLatitude(), coord.getLongitude());
        HttpURLConnection client = null;
        Location result = null;
        WeatherException wEx = null;

        try {
            // Connect to webstream
            URL queryURL = new URL(queryAPI + query + options);
            client = (HttpURLConnection) queryURL.openConnection();
            client.setConnectTimeout(Settings.CONNECTION_TIMEOUT);
            client.setReadTimeout(Settings.READ_TIMEOUT);

            InputStream stream = client.getInputStream();

            // Load data
            Serializer deserializer = new Persister();
            result = deserializer.read(Location.class, stream, false);

            // End Stream
            stream.close();
        } catch (Exception ex) {
            result = null;
            if (ex instanceof IOException) {
                wEx = new WeatherException(WeatherUtils.ErrorStatus.NETWORKERROR);
            }
            Logger.writeLine(Log.ERROR, ex, "WeatherUndergroundProvider: error getting location");
        } finally {
            if (client != null)
                client.disconnect();
        }

        if (wEx != null)
            throw wEx;

        if (result != null && !StringUtils.isNullOrWhitespace(result.getQuery()))
            location = new LocationQueryViewModel(result, weatherAPI);
        else
            location = new LocationQueryViewModel();

        return location;
    }

    @Override
    public boolean isKeyValid(String key) {
        return false;
    }

    @Override
    public String getAPIKey() {
        return null;
    }

}
