package com.thewizrd.shared_resources.weatherdata;

import android.util.Log;

import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.thewizrd.shared_resources.utils.ConversionMethods;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.shared_resources.utils.StringUtils;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

public class Condition {

    @SerializedName("weather")
    private String weather;

    @SerializedName("temp_f")
    private double tempF;

    @SerializedName("temp_c")
    private double tempC;

    @SerializedName("wind_degrees")
    private int windDegrees;

    @SerializedName("wind_mph")
    private double windMph;

    @SerializedName("wind_kph")
    private double windKph;

    @SerializedName("feelslike_f")
    private double feelslikeF;

    @SerializedName("feelslike_c")
    private double feelslikeC;

    @SerializedName("icon")
    private String icon;

    private Condition() {
        // Needed for deserialization
    }

    public Condition(com.thewizrd.shared_resources.weatherdata.weatherunderground.CurrentObservation condition) {
        weather = condition.getWeather();
        tempF = condition.getTempF();
        tempC = condition.getTempC();
        windDegrees = condition.getWindDegrees();
        windMph = condition.getWindMph();
        windKph = condition.getWindKph();
        feelslikeF = condition.getFeelslikeF();
        feelslikeC = condition.getFeelslikeC();
        icon = WeatherManager.getProvider(WeatherAPI.WEATHERUNDERGROUND)
                .getWeatherIcon(condition.getIconUrl().replace("http://icons.wxug.com/i/c/k/", "").replace(".gif", ""));
    }

    public Condition(com.thewizrd.shared_resources.weatherdata.weatheryahoo.Channel channel) {
        weather = channel.getItem().getCondition().getText();
        tempF = Float.valueOf(channel.getItem().getCondition().getTemp());
        tempC = Float.valueOf(ConversionMethods.FtoC(channel.getItem().getCondition().getTemp()));
        windDegrees = Integer.valueOf(channel.getWind().getDirection());
        windKph = Float.valueOf(channel.getWind().getSpeed());
        windMph = Float.valueOf(ConversionMethods.kphTomph(channel.getWind().getSpeed()));
        feelslikeF = Float.valueOf(channel.getWind().getChill());
        feelslikeC = Float.valueOf(ConversionMethods.FtoC(channel.getWind().getChill()));
        icon = WeatherManager.getProvider(WeatherAPI.YAHOO)
                .getWeatherIcon(channel.getItem().getCondition().getCode());
    }

    public Condition(com.thewizrd.shared_resources.weatherdata.openweather.CurrentRootobject root) {
        weather = StringUtils.toUpperCase(root.getWeather().get(0).getDescription());
        tempF = Float.valueOf(ConversionMethods.KtoF(Float.toString(root.getMain().getTemp())));
        tempC = Float.valueOf(ConversionMethods.KtoC(Float.toString(root.getMain().getTemp())));
        windDegrees = (int) root.getWind().getDeg();
        windMph = Float.valueOf(ConversionMethods.msecToMph(Float.toString(root.getWind().getSpeed())));
        windKph = Float.valueOf(ConversionMethods.msecToKph(Float.toString(root.getWind().getSpeed())));
        // This will be calculated after with formula
        feelslikeF = tempF;
        feelslikeC = tempC;

        String ico = root.getWeather().get(0).getIcon();
        String dn = Character.toString(ico.charAt(ico.length() == 0 ? 0 : ico.length() - 1));

        try {
            int x = Integer.valueOf(dn);
            dn = "";
        } catch (NumberFormatException ex) {
            // DO nothing
        }

        icon = WeatherManager.getProvider(WeatherAPI.OPENWEATHERMAP)
                .getWeatherIcon(root.getWeather().get(0).getId() + dn);
    }

    public Condition(com.thewizrd.shared_resources.weatherdata.metno.Weatherdata.Time time) {
        // weather
        tempF = Float.valueOf(ConversionMethods.CtoF(time.getLocation().getTemperature().getValue().toString()));
        tempC = time.getLocation().getTemperature().getValue().floatValue();
        windDegrees = Math.round(time.getLocation().getWindDirection().getDeg().floatValue());
        windMph = (float) Math.round(Double.valueOf(ConversionMethods.msecToMph(time.getLocation().getWindSpeed().getMps().toString())));
        windKph = (float) Math.round(Double.valueOf(ConversionMethods.msecToKph(time.getLocation().getWindSpeed().getMps().toString())));
        // This will be calculated after with formula
        feelslikeF = tempF;
        feelslikeC = tempC;
        // icon
    }

