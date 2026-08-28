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
import android.view.animation.LinearInterpolator;

public class WhipOverlayView extends View {

    private float progress = 0f;
    private PointF targetPoint = new PointF();
    private Paint ropePaint;
    private Paint handlePaint;
    private Paint tipPaint;
    private Paint sparkPaint;
    private Paint slashPaint;
    private Paint flashPaint;
    private Path ropePath = new Path();

    public WhipOverlayView(Context context) {
        super(context);
        init();
    }

    private void init() {
        ropePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ropePaint.setColor(Color.parseColor("#92400E")); // Braided leather
        ropePaint.setStyle(Paint.Style.STROKE);
        ropePaint.setStrokeCap(Paint.Cap.ROUND);
        ropePaint.setStrokeJoin(Paint.Join.ROUND);

        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setColor(Color.parseColor("#451A03")); // Dark polished wood/leather
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeCap(Paint.Cap.ROUND);

        tipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tipPaint.setColor(Color.parseColor("#EF4444")); // Red cracker
        tipPaint.setStyle(Paint.Style.STROKE);
        tipPaint.setStrokeCap(Paint.Cap.ROUND);

        sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sparkPaint.setColor(Color.parseColor("#F59E0B"));
        sparkPaint.setStyle(Paint.Style.FILL);

        slashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        slashPaint.setStyle(Paint.Style.STROKE);
        slashPaint.setStrokeCap(Paint.Cap.ROUND);

        flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        flashPaint.setStyle(Paint.Style.FILL);
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

        overlay.startBeatingCombo(tx, ty, decor);
    }

