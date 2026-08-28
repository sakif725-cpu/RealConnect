package com.realconnect.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AvatarHelper {

    private static final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
    private static final int cacheSize = Math.max(1024, maxMemory / 8);
    private static final LruCache<String, Bitmap> avatarCache = new LruCache<String, Bitmap>(cacheSize) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount() / 1024;
        }
    };

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void loadAvatar(Context context, ImageView imageView, @Nullable String phoneNumber, @Nullable String displayName) {
        if (imageView == null) return;

        String name = (displayName != null && !displayName.trim().isEmpty())
                ? displayName
                : (phoneNumber != null && !phoneNumber.trim().isEmpty() ? phoneNumber : "?");

        int avatarSize = 120;
        Bitmap initialAvatar = ImageUtils.createAvatarWithInitial(
                name,
                avatarSize,
                Color.parseColor("#E2E8F0"),
                Color.parseColor("#0F172A")
        );

        imageView.setPadding(0, 0, 0, 0);
        imageView.setImageTintList(null);
        imageView.setColorFilter(null);
        imageView.setBackground(null);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        String cleanPhone = ChatRepository.cleanPhone(phoneNumber);
        if (cleanPhone.isEmpty()) {
            imageView.setImageBitmap(initialAvatar);
            return;
        }

        // Check in-memory LRU cache
        Bitmap cached = avatarCache.get(cleanPhone);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        // Set initial avatar while loading
        imageView.setImageBitmap(initialAvatar);
        imageView.setTag(cleanPhone);

        // Fetch from Firebase asynchronously
        FirebaseDatabase.getInstance().getReference("users")
                .child(cleanPhone)
                .child("profileImageBase64")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        try {
                            String base64 = snapshot.getValue(String.class);
                            if (base64 != null && !base64.trim().isEmpty()) {
                                Bitmap photo = ImageUtils.base64ToBitmap(base64);
                                if (photo != null) {
                                    avatarCache.put(cleanPhone, photo);
                                    mainHandler.post(() -> {
                                        Object tag = imageView.getTag();
                                        if (cleanPhone.equals(tag)) {
                                            imageView.setImageBitmap(photo);
                                        }
                                    });
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    public static void clearCacheForPhone(String phone) {
        String cleanPhone = ChatRepository.cleanPhone(phone);
        if (!cleanPhone.isEmpty()) {
            avatarCache.remove(cleanPhone);
        }
    }
}