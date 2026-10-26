package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * config.xml now_playing: fills the card under the clock from Spotify's media session, playing or paused, and hides it
 * once the session ends (Spotify closed, the TV restarted). It only listens to a session Spotify already has; it never
 * starts or wakes Spotify, and nothing is stored. Needs notification access for NowPlayingListener (the README gives
 * the one adb command). Nothing leaves the TV except the cover, when Spotify names it by https address.
 */
final class NowPlayingCard {

    /** The TV app and the phone app; anything else playing is not Spotify's business. */
    private static final List<String> PACKAGES = Arrays.asList("com.spotify.tv.android", "com.spotify.music");
    private static final long TICK_MS = 1000;

    private final Activity activity;
    private final View card;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ImageView backdrop, cover;
    private final TextView title, artist, elapsed, total;
    private final View progress, done;
    private final Runnable onShown;
    private final GradientDrawable edge = new GradientDrawable();

    private MediaSessionManager manager;
    private ComponentName listener;
    private MediaController controller;
    /** The controller's last state and song, from its callbacks: each getter is a call into Spotify that copies the cover. */
    private PlaybackState state;
    private MediaMetadata meta;
    private String shownArt = "";   // identifies the cover on screen, so the same song is not decoded again
    private String pkg = "";        // the app the song on the card came from
    private long duration, position;

