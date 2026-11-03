// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

/**
 * When the current night started. The clock keeps running while the screensaver is briefly dismissed, so a child
 * who taps the screen at 2am gets the sleep light back, not the colour show from the start. Pure Java for tests.
 */
final class Session {
    /** A screensaver that comes back within this long carries on where it was. */
    static final long RESUME_MS = 60 * 60_000L;

    interface Clock { long now(); }

    private final Clock clock;
    private long start = -1;
    private long endedAt = -1;
    private boolean live;

    Session(Clock clock) {
        this.clock = clock;
    }

    /** The screensaver appeared. Starts a new night unless the last one ended recently. */
    synchronized void shown() {
        long now = clock.now();
        if (live) return;
        live = true;
        if (start < 0 || endedAt < 0 || now - endedAt > RESUME_MS || now < endedAt) start = now;
        endedAt = -1;
    }

    /** The screensaver went away for good (after KS's short grace period). */
    synchronized void ended() {
        if (!live) return;
        live = false;
        endedAt = clock.now();
    }

    /** Start the night over, from the colour show. */
    synchronized void restart() {
        start = clock.now();
        endedAt = live ? -1 : start;
    }

    /** Begin the fade to the sleep light now, unless it already began. */
    synchronized void skipTo(long elapsedMs) {
        if (start < 0) start = clock.now();
        long now = clock.now();
        if (now - start < elapsedMs) start = now - elapsedMs;
        if (!live) endedAt = now;
    }

    synchronized boolean live() {
        return live;
    }

    synchronized long elapsed() {
        if (start < 0) return 0;
        return Math.max(0, clock.now() - start);
    }
}
