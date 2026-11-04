// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;
import me.jxl.kiosk.plugins.KsTheme;

/**
 * The button on the night light: Soother starts the soother, End soother fades it back to the night light. It is
 * drawn dim in the night light colour so it does not light up a dark bedroom, and checks once a second so the label
 * follows the soother ending on its own. In Night mode it takes the Night colour, or hides.
 */
final class SleepButton extends TextView {
    interface Source {
        Config config();
        boolean showing();
        void toggleShow();
        /** True while Night mode is on and the room is dark. */
        boolean night();
    }

    private static final long REFRESH_MS = 1000;

    private final Source source;
    private final KsTheme theme;
    private final Runnable refresh = this::refresh;

    SleepButton(Context context, KsTheme theme, Source source) {
        super(context);
        this.source = source;
        this.theme = theme;
        theme.stylePill(this, false);
        setOnClickListener(v -> {
            source.toggleShow();
            refresh();
        });
        refresh();
    }

    private void refresh() {
        removeCallbacks(refresh);
        Config c = source.config();
        boolean night = source.night();
        int warm = night ? c.nightColor : c.warmColor;
        setText(source.showing() ? "End soother" : "Soother");
        setTextColor(0x99000000 | warm);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(theme.px(100));
        shape.setColor(0x66000000);
        shape.setStroke(Math.max(1, theme.px(1)), 0x44000000 | warm);
        setBackground(shape);
        // Hidden, it takes no taps either; a double tap still closes the screensaver.
        setVisibility(night && c.nightHide ? INVISIBLE : VISIBLE);
        if (isAttachedToWindow()) postDelayed(refresh, REFRESH_MS);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refresh();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(refresh);
        super.onDetachedFromWindow();
    }
}