    private void startBeatingCombo(float tx, float ty, ViewGroup parent) {
        this.targetPoint.set(tx, ty);

        post(() -> {
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(680); // 3 rapid-fire beating strikes in 680ms
            animator.setInterpolator(new LinearInterpolator());
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

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float density = getResources().getDisplayMetrics().density;
        float tx = targetPoint.x;
        float ty = targetPoint.y;

        // Strike 1: Progress 0.00 -> 0.33 (Fast Left Swing & Whack)
        if (progress < 0.33f) {
            float p = progress / 0.33f;
            PointF start = new PointF(w * 0.10f, h * 0.70f);
            drawSingleWhipStrike(canvas, start, tx, ty, p, -40f, density, Color.parseColor("#EF4444"), true);
        }
        // Strike 2: Progress 0.33 -> 0.66 (Fast Counter Right Whack)
        else if (progress < 0.66f) {
            float p = (progress - 0.33f) / 0.33f;
            PointF start = new PointF(w * 0.90f, h * 0.65f);
            drawSingleWhipStrike(canvas, start, tx, ty, p, -140f, density, Color.parseColor("#F59E0B"), false);
        }
        // Strike 3: Progress 0.66 -> 1.00 (Heavy Overhead Power Slam & Mega Shockwave)
        else {
            float p = (progress - 0.66f) / 0.34f;
            PointF start = new PointF(w * 0.45f, h * 0.05f);
            drawHeavyOverheadStrike(canvas, start, tx, ty, p, density);
        }
    }

    private void drawSingleWhipStrike(Canvas canvas, PointF start, float tx, float ty, float p, float angleDeg, float density, int slashColor, boolean isLeft) {
        float alpha = p < 0.85f ? 1.0f : (1.0f - (p - 0.85f) / 0.15f);
        int alphaInt = (int) (alpha * 255);

        ropePaint.setAlpha(alphaInt);
        handlePaint.setAlpha(alphaInt);
        tipPaint.setAlpha(alphaInt);
        sparkPaint.setAlpha(alphaInt);

        // 1. Draw Grip Handle
        float handleLen = 45 * density;
        float hRad = (float) Math.toRadians(angleDeg);
        float hx = start.x + (float) Math.cos(hRad) * handleLen;
        float hy = start.y + (float) Math.sin(hRad) * handleLen;

        handlePaint.setStrokeWidth(9 * density);
        canvas.drawLine(start.x, start.y, hx, hy, handlePaint);

        // Ferrule ring
        Paint ferrule = new Paint(Paint.ANTI_ALIAS_FLAG);
        ferrule.setColor(Color.parseColor("#FBBF24"));
        ferrule.setAlpha(alphaInt);
        canvas.drawCircle(hx, hy, 4.5f * density, ferrule);

        // 2. Draw Whip Lash Path
        ropePath.reset();
        ropePath.moveTo(hx, hy);

        float hitThreshold = 0.50f;
        if (p < hitThreshold) {
            float pNorm = p / hitThreshold;
            float tipX = hx + (tx - hx) * pNorm;
            float tipY = hy + (ty - hy) * pNorm;

            float curveSide = isLeft ? -1 : 1;
            float waveApexX = hx + (tx - hx) * 0.5f + (curveSide * (1f - pNorm) * 110 * density);
            float waveApexY = hy + (ty - hy) * 0.3f - (float) Math.sin(pNorm * Math.PI) * 140 * density;

            ropePath.cubicTo(hx + (waveApexX - hx) * 0.5f, hy - 60 * density, waveApexX, waveApexY, tipX, tipY);
            ropePaint.setStrokeWidth(5 * density);
            canvas.drawPath(ropePath, ropePaint);

            tipPaint.setStrokeWidth(3 * density);
            canvas.drawLine(tipX, tipY, tipX + (isLeft ? 14 : -14) * density, tipY - 8 * density, tipPaint);
        } else {
            // Hit & Recoil
            float pAfter = (p - hitThreshold) / (1f - hitThreshold);
            float waveOffset = (float) Math.sin(pAfter * Math.PI * 4) * (1f - pAfter) * 18 * density;

            ropePath.cubicTo(
                    hx + (tx - hx) * 0.35f + waveOffset,
                    hy + (ty - hy) * 0.25f - 30 * density * (1f - pAfter),
                    hx + (tx - hx) * 0.7f - waveOffset,
                    hy + (ty - hy) * 0.65f + waveOffset,
                    tx, ty
            );
            ropePaint.setStrokeWidth(Math.max(2 * density, (4.5f - pAfter * 2.5f) * density));
            canvas.drawPath(ropePath, ropePaint);

            // Energy Slash Line across avatar
            slashPaint.setColor(slashColor);
            slashPaint.setStrokeWidth(4 * density * (1f - pAfter));
            slashPaint.setAlpha((int) ((1f - pAfter) * 255));
            float slashSpan = 35 * density;
            float slashAngle = isLeft ? 35 : -35;
            float sRad = (float) Math.toRadians(slashAngle);
            canvas.drawLine(
                    tx - (float) Math.cos(sRad) * slashSpan,
                    ty - (float) Math.sin(sRad) * slashSpan,
                    tx + (float) Math.cos(sRad) * slashSpan,
                    ty + (float) Math.sin(sRad) * slashSpan,
                    slashPaint
            );

            // Sparks
            drawSparks(canvas, tx, ty, pAfter, 8, 38 * density, density);
        }
    }

    private void drawHeavyOverheadStrike(Canvas canvas, PointF start, float tx, float ty, float p, float density) {
        float alpha = p < 0.80f ? 1.0f : (1.0f - (p - 0.80f) / 0.20f);
        int alphaInt = (int) (alpha * 255);

        ropePaint.setAlpha(alphaInt);
        handlePaint.setAlpha(alphaInt);
        sparkPaint.setAlpha(alphaInt);

        float handleLen = 50 * density;
        float hx = start.x;
        float hy = start.y + handleLen;

        handlePaint.setStrokeWidth(11 * density);
        canvas.drawLine(start.x, start.y, hx, hy, handlePaint);

        ropePath.reset();
        ropePath.moveTo(hx, hy);

        float hitThreshold = 0.45f;
        if (p < hitThreshold) {
            float pNorm = p / hitThreshold;
            float tipX = hx + (tx - hx) * pNorm;
            float tipY = hy + (ty - hy) * pNorm;

            ropePath.cubicTo(hx + 80 * density * (1f - pNorm), hy + (tipY - hy) * 0.4f, hx - 60 * density * (1f - pNorm), hy + (tipY - hy) * 0.7f, tipX, tipY);
            ropePaint.setStrokeWidth(7 * density);
            canvas.drawPath(ropePath, ropePaint);
        } else {
            float pAfter = (p - hitThreshold) / (1f - hitThreshold);
            float recoilWave = (float) Math.sin(pAfter * Math.PI * 5) * (1f - pAfter) * 22 * density;

            ropePath.cubicTo(hx + recoilWave, hy + (ty - hy) * 0.3f, hx - recoilWave, hy + (ty - hy) * 0.7f, tx, ty);
            ropePaint.setStrokeWidth(Math.max(2 * density, (6 - pAfter * 4) * density));
            canvas.drawPath(ropePath, ropePaint);

            // Double Shockwave Rings on Heavy Slam
            flashPaint.setColor(Color.parseColor("#FEF08A"));
            flashPaint.setAlpha((int) ((1f - pAfter) * 220));
            canvas.drawCircle(tx, ty, (1f - pAfter) * 30 * density, flashPaint);

            Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setColor(Color.parseColor("#EF4444"));
            ringPaint.setStrokeWidth(3 * density);
            ringPaint.setAlpha((int) ((1f - pAfter) * 255));
            canvas.drawCircle(tx, ty, pAfter * 55 * density, ringPaint);

            // 14 Explosive Sparks
            drawSparks(canvas, tx, ty, pAfter, 14, 60 * density, density);
        }
    }

    private void drawSparks(Canvas canvas, float tx, float ty, float pAfter, int numSparks, float maxRadius, float density) {
        float sparkRadius = pAfter * maxRadius;
        float sparkAlpha = (1f - pAfter);
        sparkPaint.setAlpha((int) (sparkAlpha * 255));

        for (int i = 0; i < numSparks; i++) {
            double angle = (i * (2 * Math.PI / numSparks)) + (pAfter * 0.6);
            float px = tx + (float) Math.cos(angle) * sparkRadius;
            float py = ty + (float) Math.sin(angle) * sparkRadius;
            canvas.drawCircle(px, py, Math.max(1.5f, (1f - pAfter) * 4.5f * density), sparkPaint);
        }
    }
}