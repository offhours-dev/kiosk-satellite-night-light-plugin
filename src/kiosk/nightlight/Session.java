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
    /** How long End takes to fade the show back to the night light. */
    static final long END_FADE_MS = 20_000;

    interface Clock { long now(); }

    private final Clock clock;
    /** When the go to sleep show started, or -1 for the plain night light. */
    private long start = -1;
    /** When End was pressed, or -1 while the show runs its course. */
    private long endAt = -1;
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
        endAt = -1;
        if (!live) endedAt = start;
    }

    /**
     * Begin a quick fade back to the night light now, from wherever the show is, unless it is already fading. The
     * show keeps its place, so nothing jumps.
     */
    synchronized void endShow(long colourMs) {
        if (start < 0 || endAt >= 0) return;
        long now = clock.now();
        if (now - start >= colourMs) return;
        endAt = now;
        if (!live) endedAt = now;
    }

    /** True while the show's colours and music play, before any fade back to the night light. */
    synchronized boolean showing(long colourMs) {
        return start >= 0 && endAt < 0 && clock.now() - start < colourMs;
    }

    /**
     * How far the show has faded back to the night light, eased, from 0 to 1: over the fade setting once the show
     * has run its length, or over END_FADE_MS from when End was pressed. 1 on the plain night light.
     */
    synchronized double fade(Config c) {
        if (start < 0) return 1;
        long from = endAt >= 0 ? endAt : start + c.colourMs;
        long length = endAt >= 0 ? END_FADE_MS : c.fadeMs;
        long now = clock.now();
        if (now <= from) return 0;
        if (length <= 0) return 1;
        return Scene.smooth(Scene.clamp((now - from) / (double) length));
    }

    synchronized boolean live() {
        return live;
    }

    synchronized long elapsed() {
        if (start < 0) return NIGHT;
        return Math.max(0, clock.now() - start);
    }
}
