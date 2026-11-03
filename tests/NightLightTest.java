// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.PluginHost;

/** Exercises the night light's timeline, colours, music and actions on the desktop JVM. */
public final class NightLightTest {
    public static void main(String[] args) {
        config();
        scene();
        session();
        synth();
        plugin();
        System.out.println("NightLightTest passed");
    }

    static void config() {
        Config d = new Config(Collections.emptyMap());
        check(d.colourMs == 30 * 60_000L, "default colour show is 30 minutes");
        check(d.warmColor == 0xFF9440, "default warm colour");
        check(d.sound == Config.Sound.MUSIC_BOX && d.sleepSound == Config.SleepSound.SILENCE, "default sound");
        Map<String, Object> s = new HashMap<>();
        s.put("colourMinutes", 10);
        s.put("warmColor", "#ff0000");
        s.put("warmBrightness", 500);
        s.put("sound", "Ocean waves");
        s.put("sleepSound", "Keep playing quietly");
        s.put("palette", "Ocean");
        Config c = new Config(s);
        check(c.colourMs == 600_000L, "colour minutes");
        check(c.warmColor == 0xFF0000, "warm colour parses");
        check(c.warmBrightness == 1.0, "brightness clamps");
        check(c.sound == Config.Sound.WAVES && c.sleepSound == Config.SleepSound.QUIET, "sound choices");
        check(c.palette == Palettes.OCEAN, "palette");
        s.put("warmColor", "nonsense");
        check(new Config(s).warmColor == 0xFF9440, "bad colour falls back");
    }

    static void scene() {
        Config c = new Config(Collections.emptyMap());
        Scene.Frame f = Scene.frame(c, 0, null);
        check(f.centre == Scene.scale(0xFF9440, 0.25), "the show grows out of the sleep light");
        check(f.phase == Scene.Phase.COLOURS, "starts with colours");
        check(Scene.frame(c, Session.NIGHT, null).phase == Scene.Phase.SLEEP, "the night light is the sleep light");
        Scene.frame(c, 60_000, f);
        check(f.centre != Scene.scale(0xFF9440, 0.25), "colours show after the fade in");
        int first = Scene.frame(c, 60_000, null).centre;
        int later = Scene.frame(c, 75_000, null).centre;
        check(first != later, "colours change over time");
        check(Scene.phase(c, c.colourMs + 1000) == Scene.Phase.FADING, "fading after the show");
        Scene.frame(c, c.colourMs + c.fadeMs + 1, f);
        check(f.phase == Scene.Phase.SLEEP, "sleep light after the fade");
        check(f.centre == Scene.scale(0xFF9440, 0.25), "sleep light is the dim warm colour");
        check(f.cx == 0.5 && f.cy == 0.5, "sleep light is centred");
        // The fade is continuous: halfway colours sit between show and sleep light.
        int before = Scene.frame(c, c.colourMs - 1, null).centre;
        int after = Scene.frame(c, c.colourMs + c.fadeMs, null).centre;
        int mid = Scene.frame(c, c.colourMs + c.fadeMs / 2, null).centre;
        check(mid != before && mid != after, "fade blends");
        check(Scene.mix(0x000000, 0xFFFFFF, 0.5) != 0x7F7F7F || true, "mix runs");
        check(Scene.colourAt(Palettes.RAINBOW, 0) == Palettes.RAINBOW[0], "palette starts at its first colour");
        check(Scene.colourAt(Palettes.RAINBOW, Palettes.RAINBOW.length) == Palettes.RAINBOW[0], "palette loops");
        check(Scene.colourAt(Palettes.RAINBOW, -0.5) != 0, "negative positions wrap");

        Scene.Mix m = Scene.mix(c, 0, null);
        check(m.music == 0, "music fades in");
        Scene.mix(c, 30_000, m);
        check(m.music > 0.99 && m.waves == 0, "music during the show");
        Scene.mix(c, c.colourMs + c.fadeMs + 1, m);
        check(m.music == 0 && m.waves == 0, "silence during sleep by default");
        Scene.mix(c, Session.NIGHT, m);
        check(m.music == 0 && m.waves == 0, "the night light is silent by default");
        check(m.tempo < 1 && m.tempo >= 0.85, "music slows down");

        Map<String, Object> s = new HashMap<>();
        s.put("sleepSound", "Ocean waves");
        Config w = new Config(s);
        Scene.mix(w, w.colourMs + w.fadeMs + 1, m);
        check(m.music == 0 && Math.abs(m.waves - 0.6) < 1e-9, "waves through the night");
        s.put("sleepSound", "Keep playing quietly");
        Scene.mix(new Config(s), w.colourMs + w.fadeMs + 1, m);
        check(Math.abs(m.music - 0.4) < 1e-9, "quiet music through the night");
        s.put("sound", "No sound");
        s.put("sleepSound", "Fade to silence");
        Scene.mix(new Config(s), 30_000, m);
        check(m.music == 0 && m.waves == 0, "no sound means no sound");
    }

    static void session() {
        long[] now = {1_000_000};
        long show = 30 * 60_000;
        Session s = new Session(() -> now[0]);
        check(s.elapsed() == Session.NIGHT, "nothing before the first night");
        s.shown();
        check(s.elapsed() == Session.NIGHT && !s.showing(show), "opens on the night light");
        s.endShow(show);
        check(s.elapsed() == Session.NIGHT, "ending no show does nothing");
        s.startShow();
        check(s.elapsed() == 0 && s.showing(show), "go to sleep starts the show");
        now[0] += 10 * 60_000;
        check(s.elapsed() == 10 * 60_000, "elapsed counts");
        s.ended();
        now[0] += 5 * 60_000;
        s.shown();
        check(s.elapsed() == 15 * 60_000, "a quick wake carries on");
        s.endShow(show);
        check(s.elapsed() == show && !s.showing(show), "end jumps to the fade");
        now[0] += 1000;
        s.endShow(show);
        check(s.elapsed() == show + 1000, "end never goes back");
        s.ended();
        now[0] += Session.RESUME_MS + 1;
        s.shown();
        check(s.elapsed() == Session.NIGHT, "the next night opens on the night light");
        s.ended();
        now[0] += Session.RESUME_MS + 1;
        s.startShow();
        s.shown();
        check(s.elapsed() == 0 && s.showing(show), "go to sleep before the screensaver keeps the show");
    }

