package io.freemytv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The background without a photo: a pond with rubber ducks bobbing along the waterline, above the shelf. By day a
 * pale sky with the sun (dark text stays readable); at night a dark blue sky with stars and a moon. With setShips,
 * container ships sail there instead (the screensaver's other scene). Everything is built once per
 * size: draw() only moves things, with no allocations.
 */
final class Ducks {
    static final float HOME_LINE = 782;     // in 1920×1080 units: below the clock, above the shelf (814)
    static final float SAVER_LINE = 872;    // the screensaver: below its centered clock and weather
    private static final int STARS = 46;
    private static final float BLEED = 24;          // drawn past the edges: the screensaver moves the pond a little

    // Each duck: start x (0..1 of the lane), speed (px/s at 1080p, negative = swims left), size, lane offset, phase.
    private static final float[][] DUCKS = {
        {0.04f, 22, 1.15f, 6, 0.0f},
        {0.27f, -15, 0.8f, -4, 1.7f},
        {0.46f, 18, 0.95f, 2, 3.1f},
        {0.66f, -24, 1.25f, 8, 4.4f},
        {0.86f, 13, 0.72f, -6, 5.6f},
    };

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG), head = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wing = new Paint(Paint.ANTI_ALIAS_FLAG), beak = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eye = new Paint(Paint.ANTI_ALIAS_FLAG), shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripple = new Paint(Paint.ANTI_ALIAS_FLAG), streak = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bodyPath = new Path(), wingPath = new Path(), beakPath = new Path(), water = new Path();
    private final RectF oval = new RectF();
    private final float[] starX = new float[STARS], starY = new float[STARS], starR = new float[STARS], starP = new float[STARS];

    private final float WATERLINE;
    private Ships ships;
    private int w, h;
    private float k = 1;
    private Shader daySky, nightSky, dayWater, nightWater;
    private LightingColorFilter dim;
    private int dimLevel = -1;

    // The still pond for the glass (no ducks, no motion): drawn small, read back as pixels.
    private Bitmap glassBitmap, frostBitmap;
    private Canvas glassCanvas, frostCanvas;

    Ducks(float waterline) {
        WATERLINE = waterline;
        // The duck, facing right, in units of about 1 px for a 100 px duck; y = 0 is the waterline.
        bodyPath.moveTo(-46, -6);
        bodyPath.cubicTo(-53, -28, -52, -44, -45, -50);     // up to the tail
        bodyPath.cubicTo(-38, -38, -28, -33, -8, -34);      // the back
        bodyPath.lineTo(24, -34);
        bodyPath.cubicTo(46, -35, 54, -20, 49, -6);         // the chest
        bodyPath.cubicTo(45, 6, 28, 10, 0, 10);
        bodyPath.cubicTo(-28, 10, -42, 5, -46, -6);
        bodyPath.close();
        wingPath.moveTo(-26, -22);
        wingPath.cubicTo(-14, -36, 12, -32, 18, -20);
        wingPath.cubicTo(8, -8, -12, -8, -26, -22);
        wingPath.close();
        beakPath.moveTo(34, -63);
        beakPath.cubicTo(50, -69, 60, -61, 56, -53);
        beakPath.cubicTo(52, -47, 42, -47, 34, -50);
        beakPath.close();
        body.setShader(new LinearGradient(0, -44, 0, 10, 0xFFFFE34D, 0xFFFFB31A, Shader.TileMode.CLAMP));
        head.setShader(new LinearGradient(0, -82, 0, -36, 0xFFFFE867, 0xFFFFC21F, Shader.TileMode.CLAMP));
        wing.setColor(0xFFF5A812);
        beak.setShader(new LinearGradient(0, -68, 0, -48, 0xFFFF9A2E, 0xFFF06A0C, Shader.TileMode.CLAMP));
        eye.setColor(0xFF1D1D1F);
        shine.setColor(0xFFFFFFFF);
        ripple.setStyle(Paint.Style.STROKE);
        streak.setStyle(Paint.Style.STROKE);
        streak.setStrokeCap(Paint.Cap.ROUND);
        // Stars: the same sky every night, kept off the waterline.
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < STARS; i++) {
            starX[i] = r.nextFloat() * 1920;
            starY[i] = 20 + r.nextFloat() * (WATERLINE - 120);
            starR[i] = 1.2f + r.nextFloat() * 2f;
            starP[i] = r.nextFloat() * 6.28f;
        }
    }

    /** Container ships instead of the ducks. */
    void setShips(boolean on) {
        ships = on ? new Ships() : null;
        dimLevel = -1;
    }

    void setSize(int width, int height) {
        if (width == w && height == h) return;
        w = width;
        h = height;
        k = w / 1920f;
        float line = WATERLINE * k;
        daySky = new LinearGradient(0, 0, 0, line, 0xFFFFF6DE, 0xFFD9F0FB, Shader.TileMode.CLAMP);
        nightSky = new LinearGradient(0, 0, 0, line, 0xFF070D22, 0xFF1C2F5A, Shader.TileMode.CLAMP);
        dayWater = new LinearGradient(0, line, 0, h, 0xFF9FDBF3, 0xFF3F97CF, Shader.TileMode.CLAMP);
        nightWater = new LinearGradient(0, line, 0, h, 0xFF1F3C6B, 0xFF08132B, Shader.TileMode.CLAMP);
    }

    /** The pond at `t` seconds; `night` from 0 (day) to 1 (night). */
    void draw(Canvas c, float night, double t) {
        drawSky(c, night, t);
        dimDucks(night);
        float line = WATERLINE * k;
        if (ships != null) {
            ships.draw(c, line, k, t, night);
            drawWater(c, night, t, true);
            ships.drawFoam(c, line, k, t, night);
            return;
        }
        for (int i = 0; i < DUCKS.length; i++) drawDuck(c, DUCKS[i], line, t, night);
        drawWater(c, night, t, true);
    }

    /** The pond without ducks, small: what the glass and the frosted glass show (Backdrop.GW × GH and FW × FH). */
    void still(int[] glass, int[] frost, float night) {
        if (w == 0) return;
        if (glassBitmap == null) {
            glassBitmap = Bitmap.createBitmap(Backdrop.GW, Backdrop.GH, Bitmap.Config.ARGB_8888);
            frostBitmap = Bitmap.createBitmap(Backdrop.FW, Backdrop.FH, Bitmap.Config.ARGB_8888);
            glassCanvas = new Canvas(glassBitmap);
            frostCanvas = new Canvas(frostBitmap);
        }
        stillInto(glassCanvas, Backdrop.GW, night);
        glassBitmap.getPixels(glass, 0, Backdrop.GW, 0, 0, Backdrop.GW, Backdrop.GH);
        stillInto(frostCanvas, Backdrop.FW, night);
        frostBitmap.getPixels(frost, 0, Backdrop.FW, 0, 0, Backdrop.FW, Backdrop.FH);
    }

    private void stillInto(Canvas c, int width, float night) {
        c.save();
        c.scale(width / (float) w, width / (float) w);
        drawSky(c, night, 0);
        drawWater(c, night, 0, false);
        c.restore();
    }

    private void drawSky(Canvas c, float night, double t) {
        float line = WATERLINE * k;
        fill.setColor(0xFF000000);
        if (night < 0.999f) {
            fill.setShader(daySky);
            c.drawRect(0, -BLEED * k, w, line + 4 * k, fill);
        }
        if (night < 0.999f) drawSun(c, 1 - night, t);
        if (night > 0.001f) {
            fill.setShader(nightSky);
            fill.setAlpha(Math.round(255 * night));
            c.drawRect(0, -BLEED * k, w, line + 4 * k, fill);
            fill.setShader(null);
            // Twinkling stars and a moon with a soft glow (high, away from the clock and the buttons).
            for (int i = 0; i < STARS; i++) {
                float tw = 0.55f + 0.45f * (float) Math.sin(t * 0.9 + starP[i]);
                fill.setColor(Ui.alpha(0xFFFFFFFF, night * tw * 0.85f));
                c.drawCircle(starX[i] * k, starY[i] * k, starR[i] * k, fill);
            }
            float mx = 1540 * k, my = 300 * k;
            fill.setColor(Ui.alpha(0xFFFFF4C8, night * 0.08f));
            c.drawCircle(mx, my, 120 * k, fill);
            fill.setColor(Ui.alpha(0xFFFFF4C8, night * 0.12f));
            c.drawCircle(mx, my, 76 * k, fill);
            fill.setColor(Ui.alpha(0xFFFFF6D6, night));
            c.drawCircle(mx, my, 46 * k, fill);
        }
        fill.setShader(null);
        fill.setAlpha(255);
    }

    /** The sun where the moon is at night: a warm disc with a glow and slowly turning rays. */
    private void drawSun(Canvas c, float a, double t) {
        float sx = 1540 * k, sy = 300 * k;
        fill.setShader(null);
        fill.setColor(Ui.alpha(0xFFFFD86B, a * 0.16f));
        c.drawCircle(sx, sy, 150 * k, fill);
        fill.setColor(Ui.alpha(0xFFFFD86B, a * 0.24f));
        c.drawCircle(sx, sy, 100 * k, fill);
        streak.setStrokeWidth(7 * k);
        streak.setColor(Ui.alpha(0xFFFFC23C, a * 0.85f));
        c.save();
        c.rotate((float) (t * 4 % 360), sx, sy);
        for (int i = 0; i < 12; i++) {
            c.drawLine(sx, sy - 72 * k, sx, sy - 94 * k, streak);
            c.rotate(30, sx, sy);
        }
        c.restore();
        fill.setColor(Ui.alpha(0xFFFFCC33, a));
        c.drawCircle(sx, sy, 56 * k, fill);
        fill.setColor(Ui.alpha(0xFFFFE680, a));
        c.drawCircle(sx - 6 * k, sy - 6 * k, 44 * k, fill);
    }

    /** The water in front of the ducks' bellies, with a gently moving surface and drifting light streaks. */
    private void drawWater(Canvas c, float night, double t, boolean moving) {
        float line = WATERLINE * k, step = 24 * k;
        water.rewind();
        water.moveTo(0, h + BLEED * k);
        for (float x = 0; x <= w + step; x += step) {
            double a = x / k * 0.011 + t * 0.9;
            water.lineTo(x, line + (float) (Math.sin(a) * 2.5 + Math.sin(a * 0.43 + 1.3) * 2) * k);
        }
        water.lineTo(w + step, h + BLEED * k);
        water.close();
        fill.setColor(0xFF000000);
        if (night < 0.999f) {
            fill.setShader(dayWater);
            c.drawPath(water, fill);
        }
        if (night > 0.001f) {
            fill.setShader(nightWater);
            fill.setAlpha(Math.round(255 * night));
            c.drawPath(water, fill);
        }
        fill.setShader(null);
        fill.setAlpha(255);
        // Light on the water: short strokes in rows, drifting slowly sideways.
        streak.setStrokeWidth(3 * k);
        streak.setColor(Ui.alpha(night > 0.5f ? 0xFFFFF4C8 : 0xFFFFFFFF, 0.45f - 0.25f * night));
        float span = 1920 + 300;
        for (int row = 0; row < 4; row++) {
            float y = (WATERLINE + 34 + row * 62) * k, len = (90 - row * 10) * k;
            for (int i = 0; i < 5; i++) {
                double x = (i * 471 + row * 197 + (moving ? t * (row % 2 == 0 ? 9 : -7) : 0)) % span;
                if (x < 0) x += span;
                float sx = (float) (x - 150) * k;
                c.drawLine(sx, y, sx + len, y, streak);
            }
        }
    }

    private void drawDuck(Canvas c, float[] d, float line, double t, float night) {
        float span = 1920 + 260, s = d[2] * 1.05f;
        double x = (d[0] * span + t * d[1]) % span;
        if (x < 0) x += span;
        float px = (float) (x - 130) * k;
        float bob = (float) Math.sin(t * 1.4 + d[4]) * 3.5f;
        float tilt = (float) Math.sin(t * 1.4 + d[4] + 0.9) * 4.5f;
        float py = line + (d[3] + bob) * k;
        // A ring on the water around the duck, swelling with its bob.
        ripple.setStrokeWidth(2.5f * k);
        ripple.setColor(Ui.alpha(0xFFFFFFFF, 0.5f - 0.3f * night));
        float rw = (64 + bob * 2) * s * k, rh = (9 + bob * 0.4f) * s * k;
        oval.set(px - rw, line + 8 * s * k - rh, px + rw, line + 8 * s * k + rh);
        c.drawOval(oval, ripple);
        c.save();
        c.translate(px, py);
        c.scale(d[1] < 0 ? -s * k : s * k, s * k);
        c.rotate(d[1] < 0 ? -tilt : tilt);
        c.drawPath(bodyPath, body);
        c.drawPath(wingPath, wing);
        c.drawCircle(16, -58, 23, head);
        c.drawPath(beakPath, beak);
        c.drawCircle(23, -63, 4.4f, eye);
        shine.setAlpha(255);
        c.drawCircle(24.6f, -64.6f, 1.5f, shine);
        shine.setAlpha(110);
        oval.set(2, -77, 16, -68);
        c.drawOval(oval, shine);
        oval.set(18, -30, 38, -22);
        c.drawOval(oval, shine);
        c.restore();
    }

    /** At night the ducks are a little darker (moonlight); the filter changes only in 16 steps. */
    private void dimDucks(float night) {
        int level = Math.round(night * 16);
        if (level == dimLevel) return;
        dimLevel = level;
        int v = Math.round(255 * (1 - 0.32f * level / 16f)), b = Math.round(255 * (1 - 0.18f * level / 16f));
        dim = level == 0 ? null : new LightingColorFilter(0xFF000000 | (v << 16) | (v << 8) | b, 0);
        for (Paint p : new Paint[] {body, head, wing, beak, shine}) p.setColorFilter(dim);
        if (ships != null) ships.setFilter(dim);
    }
}
