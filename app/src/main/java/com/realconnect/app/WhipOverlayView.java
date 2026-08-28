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
    private PointF targetPoint = new PointF();
    private PointF startPoint = new PointF();
    private Paint ropePaint;
    private Paint handlePaint;
    private Paint tipPaint;
    private Paint sparkPaint;
    private Path ropePath = new Path();

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

            // Start whip from bottom-left or side below
            this.startPoint.set(w * 0.15f, h * 0.75f);

            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(420); // Fast snappy lash
            animator.setInterpolator(new DecelerateInterpolator(1.4f));
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

        float alpha = progress < 0.75f ? 1.0f : (1.0f - (progress - 0.75f) / 0.25f);
        int alphaInt = (int) (alpha * 255);

        ropePaint.setAlpha(alphaInt);
        handlePaint.setAlpha(alphaInt);
        tipPaint.setAlpha(alphaInt);
        sparkPaint.setAlpha(alphaInt);

        float density = getResources().getDisplayMetrics().density;
        float handleLen = 50 * density;

        // 1. Draw Whip Handle (from startPoint pointing slightly up-right)
        float hAngle = (float) Math.toRadians(-45);
        float hx = startPoint.x + (float) Math.cos(hAngle) * handleLen;
        float hy = startPoint.y + (float) Math.sin(hAngle) * handleLen;

        handlePaint.setStrokeWidth(10 * density);
        canvas.drawLine(startPoint.x, startPoint.y, hx, hy, handlePaint);

        // Gold ferrule ring on handle
        Paint ferrule = new Paint(Paint.ANTI_ALIAS_FLAG);
        ferrule.setColor(Color.parseColor("#FBBF24"));
        ferrule.setStrokeWidth(12 * density);
        ferrule.setStrokeCap(Paint.Cap.BUTT);
        ferrule.setAlpha(alphaInt);
        canvas.drawCircle(hx, hy, 5 * density, ferrule);

        // 2. Draw Whipping Rope Curve (Physics Wave Propagation)
        ropePath.reset();
        ropePath.moveTo(hx, hy);

        float sx = hx;
        float sy = hy;
        float tx = targetPoint.x;
        float ty = targetPoint.y;

        float currentTipX;
        float currentTipY;

        if (progress < 0.55f) {
            // Phase 1: The whip uncurls and propels forward in a dynamic loop
            float pNorm = progress / 0.55f;
            currentTipX = sx + (tx - sx) * pNorm;
            currentTipY = sy + (ty - sy) * pNorm;

            // Traveling wave apex loop
            float waveApexX = sx + (tx - sx) * 0.5f - (1f - pNorm) * 120 * density;
            float waveApexY = sy + (ty - sy) * 0.3f - (float) Math.sin(pNorm * Math.PI) * 180 * density;

            float cp1X = sx + (waveApexX - sx) * 0.6f;
            float cp1Y = sy - 80 * density * (1f - pNorm);

            ropePath.cubicTo(cp1X, cp1Y, waveApexX, waveApexY, currentTipX, currentTipY);
            ropePaint.setStrokeWidth(6 * density);
            canvas.drawPath(ropePath, ropePaint);

            // Tip popper
            tipPaint.setStrokeWidth(3 * density);
            canvas.drawLine(currentTipX, currentTipY, currentTipX + 15 * density, currentTipY - 10 * density, tipPaint);

        } else {
            // Phase 2: Whip has struck target, vibrating wave decay
            float pAfter = (progress - 0.55f) / 0.45f;
            currentTipX = tx;
            currentTipY = ty;

            float waveOffset = (float) Math.sin(pAfter * Math.PI * 4) * (1f - pAfter) * 25 * density;

            float cp1X = sx + (tx - sx) * 0.35f + waveOffset * 0.6f;
            float cp1Y = sy + (ty - sy) * 0.25f - 40 * density * (1f - pAfter);

            float cp2X = sx + (tx - sx) * 0.7f - waveOffset;
            float cp2Y = sy + (ty - sy) * 0.65f + waveOffset;

            ropePath.cubicTo(cp1X, cp1Y, cp2X, cp2Y, currentTipX, currentTipY);
            ropePaint.setStrokeWidth(Math.max(2 * density, (5 - pAfter * 3) * density));
            canvas.drawPath(ropePath, ropePaint);

            // Red cracker at tip
            tipPaint.setStrokeWidth(2.5f * density);
            canvas.drawLine(tx, ty, tx + 18 * density * (1f - pAfter), ty + 12 * density * (1f - pAfter), tipPaint);

            // 3. Impact Flash & Sparks on Avatar
            if (progress >= 0.52f && progress <= 0.85f) {
                float sparkProg = (progress - 0.52f) / 0.33f;
                float sparkRadius = sparkProg * 45 * density;
                float sparkAlpha = (1f - sparkProg);

                sparkPaint.setAlpha((int) (sparkAlpha * 255));

                // Center impact flash
                Paint flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                flashPaint.setColor(Color.parseColor("#FEF08A"));
                flashPaint.setAlpha((int) (sparkAlpha * 200));
                canvas.drawCircle(tx, ty, (1f - sparkProg) * 22 * density, flashPaint);

                // Radiating burst sparks
                int numSparks = 8;
                for (int i = 0; i < numSparks; i++) {
                    double angle = (i * (2 * Math.PI / numSparks)) + (sparkProg * 0.5);
                    float px = tx + (float) Math.cos(angle) * sparkRadius;
                    float py = ty + (float) Math.sin(angle) * sparkRadius;
                    float pSize = Math.max(1f, (1f - sparkProg) * 4 * density);
                    canvas.drawCircle(px, py, pSize, sparkPaint);

                    // Spark line streaks
                    Paint streakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    streakPaint.setColor(Color.parseColor("#F59E0B"));
                    streakPaint.setStrokeWidth(2 * density);
                    streakPaint.setAlpha((int) (sparkAlpha * 220));
                    canvas.drawLine(
                            px - (float) Math.cos(angle) * 6 * density,
                            py - (float) Math.sin(angle) * 6 * density,
                            px, py, streakPaint
                    );
                }
            }
        }
    }
}