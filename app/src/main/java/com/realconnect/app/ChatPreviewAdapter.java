package com.realconnect.app;

import android.content.Context;
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

public class ChatPreviewAdapter extends RecyclerView.Adapter<ChatPreviewAdapter.ChatPreviewViewHolder> {

    public interface OnChatSelectedListener {
        void onChatSelected(String contactName, String contactPhone);
    }

    private final Context context;
    private final String selfPhone;
    private final OnChatSelectedListener listener;
    private final List<Message> chatList = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());

    public ChatPreviewAdapter(Context context, String selfPhone, OnChatSelectedListener listener) {
        this.context = context;
        this.selfPhone = ChatRepository.cleanPhone(selfPhone);
        this.listener = listener;
    }

    public void setChats(List<Message> chats) {
        chatList.clear();
        if (chats != null) {
            chatList.addAll(chats);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatPreviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_preview, parent, false);
        return new ChatPreviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatPreviewViewHolder holder, int position) {
        Message message = chatList.get(position);

        String cleanSender = ChatRepository.cleanPhone(message.getSenderPhone());
        String targetPhone = cleanSender.equals(selfPhone) ? message.getReceiverPhone() : message.getSenderPhone();

        String contactName = ContactRepository.getInstance(context).findContactByNumber(targetPhone);
        String displayName = (contactName != null && !contactName.trim().isEmpty()) ? contactName : targetPhone;

        holder.textName.setText(displayName != null && !displayName.isEmpty() ? displayName : "Unknown");
        holder.textLastMessage.setText(message.getText());

        long diffDays = (System.currentTimeMillis() - message.getTimestamp()) / (1000 * 60 * 60 * 24);
        if (diffDays == 0) {
            holder.textTime.setText(timeFormat.format(new Date(message.getTimestamp())));
        } else {
            holder.textTime.setText(dateFormat.format(new Date(message.getTimestamp())));
        }

        holder.badgeUnread.setVisibility(!message.isRead() && !cleanSender.equals(selfPhone) ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChatSelected(displayName, targetPhone);
            }
        });
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    static class ChatPreviewViewHolder extends RecyclerView.ViewHolder {
        TextView textName, textLastMessage, textTime;
        View badgeUnread;

        ChatPreviewViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.text_chat_name);
            textLastMessage = itemView.findViewById(R.id.text_chat_last_message);
            textTime = itemView.findViewById(R.id.text_chat_time);
            badgeUnread = itemView.findViewById(R.id.badge_unread);
        }
    }
}