package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Weather: what the WMO codes read as and what leaves the TV. */
public class WeatherTest {

    @Test
    public void wmoCodesFoldIntoTheSkiesTheLineNames() {
        assertEquals(Weather.Sky.CLEAR, Weather.sky(0));
        assertEquals(Weather.Sky.PARTLY, Weather.sky(2));
        assertEquals(Weather.Sky.CLOUDY, Weather.sky(3));
        assertEquals(Weather.Sky.FOG, Weather.sky(45));
        assertEquals(Weather.Sky.DRIZZLE, Weather.sky(53));
        assertEquals(Weather.Sky.RAIN, Weather.sky(63));
        assertEquals(Weather.Sky.RAIN, Weather.sky(81));
        assertEquals(Weather.Sky.SNOW, Weather.sky(75));
        assertEquals(Weather.Sky.SNOW, Weather.sky(86));
        assertEquals(Weather.Sky.STORM, Weather.sky(95));
    }

    @Test
    public void anUnknownCodeReadsAsCloudyNotRain() {
        assertEquals(Weather.Sky.CLOUDY, Weather.sky(42));
        assertEquals(Weather.Sky.CLOUDY, Weather.sky(-1));
    }

    @Test
    public void glyphsAskForTheTextForm() {
        for (Weather.Sky sky : Weather.Sky.values()) {
            assertTrue(sky.name(), Weather.glyph(sky, true).endsWith("︎"));
            assertTrue(sky.name(), Weather.glyph(sky, false).endsWith("︎"));
        }
        // A clear night is a moon, not a sun.
        assertFalse(Weather.glyph(Weather.Sky.CLEAR, false).equals(Weather.glyph(Weather.Sky.CLEAR, true)));
    }

    @Test
    public void onlyRoundedCoordinatesGoOutOverHttps() {
        String url = Weather.url(38.7223, -9.1393);
        assertTrue(url, url.startsWith("https://api.open-meteo.com/"));
        assertTrue(url, url.contains("latitude=38.72&longitude=-9.14"));
    }

    @Test
    public void theHoursStartAfterNowAndCrossMidnight() {
        String[] times = {"2026-10-21T17:00", "2026-10-21T18:00", "2026-10-21T19:00", "2026-10-21T23:00",
                "2026-10-22T00:00"};
        assertEquals(2, Weather.firstHourAfter(times, "2026-10-21T18:15"));
        assertEquals(2, Weather.firstHourAfter(times, "2026-10-21T18:00"));
        assertEquals(4, Weather.firstHourAfter(times, "2026-10-21T23:45"));
        assertEquals(-1, Weather.firstHourAfter(times, "2026-10-22T00:30"));
    }

    @Test
    public void hourLabelsAreTheTwoDigitHour() {
        assertEquals("19", Weather.hourLabel("2026-10-21T19:00"));
        assertEquals("00", Weather.hourLabel("2026-10-22T00:00"));
        assertEquals("odd", Weather.hourLabel("odd"));
    }

    @Test
    public void coordinatesOutOfRangeOrBlankTurnTheWeatherOff() {
        assertEquals(38.72, Weather.coordinate(" 38.72 ", 90), 1e-9);
        assertTrue(Double.isNaN(Weather.coordinate("", 90)));
        assertTrue(Double.isNaN(Weather.coordinate(null, 90)));
        assertTrue(Double.isNaN(Weather.coordinate("Lisbon", 90)));
        assertTrue(Double.isNaN(Weather.coordinate("91", 90)));
    }

    @Test
    public void asksOnANewHourAfterRefreshOrAfterAFailureHasWaited() {
        java.util.TimeZone lisbon = java.util.TimeZone.getTimeZone("Europe/Lisbon");
        long h = 3_600_000L, m = 60_000L;
        long t1105 = 1_793_012_700_000L;   // 2026-10-26 11:05 in Lisbon (UTC+0)
        assertTrue(Weather.due(t1105, 0, 0, lisbon));                          // never asked
        assertFalse(Weather.due(t1105 + 20 * m, t1105, t1105, lisbon));       // 11:25, fresh
        assertTrue(Weather.due(t1105 + 55 * m, t1105 + 50 * m, t1105 + 50 * m, lisbon));   // 12:00, the hours row moves
        assertTrue(Weather.due(t1105 + 30 * m, t1105, t1105, lisbon));        // 11:35, REFRESH_MS
        assertTrue(Weather.due(t1105 - m, t1105, t1105, lisbon));             // clock set back
        assertFalse(Weather.due(t1105 + 2 * m, 0, t1105, lisbon));            // failed 2 minutes ago
        assertTrue(Weather.due(t1105 + 5 * m, 0, t1105, lisbon));             // and retries after 5
        assertFalse(Weather.due(t1105 + h + 2 * m, t1105, t1105 + h, lisbon));   // old answer, fresh failure
    }
}
