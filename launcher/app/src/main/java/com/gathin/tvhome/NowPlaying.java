package com.gathin.tvhome;

import java.util.Locale;

/** The widget's plain arithmetic: where the song is and how to write it; the JVM tests cover it. */
final class NowPlaying {

    private NowPlaying() {
    }

    /** The playback position now: the last reported one plus the time since, at the playback speed, capped at the end. */
    static long position(long reported, long reportedAt, float speed, long now, long duration) {
        long p = reported + Math.round((now - reportedAt) * speed);
        if (duration > 0) p = Math.min(p, duration);
        return Math.max(0, p);
    }

    /** 0 to 1; 0 when the length is unknown. */
    static float fraction(long position, long duration) {
        return duration > 0 ? Math.max(0f, Math.min(1f, position / (float) duration)) : 0f;
    }

    /** "2:32", or "1:02:05" past an hour. */
    static String time(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
