package com.realconnect.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import org.webrtc.SessionDescription;

public class CallService extends Service {

    private static final String TAG = "CallService";
    public static final String CHANNEL_SERVICE = "realconnect_service_channel_v1";
    public static final String CHANNEL_CALLS = "realconnect_incoming_calls_channel_v3";
    public static final String CHANNEL_MESSAGES = "realconnect_messages_channel_v1";

    public static final int SERVICE_NOTIFICATION_ID = 1001;
    public static final int INCOMING_CALL_NOTIFICATION_ID = 2002;
    public static final int MESSAGE_NOTIFICATION_BASE_ID = 3000;

    private static CallService instance;
    private SignalingClient signalingClient;
    private boolean isListeningPaused = false;
    private boolean isProcessingCall = false;

    public static void start(Context context) {
        Intent intent = new Intent(context, CallService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, CallService.class);
        context.stopService(intent);
    }

    public static void pauseListening() {
        if (instance != null) {
            instance.isListeningPaused = true;
            instance.destroySignaling();
        }
    }

    public static void resumeListening() {
        if (instance != null) {
            instance.isListeningPaused = false;
            instance.isProcessingCall = false;
            instance.setupSignaling();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannels();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(SERVICE_NOTIFICATION_ID, createServiceNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } catch (Exception e) {
                startForeground(SERVICE_NOTIFICATION_ID, createServiceNotification());
            }
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, createServiceNotification());
        }
        setupSignaling();
        setupMessageInbox();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        instance = this;
        if (!isListeningPaused) {
            setupSignaling();
        }
        setupMessageInbox();
        return START_STICKY;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            // Background Service Channel
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_SERVICE,
                    "RealConnect Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Keeps RealConnect active for incoming calls & messages");
            serviceChannel.setShowBadge(false);
            manager.createNotificationChannel(serviceChannel);

            // High-Priority Incoming Call Channel
            NotificationChannel callChannel = new NotificationChannel(
                    CHANNEL_CALLS,
                    "Incoming Calls",
                    NotificationManager.IMPORTANCE_HIGH
            );
            callChannel.setDescription("Full-screen incoming calls and notifications");
            callChannel.enableLights(true);
            callChannel.enableVibration(true);
            callChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000});

            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build();
            callChannel.setSound(ringtoneUri, audioAttributes);
            callChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(callChannel);

            // Messages Channel
            NotificationChannel messageChannel = new NotificationChannel(
                    CHANNEL_MESSAGES,
                    "Incoming Messages",
                    NotificationManager.IMPORTANCE_HIGH
            );
            messageChannel.setDescription("Notifications for incoming messages");
            messageChannel.enableLights(true);
            messageChannel.enableVibration(true);
            manager.createNotificationChannel(messageChannel);
        }
    }

    private Notification createServiceNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        return new NotificationCompat.Builder(this, CHANNEL_SERVICE)
                .setContentTitle("RealConnect Active")
                .setContentText("Ready for secure calls & messages")
                .setSmallIcon(R.drawable.ic_call)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .build();
    }

    private void setupSignaling() {
        SharedPreferences prefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE);
        String selfPhone = prefs.getString("phone", null);

        if (selfPhone == null || selfPhone.trim().isEmpty()) {
            return;
        }

        if (signalingClient != null) {
            return; // Already listening
        }

        SignalingClient.clearNode(selfPhone);
        signalingClient = new SignalingClient(selfPhone, new SignalingClient.SignalingInterface() {
            @Override
            public void onRemoteOfferReceived(String callerPhone, SessionDescription description) {
                if (isListeningPaused || isProcessingCall) return;
                isProcessingCall = true;

                destroySignaling();

                AiService.checkSpam(callerPhone, isSpam -> {
                    String callerName = ContactRepository.getInstance(CallService.this).findContactByNumber(callerPhone);
                    String displayName = (callerName != null && !callerName.isEmpty()) ? callerName : callerPhone;

                    showIncomingCall(callerPhone, displayName, isSpam, description.description);
                });
            }
        });
    }

    private void setupMessageInbox() {
        SharedPreferences prefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE);
        String selfPhone = prefs.getString("phone", null);

        if (selfPhone != null && !selfPhone.trim().isEmpty()) {
            ChatRepository.getInstance(this).startListeningToUserInbox(selfPhone, this::showIncomingMessageNotification);
        }
    }

    private void showIncomingCall(String callerPhone, String callerName, boolean isSpam, String sdpOffer) {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                PowerManager.WakeLock wl = pm.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                        "RealConnect:IncomingCallWakeLock"
                );
                wl.acquire(10000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring WakeLock", e);
        }

        Intent callIntent = new Intent(this, CallingActivity.class);
        callIntent.putExtra("IS_INCOMING", true);
        callIntent.putExtra("IS_SPAM", isSpam);
        callIntent.putExtra("REMOTE_OFFER", sdpOffer);
        callIntent.putExtra("CONTACT_PHONE", callerPhone);
        callIntent.putExtra("CONTACT_NAME", callerName);
        callIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_CALLS)
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle(isSpam ? "⚠️ Possible Spam Call" : "Incoming RealConnect Call")
                .setContentText(callerName)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setSound(ringtoneUri)
                .setVibrate(new long[]{0, 1000, 500, 1000})
                .setAutoCancel(true)
                .setOngoing(true)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .addAction(R.drawable.ic_call, "Answer", fullScreenPendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, builder.build());
        }

        try {
            startActivity(callIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error starting CallingActivity from service", e);
        }
    }

    private void showIncomingMessageNotification(Message message) {
        String senderPhone = message.getSenderPhone();
        String contactName = ContactRepository.getInstance(this).findContactByNumber(senderPhone);
        String displayName = (contactName != null && !contactName.isEmpty()) ? contactName : senderPhone;

        Intent chatIntent = new Intent(this, ChatActivity.class);
        chatIntent.putExtra("CONTACT_PHONE", senderPhone);
        chatIntent.putExtra("CONTACT_NAME", displayName);
        chatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (senderPhone != null ? senderPhone.hashCode() : 0),
                chatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_chat)
                .setContentTitle(displayName)
                .setContentText(message.getText())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(MESSAGE_NOTIFICATION_BASE_ID + (senderPhone != null ? Math.abs(senderPhone.hashCode() % 1000) : 0), builder.build());
        }
    }

    private void destroySignaling() {
        if (signalingClient != null) {
            signalingClient.destroy();
            signalingClient = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroySignaling();
        instance = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}