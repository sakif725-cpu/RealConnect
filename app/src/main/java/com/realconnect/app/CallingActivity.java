package com.realconnect.app;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import org.webrtc.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CallingActivity extends AppCompatActivity {

    private static final String TAG = "WebRTCCall";
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private TextView textCallTimer;
    private TextView textAiStatus;
    private TextView textSpamWarning;
    private View controlsContainer;
    private FloatingActionButton btnAcceptCall;
    private Ringtone ringtone;
    
    private int seconds = 0;
    private boolean running = false;
    private boolean isConnected = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<IceCandidate> pendingIceCandidates = new ArrayList<>();

    // WebRTC components
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private AudioManager audioManager;
    private SignalingClient signalingClient;
    
    private String selfPhone;
    private String targetPhone;
    private boolean isIncoming;
    private LiveRiskResult lastRiskResult;
    private LiveCallAiProcessor aiProcessor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            );
        }

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(CallService.INCOMING_CALL_NOTIFICATION_ID);
        }

        CallService.pauseListening();

        setContentView(R.layout.activity_calling);

        SharedPreferences prefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE);
        selfPhone = prefs.getString("phone", "");
        targetPhone = getIntent().getStringExtra("CONTACT_PHONE");
        isIncoming = getIntent().getBooleanExtra("IS_INCOMING", false);

        if (selfPhone.isEmpty()) {
            Toast.makeText(this, "Profile number missing!", Toast.LENGTH_SHORT).show();
            CallService.resumeListening();
            finish();
            return;
        }

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        textCallTimer = findViewById(R.id.text_call_timer);
        textAiStatus = findViewById(R.id.text_ai_status);
        textSpamWarning = findViewById(R.id.text_spam_warning);
        TextView textCallerName = findViewById(R.id.text_caller_name);
        FloatingActionButton btnEndCall = findViewById(R.id.btn_end_call);
        btnAcceptCall = findViewById(R.id.btn_accept_call);
        controlsContainer = findViewById(R.id.controls_container);

        String name = getIntent().getStringExtra("CONTACT_NAME");
        ImageView imgAvatarCalling = findViewById(R.id.img_avatar_calling);
        loadCallerAvatar(imgAvatarCalling, targetPhone, name);

        textCallerName.setText(name != null && !name.isEmpty() ? name : (targetPhone != null ? targetPhone : "Unknown"));

        btnEndCall.setOnClickListener(v -> endCall());
        btnAcceptCall.setOnClickListener(v -> acceptCall());

        View badgeAiStatus = findViewById(R.id.ai_status_badge);
        if (badgeAiStatus != null) {
            badgeAiStatus.setOnClickListener(v -> performVoiceAiAnalysis(true));
        }

        // Default UI State: Controls visible for caller, hidden for receiver
        if (isIncoming) {
            btnAcceptCall.setVisibility(View.VISIBLE);
            controlsContainer.setVisibility(View.GONE);
            textCallTimer.setText("Incoming...");
            startRinging();
            
            if (getIntent().getBooleanExtra("IS_SPAM", false)) {
                showSpamWarning();
            }
        } else {
            btnAcceptCall.setVisibility(View.GONE);
            controlsContainer.setVisibility(View.VISIBLE);
            textCallTimer.setText("Calling...");
            textAiStatus.setText("AI GUARD: ACTIVE");
            startContinuousAiListening();
            
            if (checkPermissions()) {
                startCallFlow();
            } else {
                requestPermissions();
            }
        }

        View btnMinimize = findViewById(R.id.btn_minimize_call);
        if (btnMinimize != null) {
            btnMinimize.setOnClickListener(v -> minimizeCall());
        }

        ActiveCallSession.getInstance().startSession(this, getIntent().getStringExtra("CONTACT_NAME"), targetPhone);

        setupActions();
        setupSignaling();
    }

    private void loadCallerAvatar(ImageView imgAvatar, String phone, String name) {
        if (imgAvatar == null) return;

        String displayName = (name != null && !name.trim().isEmpty()) ? name : (phone != null ? phone : "?");
        String cleanTarget = ChatRepository.cleanPhone(phone);
        String cleanSelf = ChatRepository.cleanPhone(selfPhone);

        // 1. Initial placeholder with clean background and initial letter
        Bitmap initialAvatar = ImageUtils.createAvatarWithInitial(displayName, 280, Color.parseColor("#1E293B"), Color.WHITE);
        imgAvatar.setPadding(0, 0, 0, 0);
        imgAvatar.setImageTintList(null);
        imgAvatar.setColorFilter(null);
        imgAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imgAvatar.setImageBitmap(initialAvatar);

        // 2. If self-call or local profile photo exists, display immediately
        if (cleanTarget.equals(cleanSelf)) {
            SharedPreferences prefs = getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE);
            String imageUriStr = prefs.getString("image_uri", null);
            if (imageUriStr != null && !imageUriStr.isEmpty()) {
                try {
                    Uri uri = Uri.parse(imageUriStr);
                    imgAvatar.setImageURI(uri);
                    return;
                } catch (Exception ignored) {}
            }
        }

        // 3. Fetch remote profile picture from Firebase Realtime Database
        if (!cleanTarget.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("users")
                    .child(cleanTarget)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            try {
                                if (isFinishing() || isDestroyed()) return;

                                String base64 = snapshot.child("profileImageBase64").getValue(String.class);
                                String remoteName = snapshot.child("name").getValue(String.class);

                                runOnUiThread(() -> {
                                    if (remoteName != null && !remoteName.trim().isEmpty() && (name == null || name.isEmpty() || name.equals(phone))) {
                                        TextView textCallerName = findViewById(R.id.text_caller_name);
                                        if (textCallerName != null) {
                                            textCallerName.setText(remoteName);
                                        }
                                    }

                                    if (base64 != null && !base64.trim().isEmpty()) {
                                        Bitmap photo = ImageUtils.base64ToBitmap(base64);
                                        if (photo != null) {
                                            imgAvatar.setPadding(0, 0, 0, 0);
                                            imgAvatar.setImageTintList(null);
                                            imgAvatar.setColorFilter(null);
                                            imgAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                            imgAvatar.setImageBitmap(photo);
                                        }
                                    }
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Error rendering caller avatar", e);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });
        }
    }

    private void showSpamWarning() {
        textSpamWarning.setVisibility(View.VISIBLE);
        textAiStatus.setText("AI: SPAM DETECTED");
        textAiStatus.setTextColor(Color.parseColor("#EF4444"));
    }

    private void onCallConnected() {
        if (isConnected) return;
        isConnected = true;
        runOnUiThread(() -> {
            stopRinging();
            textCallTimer.setText("00:00");
            controlsContainer.setVisibility(View.VISIBLE);
            running = true;
            runTimer();
            startContinuousAiListening();
        });
    }

    private void startContinuousAiListening() {
        if (aiProcessor != null) {
            aiProcessor.stop();
        }

        boolean isSpamPreFlagged = getIntent().getBooleanExtra("IS_SPAM", false);
        String callerName = getIntent().getStringExtra("CONTACT_NAME");

        aiProcessor = new LiveCallAiProcessor(this, targetPhone, callerName, isSpamPreFlagged, new LiveCallAiProcessor.AiScanListener() {
            @Override
            public void onProgressTick(int secondsElapsed, String statusSummary) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    textAiStatus.setText(statusSummary);
                    if (lastRiskResult != null && lastRiskResult.getLevel() == LiveRiskResult.Level.HIGH) {
                        textAiStatus.setTextColor(Color.parseColor("#EF4444"));
                    } else if (lastRiskResult != null && lastRiskResult.getLevel() == LiveRiskResult.Level.MEDIUM) {
                        textAiStatus.setTextColor(Color.parseColor("#EAB308"));
                    } else {
                        textAiStatus.setTextColor(Color.parseColor("#22C55E"));
                    }
                });
            }

            @Override
            public void onRiskUpdated(LiveRiskResult result) {
                runOnUiThread(() -> {
                    lastRiskResult = result;
                    if (isFinishing() || isDestroyed()) return;

                    if (result.getLevel() == LiveRiskResult.Level.HIGH) {
                        textAiStatus.setText("AI: HIGH RISK (" + result.getRiskScore() + "%)");
                        textAiStatus.setTextColor(Color.parseColor("#EF4444"));
                        textSpamWarning.setVisibility(View.VISIBLE);
                        textSpamWarning.setText("⚠ " + result.getSummary().toUpperCase());
                    } else if (result.getLevel() == LiveRiskResult.Level.MEDIUM) {
                        textAiStatus.setText("AI: MODERATE (" + result.getRiskScore() + "%)");
                        textAiStatus.setTextColor(Color.parseColor("#EAB308"));
                        textSpamWarning.setVisibility(View.GONE);
                    } else {
                        textAiStatus.setText("AI: SAFE (" + result.getRiskScore() + "%)");
                        textAiStatus.setTextColor(Color.parseColor("#22C55E"));
                        textSpamWarning.setVisibility(View.GONE);
                    }
                });
            }
        });

        aiProcessor.start();
    }

    private void performVoiceAiAnalysis(boolean showModalOnFinish) {
        LiveRiskResult resultToShow = (aiProcessor != null) ? aiProcessor.getCurrentRiskResult() : lastRiskResult;
        if (resultToShow != null) {
            if (showModalOnFinish) {
                LiveCallGuard.showLiveRiskSheet(CallingActivity.this, resultToShow);
            }
        } else {
            Toast.makeText(this, "AI is monitoring live audio stream...", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupSignaling() {
        signalingClient = new SignalingClient(selfPhone, new SignalingClient.SignalingInterface() {
            @Override
            public void onRemoteAnswerReceived(SessionDescription description) {
                if (peerConnection != null) {
                    Log.d(TAG, "Answer Received - Connecting...");
                    peerConnection.setRemoteDescription(new SimpleSdpObserver() {
                        @Override
                        public void onSetSuccess() {
                            runOnUiThread(() -> drainRemoteCandidates());
                        }
                    }, description);
                }
            }

            @Override
            public void onRemoteIceCandidateReceived(IceCandidate candidate) {
                if (peerConnection != null && peerConnection.getRemoteDescription() != null) {
                    peerConnection.addIceCandidate(candidate);
                } else {
                    pendingIceCandidates.add(candidate);
                }
            }

            @Override
            public void onCallEnded() {
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        Toast.makeText(CallingActivity.this, "Call Ended", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }
        });
    }

    private void drainRemoteCandidates() {
        if (peerConnection == null || peerConnection.getRemoteDescription() == null) return;
        Log.d(TAG, "Draining " + pendingIceCandidates.size() + " candidates");
        for (IceCandidate candidate : pendingIceCandidates) {
            peerConnection.addIceCandidate(candidate);
        }
        pendingIceCandidates.clear();
    }

    private void startCallFlow() {
        initializeWebRTC();

        if (isIncoming) {
            String remoteSdp = getIntent().getStringExtra("REMOTE_OFFER");
            if (remoteSdp != null) {
                SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, remoteSdp);
                peerConnection.setRemoteDescription(new SimpleSdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        runOnUiThread(() -> {
                            createAnswer();
                            drainRemoteCandidates();
                        });
                    }
                }, offer);
            }
        } else {
            createCallOffer();
        }
    }

    private void createCallOffer() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                signalingClient.sendOffer(targetPhone, selfPhone, sessionDescription);
            }
        }, constraints);
    }

    private void createAnswer() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                signalingClient.sendAnswer(targetPhone, sessionDescription);
            }
        }, constraints);
    }

    private void startRinging() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), notification);
            if (ringtone != null) ringtone.play();
        } catch (Exception e) {
            Log.e(TAG, "Ringtone error", e);
        }
    }

    private void stopRinging() {
        if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
    }

    private void acceptCall() {
        stopRinging();
        btnAcceptCall.setVisibility(View.GONE);
        textCallTimer.setText("Connecting...");
        startContinuousAiListening();
        if (checkPermissions()) {
            startCallFlow();
        } else {
            requestPermissions();
        }
    }
    
    private void endCall() {
        finish();
    }

    private void initializeWebRTC() {
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(false);
        }

        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        org.webrtc.audio.AudioDeviceModule adm = org.webrtc.audio.JavaAudioDeviceModule.builder(this)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule();

        factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setOptions(new PeerConnectionFactory.Options())
                .createPeerConnectionFactory();

        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googHighpassFilter", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("echoCancellation", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("noiseSuppression", "true"));

        audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack("101", audioSource);
        localAudioTrack.setEnabled(true);

        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        peerConnection = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState s) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) {
                Log.d(TAG, "ICE State: " + s.name());
                if (s == PeerConnection.IceConnectionState.CONNECTED || s == PeerConnection.IceConnectionState.COMPLETED) {
                    onCallConnected();
                }
            }
            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) {}
            @Override public void onIceCandidate(IceCandidate iceCandidate) {
                signalingClient.sendIceCandidate(targetPhone, iceCandidate);
            }
            @Override public void onIceCandidatesRemoved(IceCandidate[] i) {}
            @Override
            public void onAddStream(MediaStream m) {
                Log.d(TAG, "Remote stream added: " + (m != null ? m.getId() : "null"));
                if (m != null && !m.audioTracks.isEmpty()) {
                    for (AudioTrack track : m.audioTracks) {
                        track.setEnabled(true);
                        track.setVolume(1.0);
                    }
                }
            }
            @Override public void onRemoveStream(MediaStream m) {}
            @Override public void onDataChannel(DataChannel d) {}
            @Override public void onRenegotiationNeeded() {}
            @Override
            public void onAddTrack(RtpReceiver r, MediaStream[] m) {
                Log.d(TAG, "Remote track added: " + (r != null && r.track() != null ? r.track().kind() : "null"));
                if (r != null && r.track() instanceof AudioTrack) {
                    AudioTrack remoteAudio = (AudioTrack) r.track();
                    remoteAudio.setEnabled(true);
                    remoteAudio.setVolume(1.0);
                }
            }
        });

        peerConnection.addTrack(localAudioTrack);
    }

    private void setupActions() {
        setupActionItem(findViewById(R.id.action_mute), R.drawable.ic_mic, R.string.label_mute);
        setupActionItem(findViewById(R.id.action_speaker), R.drawable.ic_speaker, R.string.label_speaker);
        setupActionItem(findViewById(R.id.action_ai_mode), R.drawable.ic_ai_mode, R.string.label_ai_mode);
        setupActionItem(findViewById(R.id.action_record), R.drawable.ic_record, R.string.label_record);
        setupActionItem(findViewById(R.id.action_hold), R.drawable.ic_hold, R.string.label_hold);
        setupActionItem(findViewById(R.id.action_keypad), R.drawable.ic_keypad, R.string.label_keypad);
    }

    private void setupActionItem(View container, int iconRes, int labelRes) {
        if (container == null) return;
        FloatingActionButton fab = container.findViewById(R.id.fab_action);
        TextView label = container.findViewById(R.id.text_action_label);
        fab.setImageResource(iconRes);
        label.setText(labelRes);

        container.setOnClickListener(v -> {
            boolean isSelected = !v.isSelected();
            v.setSelected(isSelected);

            // Change colors to show active/selected state
            if (isSelected) {
                fab.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                fab.setImageTintList(ColorStateList.valueOf(Color.parseColor("#0F172A"))); // Dark color for contrast
                label.setAlpha(1.0f);
            } else {
                fab.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))); // Semi-transparent
                fab.setImageTintList(ColorStateList.valueOf(Color.WHITE));
                label.setAlpha(0.8f);
            }

            // Real call actions
            if (labelRes == R.string.label_mute) {
                if (localAudioTrack != null) {
                    localAudioTrack.setEnabled(!isSelected);
                }
                ActiveCallSession.getInstance().updateMute(isSelected);
            } else if (labelRes == R.string.label_speaker) {
                audioManager.setSpeakerphoneOn(isSelected);
            } else if (labelRes == R.string.label_ai_mode) {
                performVoiceAiAnalysis(true);
            } else if (labelRes == R.string.label_record) {
                toggleCallRecording(isSelected);
            }
        });
    }

    private void toggleCallRecording(boolean start) {
        if (start) {
            String callerName = getIntent().getStringExtra("CONTACT_NAME");
            boolean success = CallRecordingHelper.getInstance().startRecording(this, targetPhone, callerName);
            if (success) {
                Toast.makeText(this, "Call recording started", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Unable to start recording", Toast.LENGTH_SHORT).show();
            }
        } else {
            CallRecordingHelper.getInstance().stopRecording();
            Toast.makeText(this, "Call recording saved", Toast.LENGTH_SHORT).show();
        }
    }

    private void runTimer() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (running) {
                    int mins = (seconds % 3600) / 60;
                    int secs = seconds % 60;
                    String formatted = String.format(Locale.getDefault(), "%02d:%02d", mins, secs);
                    textCallTimer.setText(formatted);
                    ActiveCallSession.getInstance().updateDuration(formatted);
                    seconds++;
                    handler.postDelayed(this, 1000);
                }
            }
        });
    }

    public void minimizeCall() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (isConnected || running) {
            minimizeCall();
        } else {
            super.onBackPressed();
        }
    }

    public void endCallFromBanner() {
        endCall();
    }

    public void toggleMuteFromBanner() {
        View muteView = findViewById(R.id.action_mute);
        if (muteView != null) {
            muteView.performClick();
        }
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        running = false;
        stopRinging();
        ActiveCallSession.getInstance().endSession();
        if (aiProcessor != null) {
            aiProcessor.stop();
            aiProcessor = null;
        }
        CallRecordingHelper.getInstance().stopRecording();
        saveCallLogEntry();
        if (signalingClient != null) {
            signalingClient.endCall(targetPhone);
            signalingClient.destroy();
        }
        if (peerConnection != null) peerConnection.dispose();
        if (audioSource != null) audioSource.dispose();
        if (factory != null) factory.dispose();

        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
            audioManager.setSpeakerphoneOn(false);
        }

        CallService.resumeListening();
    }

    private boolean callLogSaved = false;

    private void saveCallLogEntry() {
        if (callLogSaved) return;
        callLogSaved = true;
        if (targetPhone == null || targetPhone.isEmpty()) return;
        try {
            String name = getIntent().getStringExtra("CONTACT_NAME");
            int callType;
            if (isIncoming) {
                callType = isConnected ? CallLogEntry.TYPE_INCOMING : CallLogEntry.TYPE_MISSED;
            } else {
                callType = CallLogEntry.TYPE_OUTGOING;
            }
            boolean isSpam = getIntent().getBooleanExtra("IS_SPAM", false);

            CallLogEntry entry = new CallLogEntry(
                    targetPhone,
                    name,
                    callType,
                    System.currentTimeMillis(),
                    seconds,
                    isSpam
            );
            CallLogRepository.getInstance(this).addCallLog(entry);
        } catch (Exception e) {
            Log.e(TAG, "Error saving call log", e);
        }
    }

    private static class SimpleSdpObserver implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription s) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String s) { Log.e(TAG, "SDP Error: " + s); }
        @Override public void onSetFailure(String s) { Log.e(TAG, "SDP Error: " + s); }
    }
}
