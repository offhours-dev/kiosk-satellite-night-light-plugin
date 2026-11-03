// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * The screensaver: the warm night light, or during the go to sleep show a soft glow that drifts slowly through the
 * palette before settling back. It redraws about 15 times a second during the show and every couple of seconds
 * once it is steady.
 */
final class GlowView extends View {
    interface Source {
        Config config();
        long elapsed();
    }

    private static final long COLOUR_FRAME_MS = 66;
    private static final long SLEEP_FRAME_MS = 2000;
    private static final long FADE_UP_MS = 4000;

    private final Source source;
    private final Paint paint = new Paint();
    private final Scene.Frame frame = new Scene.Frame();
    private final int[] colours = new int[3];
    private final float[] stops = {0f, 0.5f, 1f};
    private boolean shown;

    GlowView(Context context, Source source) {
        super(context);
        this.source = source;
        paint.setDither(true);
        paint.setAntiAlias(false);
        setBackgroundColor(0xFF000000);
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        Scene.frame(source.config(), source.elapsed(), frame);
        colours[0] = 0xFF000000 | frame.centre;
        colours[1] = 0xFF000000 | Scene.mix(frame.centre, frame.edge, 0.55);
        colours[2] = 0xFF000000 | frame.edge;
        float radius = (float) Math.hypot(w, h) * 0.72f;
        paint.setShader(new RadialGradient((float) (frame.cx * w), (float) (frame.cy * h), radius, colours, stops,
            Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        if (shown) postInvalidateDelayed(frame.phase == Scene.Phase.SLEEP ? SLEEP_FRAME_MS : COLOUR_FRAME_MS);
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // Screen off or app in the background: stop animating until it shows again.
        shown = visibility == VISIBLE;
        if (shown) invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        shown = getWindowVisibility() == VISIBLE;
        // Fade up from black, so the screensaver never flashes on.
        setAlpha(0f);
        animate().alpha(1f).setDuration(FADE_UP_MS).start();
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        shown = false;
        animate().cancel();
        super.onDetachedFromWindow();
    }
}
