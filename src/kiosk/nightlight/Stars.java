// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

/**
 * The bedtime countdown: a row of up to ten stars, about one per hour of sleep, that go out one by one between bed
 * time and up time, so a child can see how long is left until they can get up. Pure Java for tests.
 */
final class Stars {
    static final int MAX = 10;
    static final long DAY_MS = 24 * 60 * 60_000L;
    private static final long HOUR_MS = 60 * 60_000L;

    /** How long the night is, from bed time to up time, or 0 when the countdown is off. */
    static long night(Config c) {
        if (!c.countdown) return 0;
        return Math.floorMod(c.wakeMs - c.bedMs, DAY_MS);
    }

    /** How many stars the row has, or 0 when the countdown is off. */
    static int count(Config c) {
        long night = night(c);
        if (night <= 0) return 0;
        return (int) Math.max(1, Math.min(MAX, Math.round(night / (double) HOUR_MS)));
    }

    /** How many stars are still lit at a time of day, in ms since local midnight. */
    static int lit(Config c, long timeOfDayMs) {
        int n = count(c);
        if (n == 0) return 0;
        long night = night(c);
        long since = Math.floorMod(timeOfDayMs - c.bedMs, DAY_MS);
        if (since >= night) {
            // Daytime: all out after up time, then all lit again from halfway to the next bed time.
            return since - night < (DAY_MS - night) / 2 ? 0 : n;
        }
        return n - (int) (since * n / night);
    }

    private Stars() {}
}
