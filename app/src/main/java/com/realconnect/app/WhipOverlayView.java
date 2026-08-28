package com.realconnect.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

public class WhipOverlayView extends View {

    private float progress = 0f;
    private final PointF targetPoint = new PointF();
    private final PointF startPoint = new PointF();
    private Paint ropePaint;
    private Paint handlePaint;
    private Paint tipPaint;
    private Paint sparkPaint;
    private final Path ropePath = new Path();

    public WhipOverlayView(Context context) {
        super(context);
        init();
    }

    private void init() {
        // Thick braided leather rope
        ropePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ropePaint.setColor(Color.parseColor("#92400E")); // Warm saddle leather brown
        ropePaint.setStyle(Paint.Style.STROKE);
        ropePaint.setStrokeCap(Paint.Cap.ROUND);
        ropePaint.setStrokeJoin(Paint.Join.ROUND);

        // Handle grip paint
        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setColor(Color.parseColor("#451A03")); // Deep dark wood / leather
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeCap(Paint.Cap.ROUND);

        // Cracker / red popper tip
        tipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tipPaint.setColor(Color.parseColor("#EF4444")); // Red popper cracker string
        tipPaint.setStyle(Paint.Style.STROKE);
        tipPaint.setStrokeCap(Paint.Cap.ROUND);

        // Golden spark particles
        sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sparkPaint.setColor(Color.parseColor("#F59E0B"));
        sparkPaint.setStyle(Paint.Style.FILL);
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

        // Calculate target center coordinates relative to screen/decor view
        int[] targetLoc = new int[2];
        targetView.getLocationInWindow(targetLoc);
        float tx = targetLoc[0] + targetView.getWidth() / 2f;
        float ty = targetLoc[1] + targetView.getHeight() / 2f;

        overlay.startAnimation(tx, ty, decor);
    }

