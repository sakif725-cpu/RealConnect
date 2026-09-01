package com.realconnect.app;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
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
    
    // Video Views & Overlays
    private SurfaceViewRenderer fullscreenVideoView;
    private SurfaceViewRenderer pipVideoView;
    private View cardPipVideo;
    private View cardAvatar;
    private View videoOverlayTop;
    private View videoOverlayBottom;
    private View voiceBgGlow;

    private int seconds = 0;
    private boolean running = false;
    private boolean isConnected = false;
    private boolean isVideoCall = false;
    private boolean isVideoEnabled = true;
    private boolean isFrontCamera = true;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<IceCandidate> pendingIceCandidates = new java.util.concurrent.CopyOnWriteArrayList<>();

    // WebRTC components
    private EglBase rootEglBase;
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private AudioSource audioSource;
    private AudioTrack localAudioTrack;
    private VideoCapturer videoCapturer;
    private SurfaceTextureHelper surfaceTextureHelper;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private VideoTrack remoteVideoTrack;
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
        isVideoCall = getIntent().getBooleanExtra("IS_VIDEO_CALL", false);

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

        // Video views
        fullscreenVideoView = findViewById(R.id.fullscreen_video_view);
        pipVideoView = findViewById(R.id.pip_video_view);
        cardPipVideo = findViewById(R.id.card_pip_video);
        cardAvatar = findViewById(R.id.card_avatar);
        videoOverlayTop = findViewById(R.id.video_overlay_gradient_top);
        videoOverlayBottom = findViewById(R.id.video_overlay_gradient_bottom);
        voiceBgGlow = findViewById(R.id.voice_bg_glow);

        initEglAndVideoRenderers();

        String name = getIntent().getStringExtra("CONTACT_NAME");
        String resolvedName = ContactRepository.getInstance(this).getDisplayName(targetPhone);
        if (resolvedName != null && !resolvedName.equals(targetPhone)) {
            name = resolvedName;
        }

        ImageView imgAvatarCalling = findViewById(R.id.img_avatar_calling);
        AvatarHelper.loadAvatar(this, imgAvatarCalling, targetPhone, name);

        textCallerName.setText(name != null && !name.isEmpty() ? name : (targetPhone != null ? targetPhone : "Unknown"));

        btnEndCall.setOnClickListener(v -> endCall());
        btnAcceptCall.setOnClickListener(v -> acceptCall());

        View badgeAiStatus = findViewById(R.id.ai_status_badge);
        if (badgeAiStatus != null) {
            badgeAiStatus.setOnClickListener(v -> performVoiceAiAnalysis(true));
        }

        if (isVideoCall) {
            switchToVideoMode();
        }

        // Default UI State: Controls visible for caller, hidden for receiver
        if (isIncoming) {
            btnAcceptCall.setVisibility(View.VISIBLE);
            controlsContainer.setVisibility(View.GONE);
            textCallTimer.setText(isVideoCall ? "Incoming Video Call..." : "Incoming Call...");
            startRinging();
            
            if (getIntent().getBooleanExtra("IS_SPAM", false)) {
                showSpamWarning();
            } else {
                AiService.checkSpam(targetPhone, isSpam -> {
                    if (isSpam && !isFinishing() && !isDestroyed()) {
                        runOnUiThread(this::showSpamWarning);
                    }
                });
            }
        } else {
            btnAcceptCall.setVisibility(View.GONE);
            controlsContainer.setVisibility(View.VISIBLE);
            textCallTimer.setText("Calling...");
            textAiStatus.setText("AI GUARD: STANDBY");
            
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

    private void initEglAndVideoRenderers() {
        try {
            rootEglBase = EglBase.create();

            pipVideoView.init(rootEglBase.getEglBaseContext(), null);
            pipVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            pipVideoView.setZOrderMediaOverlay(true);
            pipVideoView.setEnableHardwareScaler(true);

            fullscreenVideoView.init(rootEglBase.getEglBaseContext(), null);
            fullscreenVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            fullscreenVideoView.setEnableHardwareScaler(true);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing video renderers", e);
        }
    }

    public void switchToVideoMode() {
        runOnUiThread(() -> {
            isVideoCall = true;
            if (fullscreenVideoView != null) fullscreenVideoView.setVisibility(View.VISIBLE);
            if (cardPipVideo != null && isVideoEnabled) cardPipVideo.setVisibility(View.VISIBLE);
            if (videoOverlayTop != null) videoOverlayTop.setVisibility(View.VISIBLE);
            if (videoOverlayBottom != null) videoOverlayBottom.setVisibility(View.VISIBLE);
            if (cardAvatar != null) cardAvatar.setVisibility(View.GONE);
            if (voiceBgGlow != null) voiceBgGlow.setVisibility(View.GONE);
        });
    }

    public void switchToAudioMode() {
        runOnUiThread(() -> {
            isVideoCall = false;
            if (fullscreenVideoView != null) fullscreenVideoView.setVisibility(View.GONE);
            if (cardPipVideo != null) cardPipVideo.setVisibility(View.GONE);
            if (videoOverlayTop != null) videoOverlayTop.setVisibility(View.GONE);
            if (videoOverlayBottom != null) videoOverlayBottom.setVisibility(View.GONE);
            if (cardAvatar != null) cardAvatar.setVisibility(View.VISIBLE);
            if (voiceBgGlow != null) voiceBgGlow.setVisibility(View.VISIBLE);
        });
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
            if (audioManager != null) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audioManager.setMicrophoneMute(false);
                if (isVideoCall) {
                    audioManager.setSpeakerphoneOn(true);
                }
            }
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
                        if (signalingClient != null && targetPhone != null && !targetPhone.isEmpty()) {
                            signalingClient.sendThreatAlert(targetPhone, result.getSummary(), result.getRiskScore());
                        }
                    } else if (result.getLevel() == LiveRiskResult.Level.MEDIUM) {
                        textAiStatus.setText("AI: ACTIVE (" + result.getRiskScore() + "%)");
                        textAiStatus.setTextColor(Color.parseColor("#22C55E"));
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
        if (resultToShow == null) {
            boolean isSpamPreFlagged = getIntent().getBooleanExtra("IS_SPAM", false);
            resultToShow = new LiveRiskResult(
                    isSpamPreFlagged ? LiveRiskResult.Level.HIGH : LiveRiskResult.Level.LOW,
                    isSpamPreFlagged ? 85 : 5,
                    isSpamPreFlagged ? "Flagged Spam Number" : "AI Guard is actively monitoring live conversation.",
                    "Safe call. Speak naturally."
            );
            resultToShow.setContextEvaluated(true);
            resultToShow.setSpam(isSpamPreFlagged);
            resultToShow.setCallerIntent("Live Call AI Guard Active");
            resultToShow.addIndicator("• Real-time speech, video & acoustic monitoring active");
            lastRiskResult = resultToShow;
        }
        if (showModalOnFinish) {
            LiveCallGuard.showLiveRiskSheet(CallingActivity.this, resultToShow);
        }
    }

    private void setupSignaling() {
        signalingClient = new SignalingClient(selfPhone, new SignalingClient.SignalingInterface() {
            @Override
            public void onRemoteOfferReceived(String callerPhone, SessionDescription description) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (peerConnection == null) {
                        setupPeerConnection();
                    }
                    peerConnection.setRemoteDescription(new SimpleSdpObserver() {
                        @Override
                        public void onSetSuccess() {
                            drainPendingIceCandidates();
                        }
                    }, description);
                });
            }

            @Override
            public void onRemoteAnswerReceived(SessionDescription description) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (peerConnection != null) {
                        peerConnection.setRemoteDescription(new SimpleSdpObserver() {
                            @Override
                            public void onSetSuccess() {
                                drainPendingIceCandidates();
                            }
                        }, description);
                        onCallConnected();
                    }
                });
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
                    Toast.makeText(CallingActivity.this, "Call Ended", Toast.LENGTH_SHORT).show();
                    endCallLocally();
                });
            }

            @Override
            public void onThreatAlertReceived(String reason, int riskScore) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    LiveRiskResult alertResult = new LiveRiskResult(
                            LiveRiskResult.Level.HIGH,
                            riskScore > 0 ? riskScore : 90,
                            reason != null ? reason : "SUSPICIOUS THREAT DETECTED ON LIVE CALL",
                            "AI Guard detected potential scam keywords or manipulative patterns. Do NOT share OTPs, PINs, or transfer money."
                    );
                    alertResult.setContextEvaluated(true);
                    alertResult.setSpam(true);
                    alertResult.setCallerIntent("Live Call AI Threat Intercept");
                    alertResult.addIndicator("• Remote threat warning: " + reason);
                    lastRiskResult = alertResult;

                    textAiStatus.setText("🚨 FRAUD WARNING (" + alertResult.getRiskScore() + "%)");
                    textAiStatus.setTextColor(Color.parseColor("#EF4444"));
                    textSpamWarning.setText("⚠ " + (reason != null ? reason.toUpperCase() : "POTENTIAL SCAM"));
                    textSpamWarning.setVisibility(View.VISIBLE);

                    LiveCallGuard.showLiveRiskSheet(CallingActivity.this, alertResult);
                });
            }
        });
    }

    private void drainPendingIceCandidates() {
        if (peerConnection == null) return;
        for (IceCandidate c : pendingIceCandidates) {
            peerConnection.addIceCandidate(c);
        }
        pendingIceCandidates.clear();
    }

    private void startCallFlow() {
        setupPeerConnection();
        createOffer();
    }

    private void acceptCall() {
        btnAcceptCall.setVisibility(View.GONE);
        controlsContainer.setVisibility(View.VISIBLE);
        stopRinging();
        if (checkPermissions()) {
            if (peerConnection == null) {
                setupPeerConnection();
            }
            createAnswer();
            onCallConnected();
        } else {
            requestPermissions();
        }
    }

    private void createOffer() {
        if (peerConnection == null) return;
        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                signalingClient.sendOffer(targetPhone, selfPhone, sessionDescription);
            }
        }, sdpConstraints);
    }

    private void createAnswer() {
        if (peerConnection == null) return;
        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                signalingClient.sendAnswer(targetPhone, sessionDescription);
            }
        }, sdpConstraints);
    }

    private VideoCapturer createVideoCapturer() {
        VideoCapturer capturer = null;
        if (Camera2Enumerator.isSupported(this)) {
            capturer = createCameraCapturer(new Camera2Enumerator(this));
        }
        if (capturer == null) {
            capturer = createCameraCapturer(new Camera1Enumerator(true));
        }
        return capturer;
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();
        // Try front camera first
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) {
                    isFrontCamera = true;
                    return capturer;
                }
            }
        }
        // Fallback to rear camera
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer capturer = enumerator.createCapturer(deviceName, null);
                if (capturer != null) {
                    isFrontCamera = false;
                    return capturer;
                }
            }
        }
        return null;
    }

    private void setupPeerConnection() {
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        org.webrtc.audio.JavaAudioDeviceModule adm = org.webrtc.audio.JavaAudioDeviceModule.builder(this)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .setSamplesReadyCallback(new org.webrtc.audio.JavaAudioDeviceModule.SamplesReadyCallback() {
                    @Override
                    public void onWebRtcAudioRecordSamplesReady(org.webrtc.audio.JavaAudioDeviceModule.AudioSamples audioSamples) {
                        if (aiProcessor != null && isConnected) {
                            aiProcessor.onAudioSamplesCaptured(
                                    audioSamples.getData(),
                                    audioSamples.getSampleRate(),
                                    audioSamples.getChannelCount()
                            );
                        }
                    }
                })
                .createAudioDeviceModule();

        VideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(), true, true);
        VideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(
                rootEglBase.getEglBaseContext());

        factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
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

        // Setup Local Video Track
        try {
            videoCapturer = createVideoCapturer();
            if (videoCapturer != null && rootEglBase != null) {
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
                videoSource = factory.createVideoSource(videoCapturer.isScreencast());
                videoCapturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
                videoCapturer.startCapture(1280, 720, 30);

                localVideoTrack = factory.createVideoTrack("102", videoSource);
                localVideoTrack.setEnabled(true);
                localVideoTrack.addSink(pipVideoView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing camera video track", e);
        }

        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        // Global High-Availability STUN Servers
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.services.mozilla.com:3478").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.sipgate.net:3478").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.voip.blackberry.com:3478").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.nextcloud.com:443").createIceServer());

        // Multi-Port & Multi-Protocol OpenRelay TURN Relays (UDP + TCP + TLS)
        iceServers.add(PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelay").setPassword("openrelay").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelay").setPassword("openrelay").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelay").setPassword("openrelay").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turns:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelay").setPassword("openrelay").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turns:openrelay.metered.ca:5349?transport=tcp")
                .setUsername("openrelay").setPassword("openrelay").createIceServer());

        iceServers.add(PeerConnection.IceServer.builder("turn:global.relay.metered.ca:80")
                .setUsername("openrelay").setPassword("openrelay").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:global.relay.metered.ca:443")
                .setUsername("openrelay").setPassword("openrelay").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:global.relay.metered.ca:443?transport=tcp")
                .setUsername("openrelay").setPassword("openrelay").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turns:global.relay.metered.ca:443?transport=tcp")
                .setUsername("openrelay").setPassword("openrelay").createIceServer());

        iceServers.add(PeerConnection.IceServer.builder("turn:standard.relay.metered.ca:80")
                .setUsername("openrelay").setPassword("openrelay").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:standard.relay.metered.ca:443")
                .setUsername("openrelay").setPassword("openrelay").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:standard.relay.metered.ca:443?transport=tcp")
                .setUsername("openrelay").setPassword("openrelay").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turns:standard.relay.metered.ca:443?transport=tcp")
                .setUsername("openrelay").setPassword("openrelay").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;

        peerConnection = factory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override public void onSignalingChange(PeerConnection.SignalingState signalingState) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "ICE State: " + iceConnectionState);
                if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                    onCallConnected();
                } else if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED ||
                           iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
                    runOnUiThread(() -> {
                        if (isConnected) {
                            Toast.makeText(CallingActivity.this, "Call Disconnected", Toast.LENGTH_SHORT).show();
                            endCallLocally();
                        }
                    });
                }
            }
            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                signalingClient.sendIceCandidate(targetPhone, iceCandidate);
            }
            @Override public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}

            @Override
            public void onAddStream(MediaStream mediaStream) {
                Log.d(TAG, "Remote MediaStream added: " + mediaStream.getId());
                if (!mediaStream.videoTracks.isEmpty()) {
                    remoteVideoTrack = mediaStream.videoTracks.get(0);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        remoteVideoTrack.addSink(fullscreenVideoView);
                        switchToVideoMode();
                    });
                }
            }

            @Override
            public void onTrack(RtpTransceiver transceiver) {
                if (transceiver.getReceiver() != null && transceiver.getReceiver().track() != null) {
                    MediaStreamTrack track = transceiver.getReceiver().track();
                    if (track instanceof VideoTrack) {
                        remoteVideoTrack = (VideoTrack) track;
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            remoteVideoTrack.addSink(fullscreenVideoView);
                            switchToVideoMode();
                        });
                    }
                }
            }

            @Override public void onRemoveStream(MediaStream mediaStream) {}
            @Override public void onDataChannel(DataChannel dataChannel) {}
            @Override public void onRenegotiationNeeded() {}
        });

        peerConnection.addTrack(localAudioTrack);
        if (localVideoTrack != null) {
            peerConnection.addTrack(localVideoTrack);
        }
    }

    private void setupActions() {
        setupActionItem(findViewById(R.id.action_mute), R.drawable.ic_mic, R.string.label_mute);
        setupActionItem(findViewById(R.id.action_speaker), R.drawable.ic_speaker, R.string.label_speaker);
        setupActionItem(findViewById(R.id.action_video), R.drawable.ic_video_call, R.string.label_video);
        setupActionItem(findViewById(R.id.action_flip_camera), R.drawable.ic_switch_camera, R.string.label_flip_camera);
        setupActionItem(findViewById(R.id.action_ai_mode), R.drawable.ic_ai_mode, R.string.label_ai_mode);
        setupActionItem(findViewById(R.id.action_record), R.drawable.ic_record, R.string.label_record);
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

            // Visual toggle colors
            if (isSelected) {
                fab.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
                fab.setImageTintList(ColorStateList.valueOf(Color.parseColor("#0F172A")));
                label.setAlpha(1.0f);
            } else {
                fab.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")));
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
            } else if (labelRes == R.string.label_video) {
                toggleVideo(!isSelected);
                fab.setImageResource(isSelected ? R.drawable.ic_video_off : R.drawable.ic_video_call);
            } else if (labelRes == R.string.label_flip_camera) {
                switchCamera();
            } else if (labelRes == R.string.label_ai_mode) {
                performVoiceAiAnalysis(true);
            } else if (labelRes == R.string.label_record) {
                toggleCallRecording(isSelected);
            }
        });
    }

    private void toggleVideo(boolean enable) {
        isVideoEnabled = enable;
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(enable);
        }
        if (cardPipVideo != null) {
            cardPipVideo.setVisibility(enable && isVideoCall ? View.VISIBLE : View.GONE);
        }
        Toast.makeText(this, enable ? "Camera On" : "Camera Paused", Toast.LENGTH_SHORT).show();
    }

    private void switchCamera() {
        if (videoCapturer instanceof CameraVideoCapturer) {
            CameraVideoCapturer cameraCapturer = (CameraVideoCapturer) videoCapturer;
            cameraCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                @Override
                public void onCameraSwitchDone(boolean isFront) {
                    isFrontCamera = isFront;
                    runOnUiThread(() -> Toast.makeText(CallingActivity.this, isFront ? "Front Camera" : "Rear Camera", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onCameraSwitchError(String errorDescription) {
                    Log.e(TAG, "Camera switch error: " + errorDescription);
                }
            });
        }
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
        boolean audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        return audio && camera;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
        }, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (checkPermissions()) {
                if (isIncoming) {
                    acceptCall();
                } else {
                    startCallFlow();
                }
            } else {
                Toast.makeText(this, "Camera and Microphone permissions are required for calling", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startRinging() {
        try {
            Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), ringtoneUri);
            if (ringtone != null) {
                ringtone.play();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing ringtone", e);
        }
    }

    private void stopRinging() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
    }

    private void endCall() {
        if (signalingClient != null && targetPhone != null && !targetPhone.isEmpty()) {
            signalingClient.endCall(targetPhone);
        }
        endCallLocally();
    }

    private void endCallLocally() {
        finish();
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

        // Release Video Resources
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (Exception ignored) {}
            videoCapturer.dispose();
            videoCapturer = null;
        }
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }
        if (pipVideoView != null) {
            pipVideoView.release();
        }
        if (fullscreenVideoView != null) {
            fullscreenVideoView.release();
        }
        if (rootEglBase != null) {
            rootEglBase.release();
            rootEglBase = null;
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
