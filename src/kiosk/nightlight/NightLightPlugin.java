// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

import android.content.Context;
import android.view.View;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * A night light for a child's room, drawn as a Kiosk Satellite screensaver. It opens on a dim warm light. A button
 * on it starts the go to sleep show: sleepy music while the screen drifts through soft colours, which fades back to
 * the warm light when it is over or when End is pressed. KS still decides when the screensaver starts and ends; this
 * plugin only draws it and plays the music. A double tap closes it.
 */
public final class NightLightPlugin implements KioskPlugin, GlowView.Source, AudioOut.Source, SleepButton.Source {
    static final String ID = "night-light";
    static final String KEY = "nightlight";
    static final String MODE = "plugin:" + ID + ":" + KEY;
    static final String READY = "Ready. Choose Night Light under Screensaver mode, or run the Start night light action.";
    /** KS can rebuild the view (rotation, UI scale), so wait a moment before treating it as dismissed. */
    static final long END_GRACE_MS = 2500;
    /** The tap that runs a corner gesture also dismisses the screensaver, so look again after it has gone. */
    static final long TAP_SETTLE_MS = 800;
    static final String LIGHT_EVENT = "device.light";

    private volatile PluginHost host;
    private volatile Config config = new Config(Collections.emptyMap());
    private final Session session;
    private ScheduledExecutorService timer;
    private ScheduledFuture<?> pendingEnd;
    private AudioOut audio;
    private int views;
    /** True while this plugin, not the user's Screensaver mode, chose the night light. */
    private boolean overriding;
    /** True when KS passes taps to the screensaver, so it can have its Go to sleep button. */
    private volatile boolean touch;
    /** The room is dark by the light sensor, for Night mode. */
    private volatile boolean dark;

    public NightLightPlugin() {
        this(System::currentTimeMillis);
    }

    NightLightPlugin(Session.Clock clock) {
        session = new Session(clock);
    }

