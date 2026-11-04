// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

/**
 * What the night light shows and plays at a moment of its session. Pure maths, so it runs on the desktop JVM in
 * tests. The soother has three parts: its colours, a fade to the night light, and the night light itself.
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

    /** One soft colour blob of the liquid style, at (x, y) with radius r as fractions of the screen. 0xRRGGBB. */
    static final class Blob {
        double x;
        double y;
        double r;
        int colour;
    }

    static final int BLOBS = 4;
    /** In the liquid style the glow underneath is dimmed, so the blobs carry the colour. */
    static final double LIQUID_BASE = 0.35;

    /** How loud the soother's and the night light's sounds are, from 0 to 1, with their volumes applied. */
    static final class Mix {
        double soother;
        double night;
        /** The soother's music speed; it slows as the soother winds down. */
        double tempo = 1;
    }

    /** The night light's music plays at the soother's slowest. */
    static final double NIGHT_TEMPO = 0.85;

    /** The night light's colours drift this many times slower than the soother's colours. */
    static final double NIGHT_SLOWER = 4;

    /** The soother grows out of the night light at the start, so pressing Soother never jumps. */
    static final long FADE_IN_MS = 8000;

    static Phase phase(double fade) {
        if (fade <= 0) return Phase.COLOURS;
        if (fade < 1) return Phase.FADING;
        return Phase.SLEEP;
    }

    static Phase phase(Config c, long elapsed) {
        if (elapsed < c.colourMs) return Phase.COLOURS;
        if (elapsed < c.colourMs + c.fadeMs) return Phase.FADING;
        return Phase.SLEEP;
    }

    /** How far through the fade to the night light, eased, from 0 to 1, for a soother left to run its course. */
    static double fade(Config c, long elapsed) {
        if (elapsed <= c.colourMs) return 0;
        if (c.fadeMs <= 0) return 1;
        return smooth(clamp((elapsed - c.colourMs) / (double) c.fadeMs));
    }

    /** How close the soother is to the night light, from 0 to 1: it grows out of it at the start and fades back at the end. */
    static double toSleep(long elapsed, double fade) {
        return Math.max(fade, 1 - smooth(clamp(elapsed / (double) FADE_IN_MS)));
    }

    static Frame frame(Config c, long elapsed, Frame out) {
        return frame(c, elapsed, fade(c, elapsed), 0, out);
    }

    /**
     * The glow at a moment of the soother, given how far it has faded back to the night light. clockMs is the wall
     * clock, which moves the night light's colours.
     */
    static Frame frame(Config c, long elapsed, double fade, long clockMs, Frame out) {
        if (out == null) out = new Frame();
        elapsed = Math.max(0, elapsed);
        out.phase = phase(fade);
        // The night light: flat is one even colour, glow is brighter in the middle, and liquid dims the glow so its
        // blobs carry the colour.
        double bright = c.warmBrightness * (c.nightStyle == Config.Style.LIQUID ? LIQUID_BASE : 1);
        double edgeBright = c.nightStyle == Config.Style.FLAT ? bright : bright * 0.72;
        int warm;
        int warmEdge;
        if (c.nightWaves) {
            double night = nightPosition(c, clockMs);
            warm = scale(colourAt(c.nightPalette, night), bright);
            warmEdge = scale(colourAt(c.nightPalette, c.nightStyle == Config.Style.FLAT ? night : night + 0.6), edgeBright);
        } else {
            warm = scale(c.warmColor, bright);
            warmEdge = scale(c.warmColor, edgeBright);
        }
        double p = toSleep(elapsed, fade);
        if (out.phase == Phase.SLEEP) {
            out.centre = warm;
            out.edge = warmEdge;
            out.cx = 0.5;
            out.cy = 0.5;
            return out;
        }
        double t = elapsed / 1000.0;
        // The soother winds down: a little dimmer by the end, so the night light is not a jump.
        double windDown = clamp(elapsed / (double) Math.max(1, c.colourMs));
        double show = c.colourBrightness * (1 - 0.35 * windDown);
        double pos = t / c.secondsPerColour;
        if (c.liquid) show *= LIQUID_BASE;
        int centre = scale(colourAt(c.palette, pos), show);
        int edge = scale(colourAt(c.palette, pos + 0.6), show * 0.38);
        double cx = 0.5 + 0.18 * Math.sin(2 * Math.PI * t / 97.0);
        double cy = 0.5 + 0.14 * Math.sin(2 * Math.PI * t / 131.0 + 1.0);
        out.centre = mix(centre, warm, p);
        out.edge = mix(edge, warmEdge, p);
        out.cx = cx + (0.5 - cx) * p;
        out.cy = cy + (0.5 - cy) * p;
        return out;
    }

    /**
     * The liquid style's blobs during the soother. Each wanders its own slow path and drifts through the palette, a
     * different colour from the others, so where they overlap the colours blend. They slow and dim as the soother
     * winds down and gather into the centre as it fades to the night light. Colours are added on top of the frame.
     */
    static Blob[] blobs(Config c, long elapsed, Blob[] out) {
        return blobs(c, elapsed, fade(c, elapsed), out);
    }

    static Blob[] blobs(Config c, long elapsed, double fade, Blob[] out) {
        if (out == null) {
            out = new Blob[BLOBS];
            for (int i = 0; i < BLOBS; i++) out[i] = new Blob();
        }
        elapsed = Math.max(0, elapsed);
        double windDown = clamp(elapsed / (double) Math.max(1, c.colourMs));
        // Slows to 70% by the end of the soother without the blobs ever jumping.
        double t = elapsed / 1000.0 * (1 - 0.15 * windDown);
        double p = toSleep(elapsed, fade);
        double bright = c.colourBrightness * (1 - 0.35 * windDown) * 0.5 * (1 - p);
        // One lap of a blob's path takes about five colour changes: a minute or two.
        double lap = c.secondsPerColour * 5;
        for (int i = 0; i < out.length; i++) {
            Blob b = out[i];
            place(b, i, t, lap);
            b.x += (0.5 - b.x) * p;
            b.y += (0.5 - b.y) * p;
            double hue = t / c.secondsPerColour + i * c.palette.length / (double) out.length;
            b.colour = scale(colourAt(c.palette, hue), bright);
        }
        return out;
    }

    /** Where blob i is on its slow path at t seconds, with one lap taking about lap seconds. */
    private static void place(Blob b, int i, double t, double lap) {
        b.x = 0.5 + 0.32 * Math.sin(2 * Math.PI * t / (lap * (1 + 0.23 * i)) + i * 1.7);
        b.y = 0.5 + 0.28 * Math.sin(2 * Math.PI * t / (lap * (1.31 + 0.17 * i)) + i * 2.3);
        b.r = 0.42 + 0.08 * Math.sin(2 * Math.PI * t / (lap * 0.9 + i * 7) + i);
    }

    /** How far the night light has drifted through its colours by the wall clock. */
    static double nightPosition(Config c, long clockMs) {
        return clockMs / 1000.0 / (c.secondsPerColour * NIGHT_SLOWER);
    }

    /**
     * The night light's liquid blobs: the same soft blobs as the soother's, drifting NIGHT_SLOWER times slower by the
     * wall clock. In the solid night light colour, a little lighter or darker each, or each a different colour from
     * the night light's colours. They grow in as the soother fades to the night light. None unless the style is liquid.
     */
    static Blob[] nightBlobs(Config c, long elapsed, double fade, long clockMs, Blob[] out) {
        if (out == null) {
            out = new Blob[BLOBS];
            for (int i = 0; i < BLOBS; i++) out[i] = new Blob();
        }
        double p = c.nightStyle == Config.Style.LIQUID ? toSleep(Math.max(0, elapsed), fade) : 0;
        double t = clockMs / 1000.0;
        double lap = c.secondsPerColour * 5 * NIGHT_SLOWER;
        double night = nightPosition(c, clockMs);
        for (int i = 0; i < out.length; i++) {
            Blob b = out[i];
            place(b, i, t, lap);
            int colour = c.nightWaves ? colourAt(c.nightPalette, night + i * c.nightPalette.length / (double) out.length) : c.warmColor;
            b.colour = scale(colour, c.warmBrightness * 0.5 * (0.75 + 0.25 * (i % 2)) * p);
        }
        return out;
    }

    static Mix mix(Config c, long elapsed, Mix out) {
        return mix(c, elapsed, fade(c, elapsed), out);
    }

    /**
     * The sound at a moment: the soother's sound at the soother volume crossfades with the night light's sound at the
     * night light volume, the same way the colours do, so starting or ending the soother never jumps.
     */
    static Mix mix(Config c, long elapsed, double fade, Mix out) {
        if (out == null) out = new Mix();
        elapsed = Math.max(0, elapsed);
        double p = toSleep(elapsed, fade);
        double windDown = clamp(elapsed / (double) Math.max(1, c.colourMs));
        out.tempo = 1 - (1 - NIGHT_TEMPO) * windDown;
        out.soother = c.sound == Config.Sound.NONE ? 0 : (1 - p) * loudness(c.volume);
        out.night = c.nightSound == Config.Sound.NONE ? 0 : p * loudness(c.nightVolume);
        return out;
    }

    /** A volume setting as a level: the curve makes the slider feel even. */
    static double loudness(double volume) {
        return Math.pow(clamp(volume), 1.5);
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
