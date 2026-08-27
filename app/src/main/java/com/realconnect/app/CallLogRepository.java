package com.realconnect.app;

import android.content.Context;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CallLogRepository {

    public interface OnCallLogsChangedListener {
        void onCallLogsChanged();
    }

    private static CallLogRepository instance;
    private final CallLogDao callLogDao;
    private final List<OnCallLogsChangedListener> listeners = new CopyOnWriteArrayList<>();

    private CallLogRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        callLogDao = db.callLogDao();
    }

    public static synchronized CallLogRepository getInstance(Context context) {
        if (instance == null) {
            instance = new CallLogRepository(context);
        }
        return instance;
    }

    public void addListener(OnCallLogsChangedListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(OnCallLogsChangedListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyListeners() {
        for (OnCallLogsChangedListener listener : listeners) {
            try {
                listener.onCallLogsChanged();
            } catch (Exception ignored) {}
        }
    }

    public List<CallLogEntry> getCallLogs() {
        return callLogDao.getAllCallLogs();
    }

    public void addCallLog(CallLogEntry entry) {
        callLogDao.insert(entry);
        notifyListeners();
    }

    public void deleteCallLog(int id) {
        callLogDao.deleteById(id);
        notifyListeners();
    }

    public void clearCallLogs() {
        callLogDao.clearAll();
        notifyListeners();
    }
}