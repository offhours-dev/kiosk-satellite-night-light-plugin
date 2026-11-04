// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

import java.util.Locale;
import java.util.Map;

/** An immutable snapshot of the plugin settings. Read from any thread. */
final class Config {
    enum Sound { MUSIC_BOX, CLASSIC, DREAMY, WAVES, NONE }
    enum SleepSound { SILENCE, QUIET, WAVES }

    final long colourMs;
    final long fadeMs;
    final int[] palette;
    final double secondsPerColour;
    final double colourBrightness;
    /** Liquid colour style: soft colour blobs that drift and blend during the show, instead of one glow. */
    final boolean liquid;
    final int warmColor;
    /** The night light drifts very slowly through the palette instead of one steady colour. */
    final boolean nightWaves;
    final double warmBrightness;
    final Sound sound;
    final double volume;
    final SleepSound sleepSound;
    final boolean countdown;
    /** Bed and up times in ms since local midnight. */
    final long bedMs;
    final long wakeMs;
    /**
     * Night mode, like the Clock screensaver's: when the light sensor reads at or below nightLux, the button, hint and
     * stars take nightColor, or hide with nightHide.
     */
    final boolean nightMode;
    final double nightLux;
    final int nightColor;
    final boolean nightHide;

    Config(Map<String, Object> s) {
        colourMs = (long) (number(s, "colourMinutes", 30, 1, 240) * 60_000L);
        fadeMs = (long) (number(s, "fadeMinutes", 3, 0.5, 30) * 60_000L);
        palette = Palettes.named(string(s, "palette", "Rainbow"));
        switch (string(s, "colourSpeed", "Slow")) {
            case "Very slow": secondsPerColour = 40; break;
            case "Gentle": secondsPerColour = 12; break;
            default: secondsPerColour = 22;
        }
        colourBrightness = number(s, "colourBrightness", 70, 10, 100) / 100.0;
        liquid = "Liquid".equals(string(s, "colourStyle", "Glow"));
        warmColor = color(s, "warmColor", 0xFF9440);
        nightWaves = bool(s, "nightWaves", false);
        warmBrightness = number(s, "warmBrightness", 25, 1, 100) / 100.0;
        switch (string(s, "sound", "Music box lullaby")) {
            case "Brahms & Mozart lullabies": sound = Sound.CLASSIC; break;
            case "Dreamy pads": sound = Sound.DREAMY; break;
            case "Ocean waves": sound = Sound.WAVES; break;
            case "No sound": sound = Sound.NONE; break;
            default: sound = Sound.MUSIC_BOX;
        }
        volume = number(s, "volume", 40, 0, 100) / 100.0;
        switch (string(s, "sleepSound", "Fade to silence")) {
            case "Keep playing quietly": sleepSound = SleepSound.QUIET; break;
            case "Ocean waves": sleepSound = SleepSound.WAVES; break;
            default: sleepSound = SleepSound.SILENCE;
        }
        countdown = bool(s, "countdown", false);
        bedMs = time(s, "bedTime", "19:00");
        wakeMs = time(s, "wakeTime", "07:00");
        nightMode = bool(s, "nightMode", false);
        nightLux = number(s, "nightLux", 5, 1, 100);
        nightColor = color(s, "nightColor", 0xA63A32);
        nightHide = bool(s, "nightHide", false);
    }

    static double number(Map<String, Object> s, String key, double fallback, double min, double max) {
        Object v = s == null ? null : s.get(key);
        double d = v instanceof Number ? ((Number) v).doubleValue() : fallback;
        if (Double.isNaN(d) || Double.isInfinite(d)) d = fallback;
        return Math.max(min, Math.min(max, d));
    }

    static String string(Map<String, Object> s, String key, String fallback) {
        Object v = s == null ? null : s.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    static boolean bool(Map<String, Object> s, String key, boolean fallback) {
        Object v = s == null ? null : s.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    static long time(Map<String, Object> s, String key, String fallback) {
        long t = parseTime(string(s, key, fallback));
        return t >= 0 ? t : parseTime(fallback);
    }

    /** Parses a time of day such as 19:00, 7:30pm or 7pm into ms since midnight, or -1. */
    static long parseTime(String v) {
        if (v == null) return -1;
        v = v.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        boolean pm = v.endsWith("pm");
        boolean am = v.endsWith("am");
        if (pm || am) v = v.substring(0, v.length() - 2);
        String[] parts = v.split("[:.]");
        if (parts.length > 2) return -1;
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length == 2 ? Integer.parseInt(parts[1]) : 0;
            if (am || pm) {
                if (hour < 1 || hour > 12) return -1;
                hour = hour % 12 + (pm ? 12 : 0);
            }
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return -1;
            return (hour * 60L + minute) * 60_000L;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Parses #RRGGBB (or #AARRGGBB, alpha ignored) into 0xRRGGBB. */
    static int color(Map<String, Object> s, String key, int fallback) {
        String v = string(s, key, null);
        if (v == null) return fallback;
        v = v.trim();
        if (v.startsWith("#")) v = v.substring(1);
        if (v.length() != 6 && v.length() != 8) return fallback;
        try {
            return (int) (Long.parseLong(v, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
