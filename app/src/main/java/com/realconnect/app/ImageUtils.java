package com.realconnect.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ImageUtils {

    private static final String TAG = "ImageUtils";

    public static String uriToBase64(Context context, Uri uri, int maxDim) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            Bitmap original = BitmapFactory.decodeStream(is);
            if (original == null) return null;

            int width = original.getWidth();
            int height = original.getHeight();
            float ratio = (float) width / height;

            int newWidth = maxDim;
            int newHeight = maxDim;
            if (ratio > 1) {
                newHeight = (int) (maxDim / ratio);
            } else {
                newWidth = (int) (maxDim * ratio);
            }

            Bitmap scaled = Bitmap.createScaledBitmap(original, Math.max(1, newWidth), Math.max(1, newHeight), true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos);
            byte[] bytes = baos.toByteArray();
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Error converting URI to base64", e);
            return null;
        }
    }

    public static Bitmap base64ToBitmap(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            Log.e(TAG, "Error decoding base64 to bitmap", e);
            return null;
        }
    }

    public static Bitmap createAvatarWithInitial(String text, int size, int backgroundColor, int textColor) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(backgroundColor);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint);

        String initial = "?";
        if (text != null && !text.trim().isEmpty()) {
            initial = text.trim().substring(0, 1).toUpperCase();
        }

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(textColor);
        textPaint.setTextSize(size * 0.45f);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float y = (size / 2f) - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(initial, size / 2f, y, textPaint);

        return bitmap;
    }
}