    public Condition(com.thewizrd.shared_resources.weatherdata.here.ObservationItem observation) {
        weather = StringUtils.toPascalCase(observation.getDescription());
        try {
            Float tempF = Float.valueOf(observation.getTemperature());
            this.tempF = tempF;
            tempC = Float.valueOf(ConversionMethods.FtoC(tempF.toString()));
        } catch (NumberFormatException ex) {
            this.tempF = 0.00f;
            tempC = 0.00f;
        }

        try {
            this.windDegrees = Integer.valueOf(observation.getWindDirection());
        } catch (NumberFormatException ex) {
            this.windDegrees = 0;
        }

        try {
            windMph = Float.valueOf(observation.getWindSpeed());
            windKph = Float.valueOf(ConversionMethods.mphTokph(observation.getWindSpeed()));
        } catch (NumberFormatException ex) {
            windMph = 0.00f;
            windKph = 0.00f;
        }

        try {
            Float comfortTempF = Float.valueOf(observation.getComfort());
            feelslikeF = comfortTempF;
            feelslikeC = Float.valueOf(ConversionMethods.FtoC(comfortTempF.toString()));
        } catch (NumberFormatException ex) {
            feelslikeF = 0.00f;
            feelslikeC = 0.00f;
        }

        icon = WeatherManager.getProvider(WeatherAPI.HERE)
                .getWeatherIcon(String.format("%s_%s", observation.getDaylight(), observation.getIconName()));
    }

    public String getWeather() {
        return weather;
    }

    public void setWeather(String weather) {
        this.weather = weather;
    }

    public double getTempF() {
        return tempF;
    }

    public void setTempF(double tempF) {
        this.tempF = tempF;
    }

    public double getTempC() {
        return tempC;
    }

    public void setTempC(double tempC) {
        this.tempC = tempC;
    }

    public int getWindDegrees() {
        return windDegrees;
    }

    public void setWindDegrees(int windDegrees) {
        this.windDegrees = windDegrees;
    }

    public double getWindMph() {
        return windMph;
    }

    public void setWindMph(double windMph) {
        this.windMph = windMph;
    }

    public double getWindKph() {
        return windKph;
    }

    public void setWindKph(double windKph) {
        this.windKph = windKph;
    }

    public double getFeelslikeF() {
        return feelslikeF;
    }

    public void setFeelslikeF(double feelslikeF) {
        this.feelslikeF = feelslikeF;
    }

    public double getFeelslikeC() {
        return feelslikeC;
    }

    public void setFeelslikeC(double feelslikeC) {
        this.feelslikeC = feelslikeC;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public static Condition fromJson(JsonReader extReader) {
        Condition obj = null;

        try {
            obj = new Condition();
            JsonReader reader;
            String jsonValue;

            if (extReader.peek() == JsonToken.STRING) {
                jsonValue = extReader.nextString();
            } else {
                jsonValue = null;
            }

            if (jsonValue == null)
                reader = extReader;
            else {
                reader = new JsonReader(new StringReader(jsonValue));
                reader.beginObject(); // StartObject
            }

            while (reader.hasNext() && reader.peek() != JsonToken.END_OBJECT) {
                if (reader.peek() == JsonToken.BEGIN_OBJECT)
                    reader.beginObject(); // StartObject

                String property = reader.nextName();

                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                    continue;
                }

                switch (property) {
                    case "weather":
                        obj.weather = reader.nextString();
                        break;
                    case "temp_f":
                        obj.tempF = Float.valueOf(reader.nextString());
                        break;
                    case "temp_c":
                        obj.tempC = Float.valueOf(reader.nextString());
                        break;
                    case "wind_degrees":
                        obj.windDegrees = Integer.valueOf(reader.nextString());
                        break;
                    case "wind_mph":
                        obj.windMph = Float.valueOf(reader.nextString());
                        break;
                    case "wind_kph":
                        obj.windKph = Float.valueOf(reader.nextString());
                        break;
                    case "feelslike_f":
                        obj.feelslikeF = Float.valueOf(reader.nextString());
                        break;
                    case "feelslike_c":
                        obj.feelslikeC = Float.valueOf(reader.nextString());
                        break;
                    case "icon":
                        obj.icon = reader.nextString();
                        break;
                    default:
                        break;
                }
            }

            if (reader.peek() == JsonToken.END_OBJECT)
                reader.endObject();

        } catch (Exception ex) {
            obj = null;
        }

        return obj;
    }

    public String toJson() {
        StringWriter sw = new StringWriter();
        JsonWriter writer = new JsonWriter(sw);
        writer.setSerializeNulls(true);

        try {
            // {
            writer.beginObject();

            // "weather" : ""
            writer.name("weather");
            writer.value(weather);

            // "temp_f" : ""
            writer.name("temp_f");
            writer.value(tempF);

            // "temp_c" : ""
            writer.name("temp_c");
            writer.value(tempC);

            // "wind_degrees" : ""
            writer.name("wind_degrees");
            writer.value(windDegrees);

            // "wind_mph" : ""
            writer.name("wind_mph");
            writer.value(windMph);

            // "wind_kph" : ""
            writer.name("wind_kph");
            writer.value(windKph);

            // "feelslike_f" : ""
            writer.name("feelslike_f");
            writer.value(feelslikeF);

            // "feelslike_c" : ""
            writer.name("feelslike_c");
            writer.value(feelslikeC);

            // "icon" : ""
            writer.name("icon");
            writer.value(icon);

            // }
            writer.endObject();
        } catch (IOException e) {
            Logger.writeLine(Log.ERROR, e, "Condition: error writing json string");
        }

        return sw.toString();
    }
}