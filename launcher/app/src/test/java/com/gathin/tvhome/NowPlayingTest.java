package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Now playing: the position keeps moving between reports and the times read like a player's. */
public class NowPlayingTest {

    @Test
    public void positionAdvancesAtTheSpeedAndStopsAtTheEnd() {
        assertEquals(12_000, NowPlaying.position(10_000, 1_000, 1f, 3_000, 200_000));
        assertEquals(10_000, NowPlaying.position(10_000, 1_000, 0f, 3_000, 200_000));
        assertEquals(200_000, NowPlaying.position(199_000, 0, 1f, 5_000, 200_000));
        assertEquals(15_000, NowPlaying.position(10_000, 0, 1f, 5_000, 0));
    }

    @Test
    public void fractionIsClampedAndZeroWithoutALength() {
        assertEquals(0.5f, NowPlaying.fraction(100, 200), 1e-6);
        assertEquals(1f, NowPlaying.fraction(300, 200), 1e-6);
        assertEquals(0f, NowPlaying.fraction(100, 0), 1e-6);
    }

    @Test
    public void timesReadMinutesAndSeconds() {
        assertEquals("0:00", NowPlaying.time(-5));
        assertEquals("2:32", NowPlaying.time(152_900));
        assertEquals("3:55", NowPlaying.time(235_000));
        assertEquals("1:02:05", NowPlaying.time(3_725_000));
    }
}