    private final MediaSessionManager.OnActiveSessionsChangedListener onSessions = this::follow;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { meta = metadata; update(); }
        @Override public void onPlaybackStateChanged(PlaybackState playback) { state = playback; update(); }
    };
    /** While playing: only the clock and the bar move, worked out from the last state, with no call into Spotify. */
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (state == null) return;
            position = NowPlaying.position(state.getPosition(), state.getLastPositionUpdateTime(),
                    state.getPlaybackSpeed(), SystemClock.elapsedRealtime(), duration);
            showProgress();
            handler.postDelayed(this, TICK_MS);
        }
    };

    /** @param onShown runs once the card turns visible, so the activity can line it up with the dock band. */
    NowPlayingCard(Activity activity, View card, float density, int glassColor, Runnable onShown) {
        this.activity = activity;
        this.card = card;
        this.onShown = onShown;
        backdrop = card.findViewById(R.id.npBackdrop);
        cover = card.findViewById(R.id.npCover);
        title = card.findViewById(R.id.npTitle);
        artist = card.findViewById(R.id.npArtist);
        elapsed = card.findViewById(R.id.npElapsed);
        total = card.findViewById(R.id.npTotal);
        progress = card.findViewById(R.id.npProgress);
        done = card.findViewById(R.id.npDone);
        done.setPivotX(0);   // the bar grows by scale from its start, so a tick never lays the screen out again

        // The weather widget's glass: the same corners and edge line, with the cover blurred inside it.
        float radius = DockDecoration.CARD_RADIUS_DP * density;
        edge.setCornerRadius(radius);
        edge.setColor(0);
        edge.setStroke(Math.max(1, Math.round(density)), DockDecoration.EDGE_COLOR);
        card.setForeground(edge);
        card.setBackgroundColor(glassColor);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        // The cover behind everything, darkest under the text on the left and lighter towards the right.
        card.findViewById(R.id.npScrim).setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xE6000000, 0x73000000}));
        float cornerCover = 5 * density;
        cover.setClipToOutline(true);
        cover.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerCover);
            }
        });
        // The focus ring, like the tiles' lift: a brighter edge and a small zoom.
        card.setOnFocusChangeListener((v, focused) -> {
            edge.setStroke(Math.max(1, Math.round(density * (focused ? 2 : 1))), focused ? 0xE6FFFFFF : DockDecoration.EDGE_COLOR);
            v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(120).start();
        });
        card.setOnClickListener(v -> {
            if (!pkg.isEmpty()) RemoteKeys.launch(activity, pkg);
        });
    }

    void start() {
        manager = (MediaSessionManager) activity.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener = new ComponentName(activity, NowPlayingListener.class);
        try {
            manager.addOnActiveSessionsChangedListener(onSessions, listener);
            follow(manager.getActiveSessions(listener));
        } catch (SecurityException noAccess) {
            // notification access not granted: only the song from before, if any, is shown
        }
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
        if (manager != null) manager.removeOnActiveSessionsChangedListener(onSessions);
        if (controller != null) controller.unregisterCallback(callback);
        controller = null;
        state = null;
        meta = null;
    }

    /** Follows Spotify's session, if there is one. */
    private void follow(List<MediaController> sessions) {
        MediaController found = null;
        if (sessions != null) for (MediaController c : sessions) {
            if (PACKAGES.contains(c.getPackageName())) { found = c; break; }
        }
        if (controller != null && (found == null || !found.getSessionToken().equals(controller.getSessionToken()))) {
            controller.unregisterCallback(callback);
        }
        if (found != null && (controller == null || !found.getSessionToken().equals(controller.getSessionToken()))) {
            found.registerCallback(callback, handler);
        }
        controller = found;
        state = found == null ? null : found.getPlaybackState();
        meta = found == null ? null : found.getMetadata();
        update();
    }

    /** Shows the live song while Spotify plays or is paused, and hides the card once its session has ended. */
    private void update() {
        handler.removeCallbacks(tick);
        int s = state == null ? PlaybackState.STATE_NONE : state.getState();
        if (controller == null || s == PlaybackState.STATE_STOPPED || s == PlaybackState.STATE_ERROR) {
            hide();
            return;
        }
        // Spotify can name the song before it reports a state, and buffers between songs: the card waits as it is.
        boolean playing = s == PlaybackState.STATE_PLAYING;
        if (meta == null || !(playing || s == PlaybackState.STATE_PAUSED)) return;
        pkg = controller.getPackageName();
        title.setText(first(meta, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
        artist.setText(first(meta, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
        duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION);
        position = NowPlaying.position(state.getPosition(), state.getLastPositionUpdateTime(),
                state.getPlaybackSpeed(), SystemClock.elapsedRealtime(), duration);
        art(meta);
        show();
        if (playing) handler.postDelayed(tick, TICK_MS - position % TICK_MS);   // ticks on the song's own seconds
    }

    /** Puts title, artist, times and bar on the card from the fields, and makes the card visible. */
    private void show() {
        card.setContentDescription(title.getText() + ", " + artist.getText());
        progress.setVisibility(duration > 0 ? View.VISIBLE : View.INVISIBLE);
        total.setText(NowPlaying.time(duration));
        showProgress();
        if (card.getVisibility() != View.VISIBLE) {
            card.setVisibility(View.VISIBLE);
            card.post(onShown);
        }
    }

    private void hide() {
        if (card.getVisibility() != View.VISIBLE) return;
        if (card.hasFocus()) {   // focus goes back down to the dock rather than nowhere
            View below = card.focusSearch(View.FOCUS_DOWN);
            if (below != null) below.requestFocus();
        }
        card.setVisibility(View.GONE);
        pkg = "";
        shownArt = "";
        cover.setImageBitmap(null);
        backdrop.setImageBitmap(null);
    }

    private void showProgress() {
        elapsed.setText(NowPlaying.time(position));
        done.setScaleX(NowPlaying.fraction(position, duration));
    }

    private static String first(MediaMetadata meta, String... keys) {
        for (String key : keys) {
            CharSequence text = meta.getText(key);
            if (text != null && text.length() > 0) return text.toString();
        }
        return "";
    }

    /** The cover: a bitmap Spotify hands over, else the one its address names. Redone only when the song changes. */
    private void art(MediaMetadata meta) {
        // Spotify fills a song change in steps, and in between the only picture on offer is its own logo (the display
        // icon). So the display icon is never used, and with no cover yet the old one stays until the new one arrives.
        Bitmap bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bitmap == null) bitmap = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
        String uri = first(meta, MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI);
        if (bitmap == null && uri.isEmpty()) return;
        String id = bitmap != null ? "b" + bitmap.getGenerationId() + bitmap.getWidth() : uri;
        if (id.equals(shownArt)) return;
        shownArt = id;
        if (bitmap != null) {
            setArt(bitmap);
        } else {
            new Thread(() -> {
                Bitmap loaded = load(uri);
                if (loaded != null) handler.post(() -> { if (id.equals(shownArt)) setArt(loaded); });
            }, "now-playing-art").start();
        }
    }

    private void setArt(Bitmap bitmap) {
        cover.setImageBitmap(bitmap);
        backdrop.setImageBitmap(bitmap == null ? null : blurred(bitmap));
    }

    /**
     * A soft copy of the cover for the background: shrunk to 160 pixels, then box-blurred twice (about a Gaussian), so the
     * view stretches smooth pixels, not blocks. 160x160 costs a few milliseconds.
     */
    private static Bitmap blurred(Bitmap src) {
        int w = 160, h = Math.max(1, w * src.getHeight() / src.getWidth());
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 2; pass++) {
            px = box(px, w, h, 2, true);
            px = box(px, w, h, 2, false);
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    /** One box-blur pass along rows or columns, the window clamped at the edges. */
    private static int[] box(int[] px, int w, int h, int r, boolean rows) {
        int[] out = new int[px.length];
        int length = rows ? w : h, lines = rows ? h : w;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < length; i++) {
                int a = 0, red = 0, green = 0, blue = 0, n = 0;
                for (int k = Math.max(0, i - r); k <= Math.min(length - 1, i + r); k++) {
                    int c = rows ? px[line * w + k] : px[k * w + line];
                    a += c >>> 24; red += c >> 16 & 0xFF; green += c >> 8 & 0xFF; blue += c & 0xFF; n++;
                }
                int c = (a / n) << 24 | (red / n) << 16 | (green / n) << 8 | blue / n;
                if (rows) out[line * w + i] = c; else out[i * w + line] = c;
            }
        }
        return out;
    }

    private Bitmap load(String address) {
        try {
            Uri uri = Uri.parse(address);
            if ("https".equals(uri.getScheme())) {
                HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(10_000);   // as the weather's: a stalled network never holds the thread
                connection.setReadTimeout(10_000);
                try (InputStream in = connection.getInputStream()) {
                    return BitmapFactory.decodeStream(in);
                } finally {
                    connection.disconnect();
                }
            }
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                return in == null ? null : BitmapFactory.decodeStream(in);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
