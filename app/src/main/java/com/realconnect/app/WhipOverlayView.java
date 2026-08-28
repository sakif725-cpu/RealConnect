package com.realconnect.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

public class WhipOverlayView extends View {

    private static final int NUM_SEGMENTS = 38;

    private float progress = 0f;
    private final PointF targetPoint = new PointF();
    private final PointF startPoint = new PointF();

    // 3D Rope Joint Chains
    private final float[] segX = new float[NUM_SEGMENTS];
    private final float[] segY = new float[NUM_SEGMENTS];
    private final float[] segZ = new float[NUM_SEGMENTS];
    private final float[] segThickness = new float[NUM_SEGMENTS];

    // Paints
    private Paint shadowPaint;
    private Paint ropeBasePaint;
    private Paint ropeHighlightPaint;
    private Paint ropeCorePaint;
    private Paint handlePaint;
    private Paint ferrulePaint;
    private Paint tipCrackerPaint;
    private Paint sparkPaint;
    private Paint flashPaint;
    private Paint ringPaint;

    private final Path pathMain = new Path();
    private final Path pathHighlight = new Path();
    private final Path pathShadow = new Path();

    public WhipOverlayView(Context context) {
        super(context);
        init();
    }

    private void init() {
        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.parseColor("#44000000"));
        shadowPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStrokeCap(Paint.Cap.ROUND);
        shadowPaint.setStrokeJoin(Paint.Join.ROUND);

        ropeBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ropeBasePaint.setColor(Color.parseColor("#5C240A")); // Dark saddle leather base
        ropeBasePaint.setStyle(Paint.Style.STROKE);
        ropeBasePaint.setStrokeCap(Paint.Cap.ROUND);
        ropeBasePaint.setStrokeJoin(Paint.Join.ROUND);

        ropeCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ropeCorePaint.setColor(Color.parseColor("#9A3412")); // Rich cognac leather midtone
        ropeCorePaint.setStyle(Paint.Style.STROKE);
        ropeCorePaint.setStrokeCap(Paint.Cap.ROUND);
        ropeCorePaint.setStrokeJoin(Paint.Join.ROUND);

        ropeHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ropeHighlightPaint.setColor(Color.parseColor("#EA580C")); // 3D Top specular sheen
        ropeHighlightPaint.setStyle(Paint.Style.STROKE);
        ropeHighlightPaint.setStrokeCap(Paint.Cap.ROUND);
        ropeHighlightPaint.setStrokeJoin(Paint.Join.ROUND);

        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setStyle(Paint.Style.FILL);

        ferrulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ferrulePaint.setColor(Color.parseColor("#F59E0B")); // Polished brass gold
        ferrulePaint.setStyle(Paint.Style.FILL);

        tipCrackerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tipCrackerPaint.setColor(Color.parseColor("#EF4444")); // Red silk popper cracker
        tipCrackerPaint.setStyle(Paint.Style.STROKE);
        tipCrackerPaint.setStrokeCap(Paint.Cap.ROUND);

        sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sparkPaint.setStyle(Paint.Style.FILL);

        flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        flashPaint.setStyle(Paint.Style.FILL);

        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public static void show(Activity activity, View targetView) {
        if (activity == null || targetView == null) return;

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        WhipOverlayView overlay = new WhipOverlayView(activity);

        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        decor.addView(overlay, lp);

        int[] targetLoc = new int[2];
        targetView.getLocationInWindow(targetLoc);
        float tx = targetLoc[0] + targetView.getWidth() / 2f;
        float ty = targetLoc[1] + targetView.getHeight() / 2f;

        overlay.start3DAnimation(tx, ty, decor);
    }