    @Override public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        config = new Config(settings);
        timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "night-light-timer");
            thread.setDaemon(true);
            return thread;
        });
        try {
            try {
                host.publishScreensaverView(KEY, "Night Light", new Factory(), true);
                touch = true;
            } catch (UnsupportedOperationException | LinkageError noTouch) {
                // An older KS: the screensaver works, but every tap closes it, so it has no button.
                host.publishScreensaverView(KEY, "Night Light", new Factory());
                touch = false;
            }
            host.status(READY, false);
            watchLight(host);
        } catch (UnsupportedOperationException | LinkageError olderHost) {
            host.status("This Kiosk Satellite is too old for native screensavers. Update Kiosk Satellite.", true);
        }
    }

    @Override public void configure(Map<String, Object> settings) {
        config = new Config(settings);
    }

    /** Follows the room's light level for Night mode, the way the Clock screensaver does. */
    private void watchLight(PluginHost h) {
        try {
            h.subscribe(LIGHT_EVENT);
            h.executeCommand("getLightLevel", Collections.emptyMap(), (ok, data, error) -> {
                if (ok && data instanceof Map) light(((Map<?, ?>) data).get("lux"));
            });
        } catch (UnsupportedOperationException | SecurityException | LinkageError noSensor) {
            h.log("No light level: " + noSensor);
        }
    }

    private void light(Object lux) {
        if (lux instanceof Number) dark = dark(dark, ((Number) lux).doubleValue(), config.nightLux);
    }

    /** Dark at or below the threshold, and light again only a quarter above it, so it never flickers. */
    static boolean dark(boolean wasDark, double lux, double threshold) {
        return lux <= (wasDark ? threshold * 1.25 : threshold);
    }

    @Override public void execute(String command, Map<String, Object> arguments) {
        PluginHost h = host;
        if (h == null) throw new IllegalStateException("Night light is not running");
        switch (command) {
            case "startNightLight": startNightLight(h); break;
            case "goToSleep": goToSleep(h); break;
            case "stopNightLight": stopNightLight(h); break;
            case "sleepNow": endShow(h); break;
            case "toggleSleep": toggleShow(); break;
            default: throw new IllegalArgumentException("Unknown action: " + command);
        }
    }

    private void startNightLight(PluginHost h) {
        session.endShow(config.colourMs);
        ensureShown(h);
    }

    private void goToSleep(PluginHost h) {
        session.startShow();
        h.status("Go to sleep show playing.", false);
        ensureShown(h);
    }

    private void endShow(PluginHost h) {
        session.endShow(config.colourMs);
        h.status("Fading back to the night light.", false);
    }

    /** Shows the night light unless it is on screen, checking again once a gesture's tap has dismissed it. */
    private synchronized void ensureShown(PluginHost h) {
        if (!onScreen()) { showScreensaver(h); return; }
        if (timer == null) return;
        try {
            timer.schedule(() -> { if (!onScreen()) showScreensaver(h); }, TAP_SETTLE_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopping) {
            // The plugin is stopping.
        }
    }

    /** True while the screensaver view is up and not on its way out. */
    private synchronized boolean onScreen() {
        return session.live() && views > 0;
    }

    private void showScreensaver(PluginHost h) {
        synchronized (this) { overriding = true; }
        h.executeCommand("setScreensaverMode", Collections.singletonMap("mode", MODE), (ok, data, error) -> {
            if (!ok) { h.status("Could not switch to the night light: " + error, true); return; }
            h.executeCommand("startScreensaver", Collections.emptyMap(), (started, d, e) -> {
                if (!started) h.status("Could not start the screensaver: " + e, true);
            });
        });
    }

    private void stopNightLight(PluginHost h) {
        boolean wasOverriding;
        synchronized (this) { wasOverriding = overriding; overriding = false; }
        if (wasOverriding) h.executeCommand("clearScreensaverMode", Collections.emptyMap(), (ok, d, e) -> {});
        h.executeCommand("stopScreensaver", Collections.emptyMap(), (ok, d, e) -> {
            if (!ok) h.status("Could not stop the screensaver: " + e, true);
        });
    }

    @Override public void onEvent(String event, Map<String, Object> payload) {
        if (("ks." + LIGHT_EVENT).equals(event) && payload != null) light(payload.get("lux"));
    }

    @Override public synchronized void stop() {
        if (audio != null) audio.stopNow();
        audio = null;
        if (timer != null) timer.shutdownNow();
        timer = null;
        session.ended();
        host = null;
    }

    // ---- Screensaver lifecycle --------------------------------------------------------------------------------

    /** Called on the main thread when KS builds the screensaver view. */
    synchronized void viewShown() {
        views++;
        if (pendingEnd != null) { pendingEnd.cancel(false); pendingEnd = null; }
        session.shown();
        if (audio == null || !audio.alive()) {
            audio = new AudioOut(this);
            audio.start();
        }
        PluginHost h = host;
        if (h == null) return;
        h.status(session.showing(config.colourMs) ? "Go to sleep show playing." : "Night light on.", false);
    }

    /** Called on the main thread when KS removes the view. */
    synchronized void viewGone() {
        views = Math.max(0, views - 1);
        if (views > 0 || timer == null) return;
        pendingEnd = timer.schedule(this::ended, END_GRACE_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void ended() {
        pendingEnd = null;
        if (views > 0) return;
        session.ended();
        if (audio != null) audio.stop();
        audio = null;
        PluginHost h = host;
        if (h == null) return;
        // The Start action only borrows the screensaver for the night. Give the usual one back in the morning.
        if (overriding) {
            overriding = false;
            h.executeCommand("clearScreensaverMode", Collections.emptyMap(), (ok, d, e) -> {});
        }
        h.status(READY, false);
    }

    synchronized boolean audioRunning() {
        return audio != null && audio.alive();
    }

    // ---- Sources for the view and the audio thread -------------------------------------------------------------

    @Override public Config config() {
        return config;
    }

    @Override public long elapsed() {
        return session.elapsed();
    }

    @Override public double fade() {
        return session.fade(config);
    }

    @Override public boolean night() {
        return dark && config.nightMode;
    }

    @Override public void log(String message) {
        PluginHost h = host;
        if (h != null) h.log(message);
    }

    @Override public boolean showing() {
        return session.showing(config.colourMs);
    }

    @Override public void toggleShow() {
        PluginHost h = host;
        if (h == null) return;
        if (showing()) endShow(h);
        else goToSleep(h);
    }

    Session session() {
        return session;
    }

    private final class Factory implements OverlayFactory {
        @Override public View create(Context context, KsTheme theme) {
            GlowView glow = new GlowView(context, NightLightPlugin.this);
            View view = touch ? new NightLightView(context, theme, glow, NightLightPlugin.this) : glow;
            viewShown();
            return view;
        }

        @Override public void onDestroy(View view) {
            viewGone();
        }
    }
}
