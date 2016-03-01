package com.example.android.sunshine.app;

/**
 * Created by hamilton.freitas on 2016-02-29.
 */
public class WearUtility {

    public static int getArtResourceForWeatherCondition(int weatherId) {

        if (weatherId >= 200 && weatherId <= 232) {
            return com.example.android.sunshine.app.R.mipmap.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return com.example.android.sunshine.app.R.mipmap.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return com.example.android.sunshine.app.R.mipmap.ic_rain;
        } else if (weatherId == 511) {
            return com.example.android.sunshine.app.R.mipmap.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return com.example.android.sunshine.app.R.mipmap.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return com.example.android.sunshine.app.R.mipmap.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return com.example.android.sunshine.app.R.mipmap.ic_fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return com.example.android.sunshine.app.R.mipmap.ic_storm;
        } else if (weatherId == 800) {
            return com.example.android.sunshine.app.R.mipmap.ic_clear;
        } else if (weatherId == 801) {
            return com.example.android.sunshine.app.R.mipmap.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return com.example.android.sunshine.app.R.mipmap.ic_cloudy;
        }
        return -1;
    }
}