    private void start3DAnimation(float tx, float ty, ViewGroup parent) {
        this.targetPoint.set(tx, ty);

        post(() -> {
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            // Start handle from lower-left side with natural tilt
            this.startPoint.set(w * 0.12f, h * 0.78f);

            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(520); // 520ms realistic whip kinematic speed
            animator.setInterpolator(new DecelerateInterpolator(1.2f));
            animator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                computePhysicsSegments();
                invalidate();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (parent != null) {
                        parent.removeView(WhipOverlayView.this);
                    }
                }
            });
            animator.start();
        });
    }

    /**
     * Calculates 3D Snake-like Traveling Wave Physics across 38 interconnected joints
     */
    private void computePhysicsSegments() {
        float density = getResources().getDisplayMetrics().density;
        float sx = startPoint.x;
        float sy = startPoint.y;
        float tx = targetPoint.x;
        float ty = targetPoint.y;

        // Handle end point (origin of the rope thong)
        float hAngle = (float) Math.toRadians(-48);
        float hLen = 54 * density;
        float hx = sx + (float) Math.cos(hAngle) * hLen;
        float hy = sy + (float) Math.sin(hAngle) * hLen;

        float totalDist = (float) Math.hypot(tx - hx, ty - hy);

        // Calculate thickness tapering for each segment (Snake-like: 16dp at base -> 1.5dp at tip)
        for (int i = 0; i < NUM_SEGMENTS; i++) {
            float frac = (float) i / (NUM_SEGMENTS - 1);
            // Non-linear realistic whip taper
            float t = (1f - frac);
            segThickness[i] = (1.5f + (t * t * 13.5f)) * density;
        }

        float strikeTime = 0.58f; // Moment of impact crack

        if (progress < strikeTime) {
            // PHASE 1: Supersonic loop wave propagation from handle to tip
            float pNorm = progress / strikeTime; // 0.0 -> 1.0

            // The main traveling loop wave position along the rope (accelerates towards the tip)
            float waveCenterFrac = (float) Math.pow(pNorm, 1.4); // Accelerating wave

            for (int i = 0; i < NUM_SEGMENTS; i++) {
                float segFrac = (float) i / (NUM_SEGMENTS - 1);

                // Base straight interpolation towards target
                float interpProg = Math.min(1.0f, pNorm * 1.35f);
                float baseX = hx + (tx - hx) * segFrac * interpProg;
                float baseY = hy + (ty - hy) * segFrac * interpProg;

                // 3D Traveling Snake Wave & Undulation
                float distFromWave = (segFrac - waveCenterFrac);
                float waveEnv = (float) Math.exp(-18.0 * distFromWave * distFromWave); // Gaussian wave packet

                // Snake harmonic ripple behind the main wave
                float ripple = (float) Math.sin((segFrac - pNorm) * Math.PI * 4.0) * (1f - segFrac) * 22 * density;

                // Perpendicular vector for wave displacement
                float dx = (tx - hx) / totalDist;
                float dy = (ty - hy) / totalDist;
                float nx = -dy;
                float ny = dx;

                // 3D loop height (coming towards the viewer in Z axis, swinging up and curling)
                float loopHeight = waveEnv * 130 * density * (1f - segFrac * 0.4f);
                float loopLateral = (float) Math.sin(pNorm * Math.PI) * (1f - segFrac) * 45 * density;

                segX[i] = baseX + (nx * loopHeight) + (ny * loopLateral) + (nx * ripple * 0.3f);
                segY[i] = baseY + (ny * loopHeight) - (nx * loopLateral) + (ny * ripple * 0.3f);
                segZ[i] = waveEnv * 40 * density; // 3D Z elevation
            }
        } else {
            // PHASE 2: Strike Impact, Supersonic Snap, and Harmonic S-Curve Decay
            float pAfter = (progress - strikeTime) / (1f - strikeTime); // 0.0 -> 1.0

            for (int i = 0; i < NUM_SEGMENTS; i++) {
                float segFrac = (float) i / (NUM_SEGMENTS - 1);

                float baseX = hx + (tx - hx) * segFrac;
                float baseY = hy + (ty - hy) * segFrac;

                // S-curve snake vibration decaying over time
                float decay = (float) Math.exp(-4.5 * pAfter) * (1f - pAfter);
                float sWave1 = (float) Math.sin((segFrac * 3.5 - pAfter * 6.0) * Math.PI) * 28 * density * decay * (1f - segFrac);
                float sWave2 = (float) Math.sin((segFrac * 6.0 + pAfter * 4.0) * Math.PI) * 14 * density * decay * (segFrac);

                float dx = (tx - hx) / totalDist;
                float dy = (ty - hy) / totalDist;
                float nx = -dy;
                float ny = dx;

                if (i == NUM_SEGMENTS - 1) {
                    // Tip stays locked onto avatar target during impact
                    segX[i] = tx;
                    segY[i] = ty;
                } else {
                    segX[i] = baseX + (nx * (sWave1 + sWave2));
                    segY[i] = baseY + (ny * (sWave1 + sWave2));
                }
                segZ[i] = decay * 15 * density * (1f - segFrac);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progress <= 0f || progress >= 1f) return;

        float alpha = progress < 0.82f ? 1.0f : (1.0f - (progress - 0.82f) / 0.18f);
        int alphaInt = (int) (alpha * 255);

        float density = getResources().getDisplayMetrics().density;
        float tx = targetPoint.x;
        float ty = targetPoint.y;

        // 1. Draw 3D Drop Shadow on Floor/Screen Layer
        draw3DDropShadow(canvas, alphaInt, density);

        // 2. Draw 3D Wooden & Braided Leather Handle
        draw3DHandle(canvas, alphaInt, density);

        // 3. Draw 3D Snake-like Tapered Leather Rope with Beveled Lighting
        draw3DTaperedRope(canvas, alphaInt, density);

        // 4. Draw Red Silk Popper Cracker at Tip
        drawTipCracker(canvas, alphaInt, density);

        // 5. Draw Explosive Supersonic Crack Sparks & Shockwave Rings on Strike
        if (progress >= 0.54f && progress <= 0.92f) {
            drawImpactShockwave(canvas, tx, ty, (progress - 0.54f) / 0.38f, density);
        }
    }

    private void draw3DDropShadow(Canvas canvas, int alphaInt, float density) {
        pathShadow.reset();
        pathShadow.moveTo(segX[0] + 12 * density, segY[0] + 18 * density);

        for (int i = 1; i < NUM_SEGMENTS; i++) {
            float shadowOffsetX = (10 + segZ[i] * 0.4f) * density;
            float shadowOffsetY = (16 + segZ[i] * 0.6f) * density;
            pathShadow.lineTo(segX[i] + shadowOffsetX, segY[i] + shadowOffsetY);
        }

        shadowPaint.setAlpha((int) (alphaInt * 0.38f));
        shadowPaint.setStrokeWidth(12 * density);
        canvas.drawPath(pathShadow, shadowPaint);
    }

    private void draw3DHandle(Canvas canvas, int alphaInt, float density) {
        float sx = startPoint.x;
        float sy = startPoint.y;
        float hAngle = (float) Math.toRadians(-48);
        float hLen = 54 * density;
        float hx = sx + (float) Math.cos(hAngle) * hLen;
        float hy = sy + (float) Math.sin(hAngle) * hLen;

        // 3D Cylindrical Shader for Handle
        LinearGradient handleShader = new LinearGradient(
                sx, sy, sx + 20 * density, sy + 20 * density,
                new int[]{Color.parseColor("#78350F"), Color.parseColor("#3F1B05"), Color.parseColor("#1C0B02")},
                null, Shader.TileMode.CLAMP
        );
        handlePaint.setShader(handleShader);
        handlePaint.setAlpha(alphaInt);

        // Draw Thick Handle Cylinder
        Paint handleStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        handleStroke.setShader(handleShader);
        handleStroke.setStrokeWidth(14 * density);
        handleStroke.setStrokeCap(Paint.Cap.ROUND);
        handleStroke.setAlpha(alphaInt);
        canvas.drawLine(sx, sy, hx, hy, handleStroke);

        // Pommel Ball at Handle Base
        ferrulePaint.setAlpha(alphaInt);
        canvas.drawCircle(sx, sy, 8 * density, ferrulePaint);

        // Brass Collars (Ferrules) with 3D highlight
        canvas.drawCircle(hx, hy, 7 * density, ferrulePaint);
        canvas.drawCircle(sx + (hx - sx) * 0.5f, sy + (hy - sy) * 0.5f, 6.5f * density, ferrulePaint);
    }

    private void draw3DTaperedRope(Canvas canvas, int alphaInt, float density) {
        // Multi-pass segmented rendering to give 3D cylindrical lighting & braided thickness taper
        for (int i = 0; i < NUM_SEGMENTS - 1; i++) {
            float x1 = segX[i];
            float y1 = segY[i];
            float x2 = segX[i + 1];
            float y2 = segY[i + 1];
            float thickness = (segThickness[i] + segThickness[i + 1]) / 2f;

            // Pass 1: Dark Leather Outer Ambient Base
            ropeBasePaint.setStrokeWidth(thickness);
            ropeBasePaint.setAlpha(alphaInt);
            canvas.drawLine(x1, y1, x2, y2, ropeBasePaint);

            // Pass 2: Warm Cognac Inner Leather Core
            ropeCorePaint.setStrokeWidth(thickness * 0.65f);
            ropeCorePaint.setAlpha(alphaInt);
            canvas.drawLine(x1, y1, x2, y2, ropeCorePaint);

            // Pass 3: Top-edge 3D Specular Sunlight Sheen
            ropeHighlightPaint.setStrokeWidth(Math.max(1.0f * density, thickness * 0.28f));
            ropeHighlightPaint.setAlpha((int) (alphaInt * 0.9f));
            float offset = thickness * 0.18f;
            canvas.drawLine(x1 - offset, y1 - offset, x2 - offset, y2 - offset, ropeHighlightPaint);

            // Pass 4: Braided Diamond Pattern Weave Nodes (every alternate segment)
            if (i % 2 == 0 && thickness > 3 * density) {
                Paint braidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                braidPaint.setColor(Color.parseColor("#FDBA74"));
                braidPaint.setAlpha((int) (alphaInt * 0.55f));
                braidPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(x1, y1, thickness * 0.22f, braidPaint);
            }
        }
    }

    private void drawTipCracker(Canvas canvas, int alphaInt, float density) {
        float tipX = segX[NUM_SEGMENTS - 1];
        float tipY = segY[NUM_SEGMENTS - 1];
        float prevX = segX[NUM_SEGMENTS - 2];
        float prevY = segY[NUM_SEGMENTS - 2];

        float angle = (float) Math.atan2(tipY - prevY, tipX - prevX);
        float crackerLen = 22 * density;
        float cx = tipX + (float) Math.cos(angle) * crackerLen;
        float cy = tipY + (float) Math.sin(angle) * crackerLen;

        tipCrackerPaint.setStrokeWidth(2.2f * density);
        tipCrackerPaint.setAlpha(alphaInt);
        canvas.drawLine(tipX, tipY, cx, cy, tipCrackerPaint);

        // Cracker tassel tip fray
        canvas.drawCircle(cx, cy, 2.5f * density, tipCrackerPaint);
    }

    private void drawImpactShockwave(Canvas canvas, float tx, float ty, float pNorm, float density) {
        float sparkAlpha = (1f - pNorm);
        int alphaInt = (int) (sparkAlpha * 255);

        // 1. Glowing Radial Impact Flash
        RadialGradient flashGradient = new RadialGradient(
                tx, ty, 55 * density,
                new int[]{Color.parseColor("#FFFBEB"), Color.parseColor("#FDE047"), Color.TRANSPARENT},
                new float[]{0.0f, 0.4f, 1.0f},
                Shader.TileMode.CLAMP
        );
        flashPaint.setShader(flashGradient);
        flashPaint.setAlpha((int) (sparkAlpha * 230));
        canvas.drawCircle(tx, ty, (1f - pNorm * 0.5f) * 45 * density, flashPaint);

        // 2. Supersonic Expanding Shockwave Ring
        ringPaint.setStrokeWidth(3.5f * density * (1f - pNorm));
        ringPaint.setColor(Color.parseColor("#F59E0B"));
        ringPaint.setAlpha(alphaInt);
        canvas.drawCircle(tx, ty, pNorm * 65 * density, ringPaint);

        // Secondary Outer Shockwave
        Paint ring2 = new Paint(ringPaint);
        ring2.setColor(Color.parseColor("#EF4444"));
        ring2.setStrokeWidth(2 * density * (1f - pNorm));
        ring2.setAlpha((int) (alphaInt * 0.7f));
        canvas.drawCircle(tx, ty, pNorm * 85 * density, ring2);

        // 3. Directional 3D Starburst Sparks with motion blur tails
        int numSparks = 12;
        sparkPaint.setColor(Color.parseColor("#FEF08A"));
        sparkPaint.setAlpha(alphaInt);

        Paint streakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        streakPaint.setColor(Color.parseColor("#F97316"));
        streakPaint.setStrokeWidth(2.5f * density);
        streakPaint.setAlpha(alphaInt);

        float sparkDist = pNorm * 75 * density;
        for (int i = 0; i < numSparks; i++) {
            double ang = (i * (2 * Math.PI / numSparks)) + (pNorm * 0.4);
            float px = tx + (float) Math.cos(ang) * sparkDist;
            float py = ty + (float) Math.sin(ang) * sparkDist;

            // Spark head
            canvas.drawCircle(px, py, Math.max(1f, (1f - pNorm) * 4.5f * density), sparkPaint);

            // Motion blur tail
            float tailX = px - (float) Math.cos(ang) * (8 * density * (1f - pNorm));
            float tailY = py - (float) Math.sin(ang) * (8 * density * (1f - pNorm));
            canvas.drawLine(tailX, tailY, px, py, streakPaint);
        }
    }
}