package com.realconnect.app;

import android.graphics.Color;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CallLogAdapter extends RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder> {

    public interface OnCallLogActionListener {
        void onCall(String phoneNumber, String contactName);
        void onLongClick(CallLogEntry entry);
    }

    private final List<CallLogEntry> callLogs = new ArrayList<>();
    private final OnCallLogActionListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());

    public CallLogAdapter(OnCallLogActionListener listener) {
        this.listener = listener;
    }

    public void setCallLogs(List<CallLogEntry> logs) {
        callLogs.clear();
        if (logs != null) {
            callLogs.addAll(logs);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CallLogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_call_log, parent, false);
        return new CallLogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CallLogViewHolder holder, int position) {
        CallLogEntry entry = callLogs.get(position);

        String displayName = (entry.getContactName() != null && !entry.getContactName().isEmpty())
                ? entry.getContactName() : entry.getPhoneNumber();
        holder.textName.setText(displayName != null ? displayName : "Unknown");

        // Format Date / Time
        String timeStr;
        if (DateUtils.isToday(entry.getTimestamp())) {
            timeStr = timeFormat.format(new Date(entry.getTimestamp()));
        } else {
            timeStr = dateFormat.format(new Date(entry.getTimestamp()));
        }

        // Format Duration
        String durationStr = "";
        if (entry.getDurationSeconds() > 0) {
            int mins = entry.getDurationSeconds() / 60;
            int secs = entry.getDurationSeconds() % 60;
            durationStr = String.format(Locale.getDefault(), " (%02d:%02d)", mins, secs);
        }

        // Call Type & Direction Icon
        if (entry.getCallType() == CallLogEntry.TYPE_INCOMING) {
            holder.imgDirection.setImageResource(R.drawable.ic_call_incoming);
            holder.imgDirection.setColorFilter(Color.parseColor("#22C55E"));
            holder.textTypeTime.setText("Incoming • " + timeStr + durationStr);
        } else if (entry.getCallType() == CallLogEntry.TYPE_OUTGOING) {
            holder.imgDirection.setImageResource(R.drawable.ic_call_outgoing);
            holder.imgDirection.setColorFilter(Color.parseColor("#0EA5E9"));
            holder.textTypeTime.setText("Outgoing • " + timeStr + durationStr);
        } else {
            holder.imgDirection.setImageResource(R.drawable.ic_call_missed);
            holder.imgDirection.setColorFilter(Color.parseColor("#EF4444"));
            holder.textTypeTime.setText("Missed • " + timeStr);
        }

        if (entry.isSpam()) {
            holder.textTypeTime.setText(holder.textTypeTime.getText() + " • ⚠️ Spam");
            holder.textName.setTextColor(Color.parseColor("#EF4444"));
        } else {
            holder.textName.setTextColor(Color.parseColor("#0F172A"));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCall(entry.getPhoneNumber(), entry.getContactName());
            }
        });

        holder.btnCall.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCall(entry.getPhoneNumber(), entry.getContactName());
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onLongClick(entry);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return callLogs.size();
    }

    static class CallLogViewHolder extends RecyclerView.ViewHolder {
        TextView textName, textTypeTime;
        ImageView imgDirection, btnCall;

        CallLogViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_log_name);
            textTypeTime = itemView.findViewById(R.id.text_log_type_time);
            imgDirection = itemView.findViewById(R.id.img_log_direction);
            btnCall = itemView.findViewById(R.id.image_log_call_action);
        }
    }
}