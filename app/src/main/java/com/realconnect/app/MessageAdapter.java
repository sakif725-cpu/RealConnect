package com.realconnect.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    public interface OnMessageLongClickListener {
        void onMessageLongClick(Message message);
    }

    private final String selfPhone;
    private final OnMessageLongClickListener longClickListener;
    private final List<Message> messageList = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    public MessageAdapter(String selfPhone, OnMessageLongClickListener longClickListener) {
        this.selfPhone = ChatRepository.cleanPhone(selfPhone);
        this.longClickListener = longClickListener;
    }

    public MessageAdapter(String selfPhone) {
        this(selfPhone, null);
    }

    public void setMessages(List<Message> messages) {
        messageList.clear();
        if (messages != null) {
            messageList.addAll(messages);
        }
        notifyDataSetChanged();
    }

    public void addMessage(Message message) {
        if (message == null) return;
        for (int i = 0; i < messageList.size(); i++) {
            if (messageList.get(i).getId().equals(message.getId())) {
                return; // Already exists
            }
        }
        messageList.add(message);
        notifyItemInserted(messageList.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messageList.get(position);
        String sender = ChatRepository.cleanPhone(message.getSenderPhone());
        if (sender.equals(selfPhone)) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SENT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_sent, parent, false);
            return new SentMessageViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received, parent, false);
            return new ReceivedMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messageList.get(position);
        String timeStr = timeFormat.format(new Date(message.getTimestamp()));

        if (holder instanceof SentMessageViewHolder) {
            SentMessageViewHolder sentHolder = (SentMessageViewHolder) holder;
            sentHolder.textBody.setText(message.getText());
            sentHolder.textTime.setText(timeStr);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ReceivedMessageViewHolder receivedHolder = (ReceivedMessageViewHolder) holder;
            receivedHolder.textBody.setText(message.getText());
            receivedHolder.textTime.setText(timeStr);
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onMessageLongClick(message);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView textBody, textTime;
        SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textBody = itemView.findViewById(R.id.text_message_body);
            textTime = itemView.findViewById(R.id.text_message_time);
        }
    }

    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView textBody, textTime;
        ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textBody = itemView.findViewById(R.id.text_message_body);
            textTime = itemView.findViewById(R.id.text_message_time);
        }
    }
}