package myscanne.com;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

public class Theme {

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static GradientDrawable card(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Prefs.card(c));
        g.setCornerRadius(dp(c, 12));
        g.setStroke(dp(c, 1), Prefs.stroke(c));
        return g;
    }

    public static GradientDrawable input(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(darken(Prefs.card(c), 0.35f));
        g.setCornerRadius(dp(c, 8));
        g.setStroke(dp(c, 1), Prefs.stroke(c));
        return g;
    }

    public static GradientDrawable filled(Context c, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, 10));
        return g;
    }

    public static GradientDrawable outline(Context c, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setCornerRadius(dp(c, 10));
        g.setStroke(dp(c, 1), strokeColor);
        return g;
    }

    public static GradientDrawable pill(Context c, int fill, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, 999));
        g.setStroke(dp(c, 1), strokeColor);
        return g;
    }

    public static void applyFont(Context c, TextView tv, int style) {
        if (Prefs.isSans(c)) tv.setTypeface(Typeface.SANS_SERIF, style);
        else tv.setTypeface(Typeface.MONOSPACE, style);
    }

    public static int darken(int color, float ratio) {
        int r = (int) (Color.red(color) * (1 - ratio));
        int g = (int) (Color.green(color) * (1 - ratio));
        int b = (int) (Color.blue(color) * (1 - ratio));
        return Color.rgb(r, g, b);
    }

    public static int onColor(int bg) {
        double lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255.0;
        return lum > 0.6 ? 0xFF0D0F14 : 0xFFFFFFFF;
    }
}
