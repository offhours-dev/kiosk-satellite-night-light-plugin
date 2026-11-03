// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

import java.util.Map;

/** An immutable snapshot of the plugin settings. Read from any thread. */
final class Config {
    enum Sound { MUSIC_BOX, DREAMY, WAVES, NONE }
    enum SleepSound { SILENCE, QUIET, WAVES }

    final long colourMs;
    final long fadeMs;
    final int[] palette;
    final double secondsPerColour;
    final double colourBrightness;
    final int warmColor;
    final double warmBrightness;
    final Sound sound;
    final double volume;
    final SleepSound sleepSound;

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
        warmColor = color(s, "warmColor", 0xFF9440);
        warmBrightness = number(s, "warmBrightness", 25, 1, 100) / 100.0;
        switch (string(s, "sound", "Music box lullaby")) {
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
