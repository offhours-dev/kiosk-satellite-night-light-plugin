// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;
import java.util.Calendar;

/**
 * The screensaver: the night light, or during the soother a soft glow that drifts slowly through the palette before
 * settling back. It redraws about 15 times a second during the soother and every couple of seconds once it is
 * steady.
 */
final class GlowView extends View {
    interface Source {
        Config config();
        long elapsed();
        double fade();
        /** True while Night mode is on and the room is dark. */
        boolean night();
    }

    private static final long COLOUR_FRAME_MS = 66;
    private static final long SLEEP_FRAME_MS = 2000;
    /** Often enough that the night light's drifting colours move smoothly. */
    private static final long WAVES_FRAME_MS = 250;
    private static final long FADE_UP_MS = 4000;
    private static final int STAR_LIT = 0x8FB4FF;
    private static final int STAR_OUT = 0x9A9A9A;
    /** The liquid style is drawn this many times smaller, then stretched: the stretch blurs it smoothly for free. */
    private static final int LIQUID_SCALE = 6;

    private final Source source;
    private final Paint paint = new Paint();
    private final Scene.Frame frame = new Scene.Frame();
    private final int[] colours = new int[3];
    private final float[] stops = {0f, 0.5f, 1f};
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star = new Path();
    private Scene.Blob[] blobs;
    private Scene.Blob[] nightBlobs;
    private Bitmap liquid;
    private Canvas liquidCanvas;
    private final Paint blobPaint = new Paint();
    private final Paint liquidPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect screen = new Rect();
    private final int[] blobColours = new int[3];
    private final float[] blobStops = {0f, 0.45f, 1f};
    private boolean shown;

    GlowView(Context context, Source source) {
        super(context);
        this.source = source;
        paint.setDither(true);
        paint.setAntiAlias(false);
        setBackgroundColor(0xFF000000);
        PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
        blobPaint.setXfermode(add);
        liquidPaint.setXfermode(add);
        liquidPaint.setDither(true);
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        Config c = source.config();
        long elapsed = source.elapsed();
        double fade = source.fade();
        long clock = System.currentTimeMillis();
        Scene.frame(c, elapsed, fade, clock, frame);
        colours[0] = 0xFF000000 | frame.centre;
        colours[1] = 0xFF000000 | Scene.mix(frame.centre, frame.edge, 0.55);
        colours[2] = 0xFF000000 | frame.edge;
        float radius = (float) Math.hypot(w, h) * 0.72f;
        paint.setShader(new RadialGradient((float) (frame.cx * w), (float) (frame.cy * h), radius, colours, stops,
            Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        boolean sootherBlobs = c.liquid && frame.phase != Scene.Phase.SLEEP;
        boolean nightLiquid = c.nightStyle == Config.Style.LIQUID;
        if (sootherBlobs || nightLiquid) {
            Scene.Blob[] soother = null;
            if (sootherBlobs) soother = blobs = Scene.blobs(c, elapsed, fade, blobs);
            Scene.Blob[] night = null;
            if (nightLiquid) night = nightBlobs = Scene.nightBlobs(c, elapsed, fade, clock, nightBlobs);
            drawLiquid(canvas, w, h, soother, night);
        }
        if (frame.phase != Scene.Phase.COLOURS) drawStars(canvas, w, h, c, fade);
        boolean moving = c.nightWaves || nightLiquid;
        long next = frame.phase != Scene.Phase.SLEEP ? COLOUR_FRAME_MS : moving ? WAVES_FRAME_MS : SLEEP_FRAME_MS;
        if (shown) postInvalidateDelayed(next);
    }

    /** The liquid style's soft blobs, the soother's and the night light's, added over the dimmed glow. */
    private void drawLiquid(Canvas canvas, int w, int h, Scene.Blob[] soother, Scene.Blob[] night) {
        int sw = Math.max(1, w / LIQUID_SCALE);
        int sh = Math.max(1, h / LIQUID_SCALE);
        if (liquid == null || liquid.getWidth() != sw || liquid.getHeight() != sh) {
            liquid = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            liquidCanvas = new Canvas(liquid);
        }
        liquid.eraseColor(0);
        if (soother != null) drawBlobs(soother, sw, sh);
        if (night != null) drawBlobs(night, sw, sh);
        screen.set(0, 0, w, h);
        canvas.drawBitmap(liquid, null, screen, liquidPaint);
    }

    private void drawBlobs(Scene.Blob[] blobs, int sw, int sh) {
        float size = Math.max(sw, sh);
        for (Scene.Blob b : blobs) {
            if (b.colour == 0) continue;
            blobColours[0] = 0xFF000000 | b.colour;
            blobColours[1] = 0x70000000 | b.colour;
            blobColours[2] = b.colour;
            float x = (float) (b.x * sw);
            float y = (float) (b.y * sh);
            float r = (float) (b.r * size);
            blobPaint.setShader(new RadialGradient(x, y, r, blobColours, blobStops, Shader.TileMode.CLAMP));
            liquidCanvas.drawCircle(x, y, r, blobPaint);
        }
    }

    /**
     * The bedtime countdown on the night light: lit stars still to go, outlines for the hours already slept. In Night
     * mode they take the Night colour, or hide.
     */
    private void drawStars(Canvas canvas, int w, int h, Config c, double fade) {
        int n = Stars.count(c);
        if (n == 0 || fade <= 0) return;
        boolean night = source.night();
        if (night && c.nightHide) return;
        int lit = night ? c.nightColor : STAR_LIT;
        int used = night ? c.nightColor : STAR_OUT;
        Calendar now = Calendar.getInstance();
        long timeOfDay = ((now.get(Calendar.HOUR_OF_DAY) * 60L + now.get(Calendar.MINUTE)) * 60
            + now.get(Calendar.SECOND)) * 1000L;
        int out = n - Stars.lit(c, timeOfDay);
        float size = Math.min(w / (n * 1.5f), h * 0.12f);
        float gap = size * 0.5f;
        float x = (w - (n * size + (n - 1) * gap)) / 2 + size / 2;
        float y = h * 0.5f;
        // As dim as the night light allows, so the stars never light up the room.
        double k = Scene.clamp(0.3 + c.warmBrightness) * fade;
        for (int i = 0; i < n; i++, x += size + gap) {
            starPath(x, y, size / 2);
            if (i < out) {
                starPaint.setStyle(Paint.Style.STROKE);
                starPaint.setStrokeWidth(Math.max(1, size * 0.05f));
                starPaint.setColor(0xFF000000 | Scene.scale(used, k * 0.6));
            } else {
                starPaint.setStyle(Paint.Style.FILL);
                starPaint.setColor(0xFF000000 | Scene.scale(lit, k));
            }
            canvas.drawPath(star, starPaint);
        }
    }

    private void starPath(float cx, float cy, float r) {
        star.reset();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * i / 5 - Math.PI / 2;
            float radius = i % 2 == 0 ? r : r * 0.45f;
            float px = cx + (float) (Math.cos(a) * radius);
            float py = cy + (float) (Math.sin(a) * radius);
            if (i == 0) star.moveTo(px, py);
            else star.lineTo(px, py);
        }
        star.close();
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
        liquid = null;
        liquidCanvas = null;
        super.onDetachedFromWindow();
    }
}
