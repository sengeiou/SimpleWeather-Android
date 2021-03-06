package com.thewizrd.shared_resources.weatherdata;

import android.util.Log;

import androidx.annotation.RestrictTo;

import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.thewizrd.shared_resources.utils.CustomJsonObject;
import com.thewizrd.shared_resources.utils.DateTimeUtils;
import com.thewizrd.shared_resources.utils.Logger;

import org.threeten.bp.LocalDate;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.LocalTime;
import org.threeten.bp.ZoneOffset;
import org.threeten.bp.ZonedDateTime;
import org.threeten.bp.format.DateTimeFormatter;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Locale;

public class Astronomy extends CustomJsonObject {

    @SerializedName("sunrise")
    private LocalDateTime sunrise;

    @SerializedName("sunset")
    private LocalDateTime sunset;

    @SerializedName("moonrise")
    private LocalDateTime moonrise;

    @SerializedName("moonset")
    private LocalDateTime moonset;

    @SerializedName("moonphase")
    private MoonPhase moonPhase;

    @RestrictTo({RestrictTo.Scope.LIBRARY})
    public Astronomy() {
        // Needed for deserialization
    }

    public Astronomy(com.thewizrd.shared_resources.weatherdata.weatherunderground.SunPhase sun_phase,
                     com.thewizrd.shared_resources.weatherdata.weatherunderground.MoonPhase moonPhase) {
        LocalDate now = LocalDate.now();

        try {
            sunrise = LocalTime.parse(String.format("%s:%s",
                    sun_phase.getSunrise().getHour(), sun_phase.getSunrise().getMinute()),
                    DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)).atDate(now);
        } catch (Exception e) {
            Logger.writeLine(Log.DEBUG, e);
        }
        try {
            sunset = LocalTime.parse(String.format("%s:%s",
                    sun_phase.getSunset().getHour(), sun_phase.getSunset().getMinute()),
                    DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)).atDate(now);
        } catch (Exception e) {
            Logger.writeLine(Log.DEBUG, e);
        }

        try {
            moonrise = LocalTime.parse(String.format("%s:%s",
                    moonPhase.getMoonrise().getHour(), moonPhase.getMoonrise().getMinute()),
                    DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)).atDate(now);
        } catch (Exception e) {
            Logger.writeLine(Log.DEBUG, e);
        }
        try {
            moonset = LocalTime.parse(String.format("%s:%s",
                    moonPhase.getMoonset().getHour(), moonPhase.getMoonset().getMinute()),
                    DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)).atDate(now);
        } catch (Exception e) {
            Logger.writeLine(Log.DEBUG, e);
        }

        // If the sun won't set/rise, set time to the future
        if (sunrise == null) {
            sunrise = LocalDateTime.now().plusYears(1).minusNanos(1);
        }
        if (sunset == null) {
            sunset = LocalDateTime.now().plusYears(1).minusNanos(1);
        }
        if (moonrise == null) {
            moonrise = DateTimeUtils.getLocalDateTimeMIN();
        }
        if (moonset == null) {
            moonset = DateTimeUtils.getLocalDateTimeMIN();
        }

        try {
            MoonPhase.MoonPhaseType moonPhaseType;

            int ageOfMoon = Integer.parseInt(moonPhase.getAgeOfMoon());
            if (ageOfMoon >= 2 && ageOfMoon < 8) {
                moonPhaseType = MoonPhase.MoonPhaseType.WAXING_CRESCENT;
            } else if (ageOfMoon == 8) {
                moonPhaseType = MoonPhase.MoonPhaseType.FIRST_QTR;
            } else if (ageOfMoon >= 9 && ageOfMoon < 16) {
                moonPhaseType = MoonPhase.MoonPhaseType.WAXING_GIBBOUS;
            } else if (ageOfMoon == 16) {
                moonPhaseType = MoonPhase.MoonPhaseType.FULL_MOON;
            } else if (ageOfMoon >= 17 && ageOfMoon < 23) {
                moonPhaseType = MoonPhase.MoonPhaseType.WANING_GIBBOUS;
            } else if (ageOfMoon == 23) {
                moonPhaseType = MoonPhase.MoonPhaseType.LAST_QTR;
            } else if (ageOfMoon >= 24 && ageOfMoon < 29) {
                moonPhaseType = MoonPhase.MoonPhaseType.WANING_CRESCENT;
            } else {
                moonPhaseType = MoonPhase.MoonPhaseType.NEWMOON;
            }

            this.moonPhase = new MoonPhase(moonPhaseType, moonPhase.getPhaseofMoon());
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
        }
    }

    public Astronomy(com.thewizrd.shared_resources.weatherdata.weatheryahoo.Astronomy astronomy) {
        LocalDate now = LocalDate.now();

        try {
            sunrise = LocalTime.parse(astronomy.getSunrise().toUpperCase(), DateTimeFormatter.ofPattern("h:m a", Locale.ROOT)).atDate(now);
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
        }
        try {
            sunset = LocalTime.parse(astronomy.getSunset().toUpperCase(), DateTimeFormatter.ofPattern("h:m a", Locale.ROOT)).atDate(now);
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
        }

        // If the sun won't set/rise, set time to the future
        if (sunrise == null) {
            sunrise = LocalDateTime.now().plusYears(1).minusNanos(1);
        }
        if (sunset == null) {
            sunset = LocalDateTime.now().plusYears(1).minusNanos(1);
        }
        if (moonrise == null) {
            moonrise = DateTimeUtils.getLocalDateTimeMIN();
        }
        if (moonset == null) {
            moonset = DateTimeUtils.getLocalDateTimeMIN();
        }
    }

    public Astronomy(com.thewizrd.shared_resources.weatherdata.openweather.CurrentRootobject root) {
        try {
            sunrise = LocalDateTime.ofEpochSecond(root.getSys().getSunrise(), 0, ZoneOffset.UTC);
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
        }
        try {
            sunset = LocalDateTime.ofEpochSecond(root.getSys().getSunset(), 0, ZoneOffset.UTC);
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
        }

        // If the sun won't set/rise, set time to the future
        if (sunrise == null) {
            sunrise = LocalDateTime.now().plusYears(1).minusNanos(1);
        }
        if (sunset == null) {
            sunset = LocalDateTime.now().plusYears(1).minusNanos(1);
        }
        if (moonrise == null) {
            moonrise = DateTimeUtils.getLocalDateTimeMIN();
        }
        if (moonset == null) {
            moonset = DateTimeUtils.getLocalDateTimeMIN();
        }
    }

    public Astronomy(com.thewizrd.shared_resources.weatherdata.metno.Astrodata astroRoot) {
        int moonPhaseValue = -1;

        for (com.thewizrd.shared_resources.weatherdata.metno.Astrodata.Time time : astroRoot.getLocation().getTimes()) {
            if (time.getSunrise() != null) {
                sunrise = ZonedDateTime.parse(astroRoot.getLocation().getTimes().get(0).getSunrise().getTime(),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
            }
            if (time.getSunset() != null) {
                sunset = ZonedDateTime.parse(astroRoot.getLocation().getTimes().get(0).getSunset().getTime(),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
            }

            if (time.getMoonrise() != null) {
                moonrise = ZonedDateTime.parse(astroRoot.getLocation().getTimes().get(0).getMoonrise().getTime(),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
            }
            if (time.getMoonset() != null) {
                moonset = ZonedDateTime.parse(astroRoot.getLocation().getTimes().get(0).getMoonset().getTime(),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
            }

            if (time.getMoonphase() != null)
                moonPhaseValue = Math.round(time.getMoonphase().getValue().floatValue());
        }

        // If the sun won't set/rise, set time to the future
        if (sunrise == null) {
            sunrise = LocalDateTime.now().plusYears(1).minusNanos(1);
        }
        if (sunset == null) {
            sunset = LocalDateTime.now().plusYears(1).minusNanos(1);
        }
        if (moonrise == null) {
            moonrise = DateTimeUtils.getLocalDateTimeMIN();
        }
        if (moonset == null) {
            moonset = DateTimeUtils.getLocalDateTimeMIN();
        }

        MoonPhase.MoonPhaseType moonPhaseType;
        if (moonPhaseValue >= 2 && moonPhaseValue < 23) {
            moonPhaseType = MoonPhase.MoonPhaseType.WAXING_CRESCENT;
        } else if (moonPhaseValue >= 23 && moonPhaseValue < 26) {
            moonPhaseType = MoonPhase.MoonPhaseType.FIRST_QTR;
        } else if (moonPhaseValue >= 26 && moonPhaseValue < 48) {
            moonPhaseType = MoonPhase.MoonPhaseType.WAXING_GIBBOUS;
        } else if (moonPhaseValue >= 48 && moonPhaseValue < 52) {
            moonPhaseType = MoonPhase.MoonPhaseType.FULL_MOON;
        } else if (moonPhaseValue >= 52 && moonPhaseValue < 73) {
            moonPhaseType = MoonPhase.MoonPhaseType.WANING_GIBBOUS;
        } else if (moonPhaseValue >= 73 && moonPhaseValue < 76) {
            moonPhaseType = MoonPhase.MoonPhaseType.LAST_QTR;
        } else if (moonPhaseValue >= 76 && moonPhaseValue < 98) {
            moonPhaseType = MoonPhase.MoonPhaseType.WANING_CRESCENT;
        } else { // 0, 1, 98, 99, 100
            moonPhaseType = MoonPhase.MoonPhaseType.NEWMOON;
        }

        this.moonPhase = new MoonPhase(moonPhaseType);
    }

    public Astronomy(List<com.thewizrd.shared_resources.weatherdata.here.AstronomyItem> astronomy) {
        com.thewizrd.shared_resources.weatherdata.here.AstronomyItem astroData = astronomy.get(0);

        LocalDate now = LocalDate.now();

        try {
            sunrise = LocalTime.parse(astroData.getSunrise(), DateTimeFormatter.ofPattern("h:mma", Locale.ROOT)).atDate(now);
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
        }
        try {
            sunset = LocalTime.parse(astroData.getSunset(), DateTimeFormatter.ofPattern("h:mma", Locale.ROOT)).atDate(now);
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
        }
        try {
            moonrise = LocalTime.parse(astroData.getMoonrise(), DateTimeFormatter.ofPattern("h:mma", Locale.ROOT)).atDate(now);
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
        }
        try {
            moonset = LocalTime.parse(astroData.getMoonset(), DateTimeFormatter.ofPattern("h:mma", Locale.ROOT)).atDate(now);
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
        }

        // If the sun won't set/rise, set time to the future
        if (sunrise == null) {
            sunrise = LocalDateTime.now().plusYears(1).minusNanos(1);
        }
        if (sunset == null) {
            sunset = LocalDateTime.now().plusYears(1).minusNanos(1);
        }
        if (moonrise == null) {
            moonrise = DateTimeUtils.getLocalDateTimeMIN();
        }
        if (moonset == null) {
            moonset = DateTimeUtils.getLocalDateTimeMIN();
        }

        switch (astroData.getIconName()) {
            case "cw_new_moon":
            default:
                this.moonPhase = new MoonPhase(MoonPhase.MoonPhaseType.NEWMOON,
                        astroData.getMoonPhaseDesc());
                break;
            case "cw_waxing_crescent":
                this.moonPhase = new MoonPhase(MoonPhase.MoonPhaseType.WAXING_CRESCENT,
                        astroData.getMoonPhaseDesc());
                break;
            case "cw_first_qtr":
                this.moonPhase = new MoonPhase(MoonPhase.MoonPhaseType.FIRST_QTR,
                        astroData.getMoonPhaseDesc());
                break;
            case "cw_waxing_gibbous":
                this.moonPhase = new MoonPhase(MoonPhase.MoonPhaseType.WAXING_GIBBOUS,
                        astroData.getMoonPhaseDesc());
                break;
            case "cw_full_moon":
                this.moonPhase = new MoonPhase(MoonPhase.MoonPhaseType.FULL_MOON,
                        astroData.getMoonPhaseDesc());
                break;
            case "cw_waning_gibbous":
                this.moonPhase = new MoonPhase(MoonPhase.MoonPhaseType.WANING_GIBBOUS,
                        astroData.getMoonPhaseDesc());
                break;
            case "cw_last_quarter":
                this.moonPhase = new MoonPhase(MoonPhase.MoonPhaseType.LAST_QTR,
                        astroData.getMoonPhaseDesc());
                break;
            case "cw_waning_crescent":
                this.moonPhase = new MoonPhase(MoonPhase.MoonPhaseType.WANING_CRESCENT,
                        astroData.getMoonPhaseDesc());
                break;
        }
    }

    public LocalDateTime getSunrise() {
        return sunrise;
    }

    public void setSunrise(LocalDateTime sunrise) {
        this.sunrise = sunrise;
    }

    public LocalDateTime getSunset() {
        return sunset;
    }

    public void setSunset(LocalDateTime sunset) {
        this.sunset = sunset;
    }

    public LocalDateTime getMoonrise() {
        return moonrise;
    }

    public void setMoonrise(LocalDateTime moonrise) {
        this.moonrise = moonrise;
    }

    public LocalDateTime getMoonset() {
        return moonset;
    }

    public void setMoonset(LocalDateTime moonset) {
        this.moonset = moonset;
    }

    public MoonPhase getMoonPhase() {
        return moonPhase;
    }

    public void setMoonPhase(MoonPhase moonPhase) {
        this.moonPhase = moonPhase;
    }

    @Override
    public void fromJson(JsonReader extReader) {
        try {
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
                    case "sunrise":
                        this.sunrise = LocalDateTime.parse(reader.nextString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        break;
                    case "sunset":
                        this.sunset = LocalDateTime.parse(reader.nextString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        break;
                    case "moonrise":
                        this.moonrise = LocalDateTime.parse(reader.nextString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        break;
                    case "moonset":
                        this.moonset = LocalDateTime.parse(reader.nextString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        break;
                    case "moonphase":
                        this.moonPhase = new MoonPhase();
                        this.moonPhase.fromJson(reader);
                        break;
                    default:
                        break;
                }
            }

            if (reader.peek() == JsonToken.END_OBJECT)
                reader.endObject();

        } catch (Exception ignored) {
        }
    }

    @Override
    public void toJson(JsonWriter writer) {
        try {
            // {
            writer.beginObject();

            // "sunrise" : ""
            writer.name("sunrise");
            writer.value(sunrise.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // "sunset" : ""
            writer.name("sunset");
            writer.value(sunset.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // "moonrise" : ""
            writer.name("moonrise");
            writer.value(moonrise.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // "moonset" : ""
            writer.name("moonset");
            writer.value(moonset.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // "moonphase" : ""
            if (moonPhase != null) {
                writer.name("moonphase");
                if (moonPhase == null)
                    writer.nullValue();
                else
                    moonPhase.toJson(writer);
            }

            // }
            writer.endObject();
        } catch (IOException e) {
            Logger.writeLine(Log.ERROR, e, "Astronomy: error writing json string");
        }
    }
}