package com.realconnect.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AvatarShatterOverlayView extends View {

    public interface ShatterListener {
        void onReassembled();
    }

    private static class Shard {
        Bitmap bitmap;
        float x, y; // Current center in overlay coordinates
        float vx, vy; // Velocity
        float rotation; // Current angle in degrees
        float vRot; // Rotational velocity
        float scale = 1.0f;
        float alpha = 1.0f;
        int origW, origH;
    }

    private static class Particle {
        float x, y;
        float vx, vy;
        float radius;
        int color;
        float alpha = 1.0f;
        float life = 1.0f; // 1.0 down to 0
        float decay;
    }

    private final List<Shard> shards = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private Paint shardPaint;
    private Paint particlePaint;
    private Paint glowPaint;
    private ValueAnimator physicsAnimator;
    private float progress = 0f;
    private float density = 1f;
    private View originalTargetView;
    private ShatterListener listener;
    private PointF targetCenter = new PointF();

    public AvatarShatterOverlayView(Context context) {
        super(context);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        shardPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        particlePaint.setStyle(Paint.Style.FILL);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * Captures targetView, fractures it into physics shards, explodes & falls down, then reassembles.
     */
    public static void shatter(Activity activity, View targetView, ShatterListener shatterListener) {
        if (activity == null || targetView == null || targetView.getWidth() <= 0 || targetView.getHeight() <= 0) {
            if (shatterListener != null) shatterListener.onReassembled();
            return;
        }

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        AvatarShatterOverlayView overlay = new AvatarShatterOverlayView(activity);
        overlay.originalTargetView = targetView;
        overlay.listener = shatterListener;

        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        decor.addView(overlay, lp);

        overlay.startShatter(targetView, decor);
    }

    private void startShatter(View targetView, ViewGroup decor) {
        int w = targetView.getWidth();
        int h = targetView.getHeight();

        // 1. Capture targetView as Bitmap
        Bitmap snapshot = null;
        try {
            snapshot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(snapshot);
            targetView.draw(c);
        } catch (Exception e) {
            if (snapshot != null && !snapshot.isRecycled()) {
                snapshot.recycle();
            }
            if (decor != null) decor.removeView(this);
            if (listener != null) listener.onReassembled();
            return;
        }

        // 2. Get target view window location
        int[] loc = new int[2];
        targetView.getLocationInWindow(loc);
        float originX = loc[0];
        float originY = loc[1];
        targetCenter.set(originX + w / 2f, originY + h / 2f);

        // 3. Slice into 4x4 grid of shards
        int cols = 4;
        int rows = 4;
        int cellW = Math.max(1, w / cols);
        int cellH = Math.max(1, h / rows);

        shards.clear();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int sx = c * cellW;
                int sy = r * cellH;
                int sw = (c == cols - 1) ? (w - sx) : cellW;
                int sh = (r == rows - 1) ? (h - sy) : cellH;

                if (sw <= 0 || sh <= 0) continue;

                try {
                    Bitmap pieceBmp = Bitmap.createBitmap(snapshot, sx, sy, sw, sh);
                    Shard shard = new Shard();
                    shard.bitmap = pieceBmp;
                    shard.origW = sw;
                    shard.origH = sh;
                    shard.x = originX + sx + sw / 2f;
                    shard.y = originY + sy + sh / 2f;

                    // Calculate radial burst velocity away from target center
                    float dx = shard.x - targetCenter.x;
                    float dy = shard.y - targetCenter.y;
                    float dist = (float) Math.hypot(dx, dy);
                    if (dist == 0) {
                        dx = (random.nextFloat() - 0.5f) * 2f;
                        dy = (random.nextFloat() - 0.5f) * 2f;
                        dist = 1f;
                    }
                    float dirX = dx / dist;
                    float dirY = dy / dist;

                    // Explosive outward burst velocity with upward initial arc
                    float speed = (7f + random.nextFloat() * 14f) * density;
                    shard.vx = dirX * speed + (random.nextFloat() - 0.5f) * 8f * density;
                    shard.vy = dirY * speed - (8f + random.nextFloat() * 12f) * density;

                    // Rotational spin
                    shard.rotation = (random.nextFloat() - 0.5f) * 30f;
                    shard.vRot = (random.nextFloat() - 0.5f) * 26f;

                    shards.add(shard);
                } catch (Exception ignored) {}
            }
        }

        // 4. Generate bursting spark and debris particles
        particles.clear();
        int numParticles = 36;
        int[] sparkColors = {Color.parseColor("#F59E0B"), Color.parseColor("#EF4444"), Color.parseColor("#FEF08A"), Color.parseColor("#FFFFFF"), Color.parseColor("#8B5CF6")};
        for (int i = 0; i < numParticles; i++) {
            Particle p = new Particle();
            p.x = targetCenter.x + (random.nextFloat() - 0.5f) * w * 0.6f;
            p.y = targetCenter.y + (random.nextFloat() - 0.5f) * h * 0.6f;

            double angle = random.nextDouble() * 2 * Math.PI;
            float pSpeed = (4f + random.nextFloat() * 18f) * density;
            p.vx = (float) Math.cos(angle) * pSpeed;
            p.vy = (float) Math.sin(angle) * pSpeed - (4f + random.nextFloat() * 6f) * density;

            p.radius = (2.5f + random.nextFloat() * 4.5f) * density;
            p.color = sparkColors[random.nextInt(sparkColors.length)];
            p.decay = 0.015f + random.nextFloat() * 0.035f;
            particles.add(p);
        }

        // Clean up full snapshot now that shards hold their slices
        if (snapshot != null && !snapshot.isRecycled()) {
            snapshot.recycle();
        }

        // 5. Hide original avatar view while shattered
        targetView.setVisibility(View.INVISIBLE);

        // 6. Run physics animation loop
        final float gravity = 0.68f * density;
        physicsAnimator = ValueAnimator.ofFloat(0f, 1f);
        physicsAnimator.setDuration(2400); // 2.4s fall animation
        physicsAnimator.addUpdateListener(animation -> {
            progress = animation.getAnimatedFraction();

            // Update shard physics
            for (Shard s : shards) {
                s.x += s.vx;
                s.y += s.vy;
                s.vy += gravity; // Apply gravity downward
                s.rotation += s.vRot;
                s.vx *= 0.985f; // Air drag

                // Fade out towards the end of fall
                if (progress > 0.65f) {
                    s.alpha = Math.max(0f, 1f - (progress - 0.65f) / 0.35f);
                    s.scale = Math.max(0.2f, 1f - (progress - 0.65f) * 0.7f);
                }
            }

            // Update particle physics
            for (Particle p : particles) {
                p.x += p.vx;
                p.y += p.vy;
                p.vy += gravity * 0.35f;
                p.vx *= 0.95f;
                p.life -= p.decay;
                p.alpha = Math.max(0f, p.life);
            }

            invalidate();
        });

        physicsAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Free shard bitmaps
                for (Shard s : shards) {
                    if (s.bitmap != null && !s.bitmap.isRecycled()) {
                        s.bitmap.recycle();
                    }
                }
                shards.clear();
                particles.clear();

                // Reassemble Avatar with dynamic pop-in bounce animation
                playReassemblyAnimation(targetView, decor);
            }
        });

        physicsAnimator.start();
    }

    private void playReassemblyAnimation(View targetView, ViewGroup decor) {
        if (targetView == null) {
            if (decor != null) decor.removeView(this);
            if (listener != null) listener.onReassembled();
            return;
        }

        // Make visible and start from scaled down with glow
        targetView.setVisibility(View.VISIBLE);
        targetView.setScaleX(0.05f);
        targetView.setScaleY(0.05f);
        targetView.setAlpha(0f);
        targetView.setRotation(-45f);

        targetView.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .rotation(0f)
                .setDuration(480)
                .setInterpolator(new OvershootInterpolator(3.2f))
                .withEndAction(() -> {
                    if (decor != null) {
                        decor.removeView(AvatarShatterOverlayView.this);
                    }
                    if (listener != null) {
                        listener.onReassembled();
                    }
                })
                .start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 1. Draw Flash / Blast Wave at fracture center early on
        if (progress < 0.22f) {
            float blastProg = progress / 0.22f;
            float blastRadius = blastProg * 75 * density;
            int blastAlpha = (int) ((1f - blastProg) * 220);

            glowPaint.setColor(Color.parseColor("#FFFBEB"));
            glowPaint.setAlpha(blastAlpha);
            canvas.drawCircle(targetCenter.x, targetCenter.y, blastRadius, glowPaint);

            glowPaint.setColor(Color.parseColor("#F59E0B"));
            glowPaint.setAlpha((int) ((1f - blastProg) * 140));
            canvas.drawCircle(targetCenter.x, targetCenter.y, blastRadius * 1.35f, glowPaint);
        }

        // 2. Draw Particles (sparks and debris)
        for (Particle p : particles) {
            if (p.alpha <= 0f) continue;
            particlePaint.setColor(p.color);
            particlePaint.setAlpha((int) (p.alpha * 255));
            canvas.drawCircle(p.x, p.y, p.radius * p.life, particlePaint);
        }

        // 3. Draw Physics Shards
        for (Shard s : shards) {
            if (s.bitmap == null || s.bitmap.isRecycled() || s.alpha <= 0f) continue;

            shardPaint.setAlpha((int) (s.alpha * 255));

            canvas.save();
            canvas.translate(s.x, s.y);
            canvas.rotate(s.rotation);
            canvas.scale(s.scale, s.scale);

            // Draw bitmap centered
            float halfW = s.origW / 2f;
            float halfH = s.origH / 2f;
            canvas.drawBitmap(s.bitmap, -halfW, -halfH, shardPaint);

            // Draw cracked glass edge highlight
            Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            edgePaint.setColor(Color.parseColor("#FFFFFF"));
            edgePaint.setStyle(Paint.Style.STROKE);
            edgePaint.setStrokeWidth(1.2f * density);
            edgePaint.setAlpha((int) (s.alpha * 160));
            canvas.drawRect(-halfW, -halfH, halfW, halfH, edgePaint);

            canvas.restore();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (physicsAnimator != null && physicsAnimator.isRunning()) {
            physicsAnimator.cancel();
        }
        for (Shard s : shards) {
            if (s.bitmap != null && !s.bitmap.isRecycled()) {
                s.bitmap.recycle();
            }
        }
        shards.clear();
        particles.clear();
    }
}

