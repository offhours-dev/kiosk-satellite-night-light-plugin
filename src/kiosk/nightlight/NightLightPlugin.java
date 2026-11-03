// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

import android.content.Context;
import android.view.View;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * A night light for a child's room, drawn as a Kiosk Satellite screensaver. It opens on a dim warm light. A button
 * over it starts the go to sleep show: sleepy music while the screen drifts through soft colours, which fades back to
 * the warm light when it is over or when End is pressed. KS still decides when the screensaver starts and ends; this
 * plugin only draws it, its button and plays the music.
 */
public final class NightLightPlugin implements KioskPlugin, GlowView.Source, AudioOut.Source, SleepButton.Source {
    static final String ID = "night-light";
    static final String KEY = "nightlight";
    static final String MODE = "plugin:" + ID + ":" + KEY;
    static final String BUTTON = "sleepbutton";
    static final String READY = "Ready. Choose Night Light under Screensaver mode, or run the Start night light action.";
    /** KS can rebuild the view (rotation, UI scale), so wait a moment before treating it as dismissed. */
    static final long END_GRACE_MS = 2500;

    private volatile PluginHost host;
    private volatile Config config = new Config(Collections.emptyMap());
    private final Session session;
    private ScheduledExecutorService timer;
    private ScheduledFuture<?> pendingEnd;
    private AudioOut audio;
    private int views;
    /** True while this plugin, not the user's Screensaver mode, chose the night light. */
    private boolean overriding;
    private boolean buttonShown;

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
            host.publishScreensaverView(KEY, "Night Light", new Factory());
            host.status(READY, false);
        } catch (UnsupportedOperationException | LinkageError olderHost) {
            host.status("This Kiosk Satellite is too old for native screensavers. Update Kiosk Satellite.", true);
        }
    }

    @Override public void configure(Map<String, Object> settings) {
        config = new Config(settings);
    }

    @Override public void execute(String command, Map<String, Object> arguments) {
        PluginHost h = host;
        if (h == null) throw new IllegalStateException("Night light is not running");
        switch (command) {
            case "startNightLight": startNightLight(h); break;
            case "goToSleep": goToSleep(h); break;
            case "stopNightLight": stopNightLight(h); break;
            case "sleepNow": endShow(h); break;
            default: throw new IllegalArgumentException("Unknown action: " + command);
        }
    }

    private void startNightLight(PluginHost h) {
        session.endShow(config.colourMs);
        if (!session.live()) showScreensaver(h);
    }

    private void goToSleep(PluginHost h) {
        session.startShow();
        h.status("Go to sleep show playing.", false);
        if (!session.live()) showScreensaver(h);
    }

    private void endShow(PluginHost h) {
        session.endShow(config.colourMs);
        h.status("Fading back to the night light.", false);
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

    @Override public void onEvent(String event, Map<String, Object> payload) {}

    @Override public synchronized void stop() {
        if (host != null) hideButton(host);
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
        showButton(h);
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
        hideButton(h);
        // The Start action only borrows the screensaver for the night. Give the usual one back in the morning.
        if (overriding) {
            overriding = false;
            h.executeCommand("clearScreensaverMode", Collections.emptyMap(), (ok, d, e) -> {});
        }
        h.status(READY, false);
    }

    /** Floats the Go to sleep button over the screensaver. Touches on the screensaver itself stay with KS. */
    private void showButton(PluginHost h) {
        if (buttonShown) return;
        try {
            h.showOverlay(BUTTON, OverlaySpec.at(OverlaySpec.BOTTOM_RIGHT).inset(24).onTop(true).closeOnBack(false)
                .enter(OverlaySpec.FADE).exit(OverlaySpec.FADE).transitionDuration(600), new ButtonFactory());
            buttonShown = true;
        } catch (UnsupportedOperationException | SecurityException | LinkageError unavailable) {
            h.log("No Go to sleep button: " + unavailable);
        }
    }

    private void hideButton(PluginHost h) {
        if (!buttonShown) return;
        buttonShown = false;
        try {
            h.hideOverlay(BUTTON);
        } catch (RuntimeException | LinkageError ignored) {
            // The session is ending anyway.
        }
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
            GlowView view = new GlowView(context, NightLightPlugin.this);
            viewShown();
            return view;
        }

        @Override public void onDestroy(View view) {
            viewGone();
        }
    }

    private final class ButtonFactory implements OverlayFactory {
        @Override public View create(Context context, KsTheme theme) {
            return new SleepButton(context, theme, NightLightPlugin.this);
        }
    }
}
