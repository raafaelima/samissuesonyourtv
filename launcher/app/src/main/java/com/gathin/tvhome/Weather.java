package com.gathin.tvhome;

import java.util.Locale;

/**
 * Optional weather widget in the top-right corner (config.xml weather): now, today's high and low and the next
 * hours, from
 * Open-Meteo (no key, no account). Only the place's coordinates, rounded to about a kilometre, are sent, over HTTPS,
 * and only while Home is on screen. MainActivity fetches and reads the JSON; the choices here are plain Java for the JVM tests.
 */
final class Weather {

    /** How often Home asks again while it stays on screen. */
    static final long REFRESH_MS = 30 * 60_000L;
    /** After a failed request (offline, a bad reply), how long until it tries again. */
    static final long RETRY_MS = 5 * 60_000L;

    /**
     * Whether to ask now, checked once a minute on the wall clock (Handler delays stop while the TV sleeps): never
     * answered, REFRESH_MS since the last answer, or a new hour began so the hours row moves on. A failure waits
     * RETRY_MS. Times are System.currentTimeMillis(), 0 for never; a clock set back counts as due.
     */
    static boolean due(long now, long fetchedAt, long triedAt, java.util.TimeZone zone) {
        if (triedAt > fetchedAt && now >= triedAt && now - triedAt < RETRY_MS) return false;
        if (fetchedAt == 0 || now < fetchedAt || now - fetchedAt >= REFRESH_MS) return true;
        return hour(now, zone) != hour(fetchedAt, zone);
    }

    private static long hour(long ms, java.util.TimeZone zone) {
        return Math.floorDiv(ms + zone.getOffset(ms), 3_600_000L);
    }

    /** The WMO weather codes Open-Meteo returns, folded into what the line can say. */
    enum Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

    /** Hours the widget's bottom row shows, starting with the next full hour. */
    static final int HOURS = 6;

    /** One hour of the bottom row. */
    static final class Hour {
        final String label;
        final Sky sky;
        final boolean day;
        final int temp;

        Hour(String label, Sky sky, boolean day, int temp) {
            this.label = label;
            this.sky = sky;
            this.day = day;
            this.temp = temp;
        }
    }

    /** One fetch, already reduced to what the widget shows. */
    static final class Forecast {
        final int temp;
        final Sky sky;
        final boolean day;
        final int high, low;
        final Hour[] hours;

        Forecast(int temp, Sky sky, boolean day, int high, int low, Hour[] hours) {
            this.temp = temp;
            this.sky = sky;
            this.day = day;
            this.high = high;
            this.low = low;
            this.hours = hours;
        }
    }

    private Weather() {
    }

    static String url(double latitude, double longitude) {
        return String.format(Locale.ROOT, "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                + "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day"
                + "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=2",
                latitude, longitude);
    }

    /** WMO 4677 codes as Open-Meteo documents them; anything unknown reads as cloudy rather than inventing rain. */
    static Sky sky(int wmo) {
        if (wmo == 0) return Sky.CLEAR;
        if (wmo == 1 || wmo == 2) return Sky.PARTLY;
        if (wmo == 45 || wmo == 48) return Sky.FOG;
        if (wmo >= 51 && wmo <= 57) return Sky.DRIZZLE;
        if (wmo >= 61 && wmo <= 67 || wmo >= 80 && wmo <= 82) return Sky.RAIN;
        if (wmo >= 71 && wmo <= 77 || wmo == 85 || wmo == 86) return Sky.SNOW;
        if (wmo >= 95 && wmo <= 99) return Sky.STORM;
        return Sky.CLOUDY;
    }

    /**
     * A one-colour symbol for the sky. U+FE0E asks for the text form, so the TV draws it in the line's colour
     * instead of a coloured emoji.
     */
    static String glyph(Sky sky, boolean day) {
        switch (sky) {
            case CLEAR: return day ? "☀︎" : "☾︎";
            case PARTLY: return day ? "⛅︎" : "☁︎";
            case DRIZZLE:
            case RAIN: return "☂︎";
            case SNOW: return "❄︎";
            case STORM: return "⛈︎";
            default: return "☁︎";
        }
    }

    /**
     * Index of the first hourly entry after now. Open-Meteo gives both as local ISO times ("2026-10-21T19:00",
     * "2026-10-21T18:15"), which sort as text; -1 when every entry is past.
     */
    static int firstHourAfter(String[] times, String now) {
        for (int i = 0; i < times.length; i++) if (times[i].compareTo(now) > 0) return i;
        return -1;
    }

    /** "19" from "2026-10-21T19:00"; the time itself when it has no hour. */
    static String hourLabel(String isoTime) {
        int t = isoTime.indexOf('T');
        return t >= 0 && isoTime.length() >= t + 3 ? isoTime.substring(t + 1, t + 3) : isoTime;
    }

    static String degrees(int value) {
        return value + "°";
    }

    /** Coordinates from config text; NaN when empty or not a number, which leaves the weather off. */
    static double coordinate(String text, double limit) {
        if (text == null || text.trim().isEmpty()) return Double.NaN;
        try {
            double v = Double.parseDouble(text.trim());
            return Math.abs(v) <= limit ? v : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