    private void startAnimation(float tx, float ty, ViewGroup parent) {
        this.targetPoint.set(tx, ty);

        post(() -> {
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            // Start whip from the MIDDLE of the screen
            this.startPoint.set(w * 0.50f, h * 0.52f);

            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(680); // Slower, smooth & visible motion (680ms)
            animator.setInterpolator(new DecelerateInterpolator(1.2f));
            animator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progress <= 0f || progress >= 1f) return;

        float alpha = progress < 0.78f ? 1.0f : (1.0f - (progress - 0.78f) / 0.22f);
        int alphaInt = (int) (alpha * 255);

        ropePaint.setAlpha(alphaInt);
        handlePaint.setAlpha(alphaInt);
        tipPaint.setAlpha(alphaInt);
        sparkPaint.setAlpha(alphaInt);

        float density = getResources().getDisplayMetrics().density;
        float handleLen = 52 * density;

        // 1. Draw Whip Handle starting in Middle of screen, pointing up-left/up towards the avatar
        float hAngle = (float) Math.toRadians(-75); // Upward angle from center
        float hx = startPoint.x + (float) Math.cos(hAngle) * handleLen;
        float hy = startPoint.y + (float) Math.sin(hAngle) * handleLen;

        handlePaint.setStrokeWidth(11 * density);
        canvas.drawLine(startPoint.x, startPoint.y, hx, hy, handlePaint);

        // Gold ferrule ring on handle
        Paint ferrule = new Paint(Paint.ANTI_ALIAS_FLAG);
        ferrule.setColor(Color.parseColor("#FBBF24"));
        ferrule.setStrokeWidth(13 * density);
        ferrule.setStrokeCap(Paint.Cap.BUTT);
        ferrule.setAlpha(alphaInt);
        canvas.drawCircle(hx, hy, 5.5f * density, ferrule);
        canvas.drawCircle(startPoint.x, startPoint.y, 7.5f * density, ferrule); // Pommel ball

        // 2. Draw Whipping Rope Curve (Physics Wave Propagation from Center to Top Avatar)
        ropePath.reset();
        ropePath.moveTo(hx, hy);

        float sx = hx;
        float sy = hy;
        float tx = targetPoint.x;
        float ty = targetPoint.y;

        float currentTipX;
        float currentTipY;

        if (progress < 0.55f) {
            // Phase 1: The whip uncurls from middle and propels upward in an elastic loop
            float pNorm = progress / 0.55f;
            currentTipX = sx + (tx - sx) * pNorm;
            currentTipY = sy + (ty - sy) * pNorm;

            // Traveling wave apex loop curving gracefully through the air
            float waveApexX = sx + (tx - sx) * 0.45f - (1f - pNorm) * 110 * density;
            float waveApexY = sy + (ty - sy) * 0.35f - (float) Math.sin(pNorm * Math.PI) * 150 * density;

            float cp1X = sx + (waveApexX - sx) * 0.6f;
            float cp1Y = sy - 60 * density * (1f - pNorm);

            ropePath.cubicTo(cp1X, cp1Y, waveApexX, waveApexY, currentTipX, currentTipY);
            ropePaint.setStrokeWidth(6.5f * density);
            canvas.drawPath(ropePath, ropePaint);

            // Tip popper
            tipPaint.setStrokeWidth(3.2f * density);
            canvas.drawLine(currentTipX, currentTipY, currentTipX + 16 * density, currentTipY - 12 * density, tipPaint);

        } else {
            // Phase 2: Whip has struck target avatar, vibrating wave decay
            float pAfter = (progress - 0.55f) / 0.45f;
            currentTipX = tx;
            currentTipY = ty;

            float waveOffset = (float) Math.sin(pAfter * Math.PI * 4) * (1f - pAfter) * 26 * density;

            float cp1X = sx + (tx - sx) * 0.35f + waveOffset * 0.7f;
            float cp1Y = sy + (ty - sy) * 0.25f - 35 * density * (1f - pAfter);

            float cp2X = sx + (tx - sx) * 0.7f - waveOffset;
            float cp2Y = sy + (ty - sy) * 0.65f + waveOffset;

            ropePath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, currentTipX, currentTipY);
            ropePaint.setStrokeWidth(Math.max(2.2f * density, (5.5f - pAfter * 3.2f) * density));
            canvas.drawPath(ropePath, ropePaint);

            // Red cracker at tip
            tipPaint.setStrokeWidth(2.6f * density);
            canvas.drawLine(tx, ty, tx + 18 * density * (1f - pAfter), ty + 14 * density * (1f - pAfter), tipPaint);

            // 3. Impact Flash & Sparks on Avatar
            if (progress >= 0.52f && progress <= 0.88f) {
                float sparkProg = (progress - 0.52f) / 0.36f;
                float sparkRadius = sparkProg * 50 * density;
                float sparkAlpha = (1f - sparkProg);

                sparkPaint.setAlpha((int) (sparkAlpha * 255));

                // Center impact flash
                Paint flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                flashPaint.setColor(Color.parseColor("#FEF08A"));
                flashPaint.setAlpha((int) (sparkAlpha * 220));
                canvas.drawCircle(tx, ty, (1f - sparkProg) * 26 * density, flashPaint);

                // Radiating burst sparks
                int numSparks = 10;
                for (int i = 0; i < numSparks; i++) {
                    double angle = (i * (2 * Math.PI / numSparks)) + (sparkProg * 0.5);
                    float px = tx + (float) Math.cos(angle) * sparkRadius;
                    float py = ty + (float) Math.sin(angle) * sparkRadius;
                    float pSize = Math.max(1.5f, (1f - sparkProg) * 4.5f * density);
                    canvas.drawCircle(px, py, pSize, sparkPaint);

                    // Spark line streaks
                    Paint streakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    streakPaint.setColor(Color.parseColor("#F59E0B"));
                    streakPaint.setStrokeWidth(2.2f * density);
                    streakPaint.setAlpha((int) (sparkAlpha * 230));
                    canvas.drawLine(
                            px - (float) Math.cos(angle) * 7 * density,
                            py - (float) Math.sin(angle) * 7 * density,
                            px, py, streakPaint
                    );
                }
            }
        }
    }
}