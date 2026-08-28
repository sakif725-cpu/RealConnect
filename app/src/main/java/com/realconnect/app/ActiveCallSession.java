package com.realconnect.app;

import java.util.ArrayList;
import java.util.List;

public class ActiveCallSession {

    public interface CallSessionListener {
        void onCallStateChanged(boolean isActive);
        void onTimerTick(String formattedTime);
        void onMuteChanged(boolean isMuted);
    }

    private static ActiveCallSession instance;

    private boolean isActive = false;
    private String callerName = "";
    private String callerPhone = "";
    private String formattedDuration = "00:00";
    private boolean isMuted = false;
    private CallingActivity currentActivity;

    private final List<CallSessionListener> listeners = new ArrayList<>();

    public static synchronized ActiveCallSession getInstance() {
        if (instance == null) {
            instance = new ActiveCallSession();
        }
        return instance;
    }

    public synchronized void startSession(CallingActivity activity, String name, String phone) {
        this.currentActivity = activity;
        this.callerName = (name != null && !name.trim().isEmpty()) ? name : phone;
        this.callerPhone = phone != null ? phone : "";
        this.isActive = true;
        this.formattedDuration = "00:00";
        this.isMuted = false;
        notifyStateChanged();
    }

    public synchronized void updateDuration(String time) {
        this.formattedDuration = time != null ? time : "00:00";
        for (CallSessionListener l : new ArrayList<>(listeners)) {
            if (l != null) l.onTimerTick(this.formattedDuration);
        }
    }

    public synchronized void updateCallerName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            this.callerName = name;
            notifyStateChanged();
        }
    }

    public synchronized void updateMute(boolean muted) {
        this.isMuted = muted;
        for (CallSessionListener l : new ArrayList<>(listeners)) {
            if (l != null) l.onMuteChanged(muted);
        }
    }

    public synchronized void endSession() {
        this.isActive = false;
        this.currentActivity = null;
        notifyStateChanged();
    }

    public synchronized void addListener(CallSessionListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            listener.onCallStateChanged(isActive);
            listener.onTimerTick(formattedDuration);
            listener.onMuteChanged(isMuted);
        }
    }

    public synchronized void removeListener(CallSessionListener listener) {
        listeners.remove(listener);
    }

    private synchronized void notifyStateChanged() {
        for (CallSessionListener l : new ArrayList<>(listeners)) {
            if (l != null) l.onCallStateChanged(isActive);
        }
    }

    public boolean isActive() { return isActive; }
    public String getCallerName() { return callerName; }
    public String getCallerPhone() { return callerPhone; }
    public String getFormattedDuration() { return formattedDuration; }
    public boolean isMuted() { return isMuted; }

    public void requestEndCall() {
        if (currentActivity != null && !currentActivity.isFinishing()) {
            currentActivity.runOnUiThread(() -> currentActivity.endCallFromBanner());
        }
    }

    public void requestToggleMute() {
        if (currentActivity != null && !currentActivity.isFinishing()) {
            currentActivity.runOnUiThread(() -> currentActivity.toggleMuteFromBanner());
        }
    }
}