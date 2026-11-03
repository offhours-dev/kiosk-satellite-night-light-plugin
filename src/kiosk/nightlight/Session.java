// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

/**
 * Whether the go to sleep show is running and when it started. The screensaver opens on the plain night light; the
 * show only runs when asked for. The clock keeps running while the screensaver is briefly dismissed, so a child who
 * taps the screen mid-show gets the show back where it was, not the plain night light. Pure Java for tests.
 */
final class Session {
    /** A screensaver that comes back within this long carries on where it was. */
    static final long RESUME_MS = 60 * 60_000L;
    /** What elapsed() reads on the plain night light: far past any show and its fade. */
    static final long NIGHT = Long.MAX_VALUE / 4;

    interface Clock { long now(); }

    private final Clock clock;
    /** When the go to sleep show started, or -1 for the plain night light. */
    private long start = -1;
    private long endedAt = -1;
    private boolean live;

    Session(Clock clock) {
        this.clock = clock;
    }

    /** The screensaver appeared. Opens on the night light unless it ended recently. */
    synchronized void shown() {
        long now = clock.now();
        if (live) return;
        live = true;
        if (endedAt < 0 || now - endedAt > RESUME_MS || now < endedAt) start = -1;
        endedAt = -1;
    }

    /** The screensaver went away for good (after KS's short grace period). */
    synchronized void ended() {
        if (!live) return;
        live = false;
        endedAt = clock.now();
    }

    /** Start the go to sleep show from the beginning. */
    synchronized void startShow() {
        start = clock.now();
        if (!live) endedAt = start;
    }

    /** Begin the fade back to the night light now, unless it already began. */
    synchronized void endShow(long colourMs) {
        if (start < 0) return;
        long now = clock.now();
        if (now - start < colourMs) start = now - colourMs;
        if (!live) endedAt = now;
    }

    /** True while the show's colours and music play, before the fade back to the night light. */
    synchronized boolean showing(long colourMs) {
        return start >= 0 && clock.now() - start < colourMs;
    }

    synchronized boolean live() {
        return live;
    }

    synchronized long elapsed() {
        if (start < 0) return NIGHT;
        return Math.max(0, clock.now() - start);
    }
}
