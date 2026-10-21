package io.freemytv;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Container ships sailing slowly along the waterline, stacked with colorful containers: the screensaver's other
 * scene (Ducks draws the sky and the sea around them). By night the bridge windows and the navigation lights are on.
 * Built once; draw() only moves things, with no allocations.
 */
final class Ships {
    // Each ship: start x (0..1 of the lane), speed (px/s at 1080p, negative = sails left), size, lane offset, phase.
    private static final float[][] SHIPS = {
        {0.12f, 11, 0.92f, 4, 0.0f},
        {0.68f, -7, 0.52f, -5, 2.3f},
    };
    private static final int COLS = 10, ROWS = 4;
    private static final int[] BOX = {0xFFD0473E, 0xFF2F6FB5, 0xFFEF8A23, 0xFF3D9A62, 0xFF1F8F9A, 0xFFE8C33A,
            0xFF8C8F96, 0xFFB5523B, 0xFF7A4FA0, 0xFFF2F2F2};

    private final Paint hull = new Paint(Paint.ANTI_ALIAS_FLAG), stripe = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint box = new Paint(), rib = new Paint(), bridge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glass = new Paint(), funnel = new Paint(), mast = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG), foam = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path hullPath = new Path(), stripePath = new Path();
    private final int[][] colors = new int[SHIPS.length][COLS * ROWS], ribs = new int[SHIPS.length][COLS * ROWS];
    private final int[][] heights = new int[SHIPS.length][COLS];
    private final float[] shipX = new float[SHIPS.length], shipY = new float[SHIPS.length];

    Ships() {
        // The ship, facing right, about 580 px long at size 1; y = 0 is the waterline.
        hullPath.moveTo(-284, -48);
        hullPath.lineTo(248, -48);
        hullPath.lineTo(292, -60);                          // the bow rises
        hullPath.cubicTo(286, -24, 274, 6, 250, 22);
        hullPath.lineTo(-268, 22);
        hullPath.lineTo(-288, -12);
        hullPath.close();
        stripePath.moveTo(-287, -10);
        stripePath.lineTo(280, -10);
        stripePath.lineTo(277, -3);
        stripePath.lineTo(-284, -3);
        stripePath.close();
        hull.setColor(0xFF23314F);
        stripe.setColor(0xFFC0392B);
        bridge.setColor(0xFFE9ECF1);
        funnel.setColor(0xFF2B2B2E);
        mast.setColor(0xFF9AA0AA);
        mast.setStyle(Paint.Style.STROKE);
        mast.setStrokeWidth(3);
        rib.setStrokeWidth(1.6f);
        foam.setStyle(Paint.Style.STROKE);
        foam.setStrokeCap(Paint.Cap.ROUND);
        // Each ship's cargo: the same stacks every time (a fixed seed), 2 to 4 high.
        java.util.Random r = new java.util.Random(42);
        for (int s = 0; s < SHIPS.length; s++) {
            for (int c = 0; c < COLS; c++) heights[s][c] = 2 + r.nextInt(3);
            for (int i = 0; i < COLS * ROWS; i++) {
                int col = BOX[r.nextInt(BOX.length)];
                colors[s][i] = col;
                ribs[s][i] = darker(col);
            }
        }
    }

    /** Darker at night, like the ducks (the lights stay bright). */
    void setFilter(ColorFilter f) {
        for (Paint p : new Paint[] {hull, stripe, box, rib, bridge, glass, funnel, mast}) p.setColorFilter(f);
    }

    void draw(Canvas c, float line, float k, double t, float night) {
        for (int i = 0; i < SHIPS.length; i++) drawShip(c, i, line, k, t, night);
    }

    /** The wake behind each ship and the wave at its bow, on top of the water. */
    void drawFoam(Canvas c, float line, float k, double t, float night) {
        foam.setColor(Ui.alpha(0xFFFFFFFF, 0.6f - 0.3f * night));
        for (int i = 0; i < SHIPS.length; i++) {
            float[] d = SHIPS[i];
            float s = d[2] * k, dir = d[1] < 0 ? -1 : 1, x = shipX[i], y = line + (d[3] + 4) * k;
            float stern = x - dir * 286 * s, bow = x + dir * 284 * s;
            foam.setStrokeWidth(3 * s);
            for (int j = 0; j < 4; j++) {
                float len = (70 + j * 70) * s, wob = (float) Math.sin(t * 1.6 + j * 1.3) * 6 * s;
                float yy = y + (j * 6 - 2) * s;
                foam.setAlpha(Math.round(foam.getAlpha() * 0.82f));
                c.drawLine(stern - dir * (10 + j * 26) * s, yy, stern - dir * (10 + j * 26) * s - dir * len + wob, yy, foam);
            }
            foam.setColor(Ui.alpha(0xFFFFFFFF, 0.6f - 0.3f * night));
            c.drawLine(bow - dir * 30 * s, y, bow + dir * 14 * s, y, foam);
        }
    }

    private void drawShip(Canvas c, int i, float line, float k, double t, float night) {
        float[] d = SHIPS[i];
        float span = 1920 + 800, s = d[2];
        double x = (d[0] * span + t * d[1]) % span;
        if (x < 0) x += span;
        float px = (float) (x - 400) * k, dir = d[1] < 0 ? -1 : 1;
        float bob = (float) Math.sin(t * 0.7 + d[4]) * 1.6f, pitch = (float) Math.sin(t * 0.5 + d[4]) * 0.5f;
        shipX[i] = px;
        shipY[i] = line + (d[3] + bob) * k;
        c.save();
        c.translate(px, shipY[i]);
        c.scale(dir * s * k, s * k);
        c.rotate(dir * pitch);
        // Containers: 40 × 22 boxes with ribs, in stacks along the deck.
        int[] col = colors[i], rb = ribs[i];
        for (int cx = 0; cx < COLS; cx++) {
            float x0 = -168 + cx * 41;
            for (int row = 0; row < heights[i][cx]; row++) {
                int n = cx * ROWS + row;
                float y0 = -48 - (row + 1) * 23;
                box.setColor(col[n]);
                c.drawRect(x0, y0, x0 + 40, y0 + 22, box);
                rib.setColor(rb[n]);
                for (int r = 1; r < 4; r++) c.drawLine(x0 + r * 10, y0 + 3, x0 + r * 10, y0 + 19, rib);
            }
        }
        // The bridge at the stern: white decks, a row of windows (lit at night), the funnel.
        c.drawRect(-266, -142, -196, -48, bridge);
        c.drawRect(-276, -142, -186, -134, bridge);
        int lit = 0xFFFFD27A, dark = 0xFF2E3D5C;
        for (int row = 0; row < 4; row++) {
            float wy = -128 + row * 20;
            for (int wx = 0; wx < 5; wx++) {
                boolean on = (wx + row * 2 + i) % 3 != 0;
                glass.setColor(on ? blend(dark, lit, night) : dark);
                c.drawRect(-260 + wx * 12, wy, -252 + wx * 12, wy + 7, glass);
            }
        }
        c.drawRect(-254, -166, -228, -142, funnel);
        stripe.setAlpha(255);
        c.drawRect(-254, -158, -228, -152, stripe);
        c.drawLine(262, -50, 262, -96, mast);
        c.drawPath(hullPath, hull);
        c.drawPath(stripePath, stripe);
        // Lights: the masthead (white) and the bow (green), with a glow at night.
        if (night > 0.01f) {
            float blink = 0.75f + 0.25f * (float) Math.sin(t * 2.2 + d[4]);
            light.setColor(Ui.alpha(0xFFFFFFFF, night * 0.25f * blink));
            c.drawCircle(262, -98, 11, light);
            light.setColor(Ui.alpha(0xFFFFFFFF, night));
            c.drawCircle(262, -98, 3.5f, light);
            light.setColor(Ui.alpha(0xFF4CFF7A, night * 0.25f));
            c.drawCircle(284, -62, 9, light);
            light.setColor(Ui.alpha(0xFF4CFF7A, night));
            c.drawCircle(284, -62, 3, light);
        }
        c.restore();
    }

    private static int blend(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = Math.round((a & 255) * (1 - f) + (b & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int darker(int color) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        return 0xFF000000 | (r * 3 / 4 << 16) | (g * 3 / 4 << 8) | b * 3 / 4;
    }
}
