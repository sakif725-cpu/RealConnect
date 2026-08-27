package com.realconnect.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.webrtc.SessionDescription;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private String selfPhone;
    private String targetPhone;
    private String targetName;
    private String chatId;

    private MessageAdapter adapter;
    private RecyclerView recyclerView;
    private EditText editInput;
    private ChatRepository chatRepo;

    private SignalingClient signalingClient;
    private boolean isProcessingCall = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        SharedPreferences prefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE);
        selfPhone = prefs.getString("phone", "");

        targetPhone = getIntent().getStringExtra("CONTACT_PHONE");
        targetName = getIntent().getStringExtra("CONTACT_NAME");

        if (selfPhone.isEmpty()) {
            Toast.makeText(this, "Please set your phone number in Profile first.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (targetPhone == null || targetPhone.isEmpty()) {
            Toast.makeText(this, "Target contact phone missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        chatRepo = ChatRepository.getInstance(this);
        chatId = ChatRepository.getChatId(selfPhone, targetPhone);

        initViews();
        loadLocalHistory();
        setupRealtimeListener();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btn_chat_back);
        ImageButton btnCall = findViewById(R.id.btn_chat_call);
        TextView textName = findViewById(R.id.text_chat_header_name);
        TextView textPhone = findViewById(R.id.text_chat_header_phone);
        FloatingActionButton btnSend = findViewById(R.id.btn_send_message);
        editInput = findViewById(R.id.edit_message_input);
        recyclerView = findViewById(R.id.recycler_messages);

        textName.setText(targetName != null && !targetName.isEmpty() ? targetName : targetPhone);
        textPhone.setText(targetPhone);

        btnBack.setOnClickListener(v -> finish());

        btnCall.setOnClickListener(v -> {
            Intent callIntent = new Intent(ChatActivity.this, CallingActivity.class);
            callIntent.putExtra("CONTACT_NAME", targetName);
            callIntent.putExtra("CONTACT_PHONE", targetPhone);
            startActivity(callIntent);
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new MessageAdapter(selfPhone);
        recyclerView.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());

        editInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    private void loadLocalHistory() {
        List<Message> localMessages = chatRepo.getLocalMessages(chatId);
        adapter.setMessages(localMessages);
        if (adapter.getItemCount() > 0) {
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
        }
        chatRepo.markAsRead(chatId, selfPhone);
    }

    private void setupRealtimeListener() {
        chatRepo.startListeningForMessages(chatId, message -> runOnUiThread(() -> {
            adapter.addMessage(message);
            if (adapter.getItemCount() > 0) {
                recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
            }
            chatRepo.markAsRead(chatId, selfPhone);
        }));
    }

    private void sendMessage() {
        String text = editInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        editInput.setText("");

        Message sentMessage = chatRepo.sendMessage(selfPhone, targetPhone, text);
        adapter.addMessage(sentMessage);
        if (adapter.getItemCount() > 0) {
            recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isProcessingCall = false;
        new Handler(Looper.getMainLooper()).postDelayed(this::setupIncomingCallListener, 300);
    }

    @Override
    protected void onPause() {
        super.onPause();
        destroySignaling();
    }

    private void destroySignaling() {
        if (signalingClient != null) {
            signalingClient.destroy();
            signalingClient = null;
        }
    }

    private void setupIncomingCallListener() {
        if (selfPhone == null || selfPhone.isEmpty()) return;

        SignalingClient.clearNode(selfPhone);
        signalingClient = new SignalingClient(selfPhone, new SignalingClient.SignalingInterface() {
            @Override
            public void onRemoteOfferReceived(String callerPhone, SessionDescription description) {
                if (isProcessingCall) return;
                isProcessingCall = true;

                destroySignaling();

                AiService.checkSpam(callerPhone, isSpam -> {
                    Intent intent = new Intent(ChatActivity.this, CallingActivity.class);
                    intent.putExtra("IS_INCOMING", true);
                    intent.putExtra("IS_SPAM", isSpam);
                    intent.putExtra("REMOTE_OFFER", description.description);
                    intent.putExtra("CONTACT_PHONE", callerPhone);

                    String callerName = ContactRepository.getInstance(ChatActivity.this).findContactByNumber(callerPhone);
                    intent.putExtra("CONTACT_NAME", callerName != null ? callerName : callerPhone);

                    startActivity(intent);
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroySignaling();
        if (chatRepo != null && chatId != null) {
            chatRepo.stopListeningForMessages(chatId);
        }
    }
}