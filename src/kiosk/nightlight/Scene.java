// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

/**
 * What the night light shows and plays at a moment of its session. Pure maths, so it runs on the desktop JVM in
 * tests. The session has three parts: the colour show, a fade to the sleep light, and the sleep light itself.
 */
final class Scene {
    enum Phase { COLOURS, FADING, SLEEP }

    /** A soft radial glow: the centre colour at (cx, cy), fading to the edge colour. Colours are 0xRRGGBB. */
    static final class Frame {
        Phase phase;
        int centre;
        int edge;
        double cx;
        double cy;
    }

    /** Layer levels for the synth, from 0 to 1 before the volume setting is applied. */
    static final class Mix {
        double music;
        double waves;
        double tempo = 1;
    }

    /** The screen fades up from black at the start, so the screensaver never flashes on. */
    static final long FADE_IN_MS = 4000;
    static final long SOUND_FADE_IN_MS = 6000;

    static Phase phase(Config c, long elapsed) {
        if (elapsed < c.colourMs) return Phase.COLOURS;
        if (elapsed < c.colourMs + c.fadeMs) return Phase.FADING;
        return Phase.SLEEP;
    }

    /** How far through the fade to the sleep light, eased, from 0 to 1. */
    static double fade(Config c, long elapsed) {
        if (elapsed <= c.colourMs) return 0;
        if (c.fadeMs <= 0) return 1;
        return smooth(clamp((elapsed - c.colourMs) / (double) c.fadeMs));
    }

    static Frame frame(Config c, long elapsed, Frame out) {
        if (out == null) out = new Frame();
        elapsed = Math.max(0, elapsed);
        out.phase = phase(c, elapsed);
        int warm = scale(c.warmColor, c.warmBrightness);
        int warmEdge = scale(c.warmColor, c.warmBrightness * 0.72);
        double p = fade(c, elapsed);
        if (out.phase == Phase.SLEEP) {
            out.centre = warm;
            out.edge = warmEdge;
            out.cx = 0.5;
            out.cy = 0.5;
            return out;
        }
        double t = elapsed / 1000.0;
        // The show winds down: a little dimmer by the end, so the sleep light is not a jump.
        double windDown = clamp(elapsed / (double) Math.max(1, c.colourMs));
        double bright = c.colourBrightness * (1 - 0.35 * windDown) * smooth(clamp(elapsed / (double) FADE_IN_MS));
        double pos = t / c.secondsPerColour;
        int centre = scale(colourAt(c.palette, pos), bright);
        int edge = scale(colourAt(c.palette, pos + 0.6), bright * 0.38);
        double cx = 0.5 + 0.18 * Math.sin(2 * Math.PI * t / 97.0);
        double cy = 0.5 + 0.14 * Math.sin(2 * Math.PI * t / 131.0 + 1.0);
        out.centre = mix(centre, warm, p);
        out.edge = mix(edge, warmEdge, p);
        out.cx = cx + (0.5 - cx) * p;
        out.cy = cy + (0.5 - cy) * p;
        return out;
    }

    static Mix mix(Config c, long elapsed, Mix out) {
        if (out == null) out = new Mix();
        elapsed = Math.max(0, elapsed);
        double in = smooth(clamp(elapsed / (double) SOUND_FADE_IN_MS));
        double p = fade(c, elapsed);
        double windDown = clamp(elapsed / (double) Math.max(1, c.colourMs));
        out.tempo = 1 - 0.15 * windDown;
        boolean music = c.sound == Config.Sound.MUSIC_BOX || c.sound == Config.Sound.DREAMY;
        boolean waves = c.sound == Config.Sound.WAVES;
        double main;
        double sleepWaves = 0;
        switch (c.sleepSound) {
            case QUIET: main = 1 - 0.6 * p; break;
            case WAVES: main = 1 - p; sleepWaves = 0.6 * p; break;
            default: main = 1 - p;
        }
        if (c.sound == Config.Sound.NONE) {
            out.music = 0;
            out.waves = c.sleepSound == Config.SleepSound.WAVES ? sleepWaves : 0;
            return out;
        }
        out.music = music ? main * in : 0;
        // A waves show stays waves: it fades or quietens like the music would, or settles at the sleep level.
        out.waves = waves ? Math.max(main, sleepWaves) * in : sleepWaves;
        return out;
    }

    /** The palette colour at a position: whole numbers land on palette entries, with eased blends between. */
    static int colourAt(int[] palette, double pos) {
        int n = palette.length;
        double floor = Math.floor(pos);
        int i = (int) (((long) floor % n + n) % n);
        double f = smooth(pos - floor);
        return mix(palette[i], palette[(i + 1) % n], f);
    }

    /** Blends two colours in roughly linear light, which avoids muddy midpoints. */
    static int mix(int a, int b, double t) {
        if (t <= 0) return a & 0xFFFFFF;
        if (t >= 1) return b & 0xFFFFFF;
        return (channel(a >> 16, b >> 16, t) << 16) | (channel(a >> 8, b >> 8, t) << 8) | channel(a, b, t);
    }

    private static int channel(int a, int b, double t) {
        double x = (a & 0xFF) / 255.0;
        double y = (b & 0xFF) / 255.0;
        double v = Math.sqrt(x * x * (1 - t) + y * y * t);
        return (int) Math.round(clamp(v) * 255);
    }

    static int scale(int c, double k) {
        k = clamp(k);
        int r = (int) Math.round(((c >> 16) & 0xFF) * k);
        int g = (int) Math.round(((c >> 8) & 0xFF) * k);
        int b = (int) Math.round((c & 0xFF) * k);
        return (r << 16) | (g << 8) | b;
    }

    static double smooth(double x) {
        x = clamp(x);
        return x * x * (3 - 2 * x);
    }

    static double clamp(double x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    private Scene() {}
}