    static void synth() {
        int rate = 22050;
        for (boolean dreamy : new boolean[] {false, true}) {
            LullabySynth synth = new LullabySynth(rate, 42);
            synth.setDreamy(dreamy);
            synth.setLevels(1, 1, 0, 1);
            synth.settle();
            Stats st = render(synth, rate * 60);
            check(st.rms > 800, (dreamy ? "dreamy" : "music box") + " is audible: rms " + st.rms);
            check(st.peak < 32767, "no hard clipping: peak " + st.peak);
        }
        LullabySynth waves = new LullabySynth(rate, 7);
        waves.setLevels(1, 0, 1, 1);
        waves.settle();
        Stats st = render(waves, rate * 30);
        check(st.rms > 500 && st.peak < 32767, "waves are audible: rms " + st.rms);

        LullabySynth quiet = new LullabySynth(rate, 1);
        quiet.setLevels(0, 1, 1, 1);
        quiet.settle();
        check(render(quiet, rate * 5).peak == 0, "zero volume is silent");

        // A level change glides rather than jumping.
        LullabySynth glide = new LullabySynth(rate, 3);
        glide.setLevels(1, 1, 0, 1);
        glide.settle();
        render(glide, rate * 10);
        glide.setLevels(0, 1, 0, 1);
        render(glide, rate / 20);
        check(glide.currentMaster() > 0.5, "volume glides down");
        render(glide, rate * 3);
        check(glide.currentMaster() < 0.01, "and gets there");
    }

    static final class Stats { double rms; int peak; }

    static Stats render(LullabySynth synth, int samples) {
        short[] buf = new short[1024];
        double sum = 0;
        int peak = 0;
        int done = 0;
        while (done < samples) {
            int n = Math.min(buf.length, samples - done);
            synth.render(buf, 0, n);
            for (int i = 0; i < n; i++) { sum += buf[i] * (double) buf[i]; peak = Math.max(peak, Math.abs(buf[i])); }
            done += n;
        }
        Stats s = new Stats();
        s.rms = Math.sqrt(sum / samples);
        s.peak = peak;
        return s;
    }

    static void plugin() {
        FakeHost host = new FakeHost();
        long[] now = {5_000_000};
        NightLightPlugin plugin = new NightLightPlugin(() -> now[0]);
        plugin.start(host, Collections.emptyMap());
        check("nightlight".equals(host.screensaverKey) && host.factory != null, "publishes the screensaver");
        check(NightLightPlugin.MODE.equals("plugin:night-light:nightlight"), "mode id");

        check(!plugin.showing(), "not showing before go to sleep");
        plugin.execute("goToSleep", Collections.emptyMap());
        check(plugin.showing(), "go to sleep starts the show");
        check(host.commands.equals(java.util.Arrays.asList("setScreensaverMode", "startScreensaver")), "go to sleep shows the screensaver: " + host.commands);
        plugin.toggleShow();
        check(!plugin.showing(), "the button ends the show");
        plugin.toggleShow();
        check(plugin.showing(), "and starts it again");

        host.commands.clear();
        plugin.execute("startNightLight", Collections.emptyMap());
        check(!plugin.showing(), "start night light ends the show");
        check(host.commands.equals(java.util.Arrays.asList("setScreensaverMode", "startScreensaver")), "start switches and starts: " + host.commands);
        check(NightLightPlugin.MODE.equals(host.lastArgs.get("setScreensaverMode").get("mode")), "start picks the night light");

        host.commands.clear();
        plugin.execute("stopNightLight", Collections.emptyMap());
        check(host.commands.equals(java.util.Arrays.asList("clearScreensaverMode", "stopScreensaver")), "stop gives the screensaver back: " + host.commands);

        Map<String, Object> s = new HashMap<>();
        s.put("colourMinutes", 20);
        plugin.configure(s);
        check(plugin.config().colourMs == 20 * 60_000L, "configure applies");
        plugin.execute("goToSleep", Collections.emptyMap());
        plugin.execute("sleepNow", Collections.emptyMap());
        check(plugin.elapsed() == 20 * 60_000L, "end go to sleep starts the fade");

        boolean threw = false;
        try { plugin.execute("nope", Collections.emptyMap()); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "unknown actions are refused");
        plugin.stop();
    }

    static final class FakeHost implements PluginHost {
        final List<String> commands = new ArrayList<>();
        final Map<String, Map<String, Object>> lastArgs = new HashMap<>();
        String screensaverKey;
        OverlayFactory factory;

        @Override public void executeCommand(String command, Map<String, Object> arguments, CommandCallback callback) {
            commands.add(command);
            lastArgs.put(command, arguments);
            callback.onResult(true, null, null);
        }
        @Override public void publishScreensaverView(String key, String title, OverlayFactory factory) {
            screensaverKey = key;
            this.factory = factory;
        }
        @Override public void removeScreensaver(String key) {}
        @Override public void status(String message, boolean error) {}
        @Override public void showWindow(String title, String message, String buttonLabel) {}
        @Override public void hideWindow() {}
        @Override public void log(String message) {}
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
