// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;
import me.jxl.kiosk.plugins.KsTheme;

/**
 * The night light with its controls, for Kiosk Satellite releases that pass single taps to the screensaver: the Go
 * to sleep button, and a hint that a double tap closes it. KS draws no hint itself. The hint shows for a few seconds
 * after each touch, then fades so it does not light up the room.
 */
final class NightLightView extends FrameLayout {
    private static final long HINT_MS = 8000;

    private final TextView hint;
    private final SleepButton.Source source;
    private final Runnable fadeHint = this::fadeHint;

    NightLightView(Context context, KsTheme theme, GlowView glow, SleepButton.Source source) {
        super(context);
        this.source = source;
        int margin = theme.px(24);
        addView(glow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LayoutParams buttonAt = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.END);
        buttonAt.setMargins(margin, margin, margin, margin);
        addView(new SleepButton(context, theme, source), buttonAt);

        hint = new TextView(context);
        theme.styleText(hint, 14, 400, "onSurface");
        hint.setTextColor(0x66000000 | source.config().warmColor);
        hint.setText("Double tap to close");
        LayoutParams hintAt = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.START);
        // Level with the middle of the button.
        hintAt.setMargins(margin, margin, margin, margin + theme.px(12));
        addView(hint, hintAt);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) showHint();
        return super.dispatchTouchEvent(event);
    }

    private void showHint() {
        removeCallbacks(fadeHint);
        hint.animate().cancel();
        Config c = source.config();
        boolean night = source.night();
        hint.setTextColor(0x66000000 | (night ? c.nightColor : c.warmColor));
        hint.animate().alpha(night && c.nightHide ? 0f : 1f).setDuration(300).start();
        postDelayed(fadeHint, HINT_MS);
    }

    private void fadeHint() {
        hint.animate().alpha(0f).setDuration(1500).start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        showHint();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(fadeHint);
        hint.animate().cancel();
        super.onDetachedFromWindow();
    }
}
