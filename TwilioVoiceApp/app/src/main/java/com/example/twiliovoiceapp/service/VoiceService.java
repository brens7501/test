package com.example.twiliovoiceapp.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.twiliovoiceapp.CallActivity;
import com.example.twiliovoiceapp.R;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.RegistrationException;
import com.twilio.voice.RegistrationListener;
import com.twilio.voice.Voice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for handling Twilio voice calls in the background.
 * This service maintains the call state even when the app is not in the foreground,
 * handles audio routing, and manages call recording functionality.
 */
public class VoiceService extends Service {
    private static final String TAG = "VoiceService";
    private static final String CHANNEL_ID = "voice_calls_channel";
    private static final int NOTIFICATION_ID = 1;

    // Call state constants
    public static final String ACTION_OUTGOING_CALL = "com.example.twiliovoiceapp.ACTION_OUTGOING_CALL";
    public static final String ACTION_INCOMING_CALL = "com.example.twiliovoiceapp.ACTION_INCOMING_CALL";
    public static final String ACTION_CANCEL_CALL = "com.example.twiliovoiceapp.ACTION_CANCEL_CALL";
    public static final String ACTION_FCM_TOKEN = "com.example.twiliovoiceapp.ACTION_FCM_TOKEN";

    // Call state
    public enum CallState {
        CONNECTING,
        RINGING,
        CONNECTED,
        DISCONNECTED,
        FAILED
    }

    // Binder for client communication
    private final IBinder binder = new VoiceBinder();
    
    // Call properties
    private Call activeCall;
    private CallState callState = CallState.DISCONNECTED;
    private String phoneNumber;
    private String twilioPhoneNumber;
    private boolean isMuted = false;
    private boolean isSpeakerOn = false;
    
    // Recording properties
    private boolean isRecording = false;
    private File recordingFile;
    private FileOutputStream recordingOutputStream;
    
    // System services
    private AudioManager audioManager;
    private PowerManager.WakeLock wakeLock;
    private ExecutorService executorService;
    private Handler mainHandler;
    
    // Listeners
    private CallStateListener callStateListener;
    
    /**
     * Interface for notifying call state changes to clients
     */
    public interface CallStateListener {
        void onCallStateChanged(CallState state, String phoneNumber);
        void onCallConnected();
        void onCallDisconnected();
        void onCallFailed(String errorMessage);
        void onRecordingStarted();
        void onRecordingStopped(String filePath);
    }
    
    /**
     * Binder class for client communication
     */
    public class VoiceBinder extends Binder {
        public VoiceService getService() {
            return VoiceService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "VoiceService onCreate");
        
        // Initialize system services
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TwilioVoiceApp:VoiceWakeLock");
        
        // Initialize handlers and executors
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Create notification channel for Android O and above
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "VoiceService onStartCommand");
        
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_OUTGOING_CALL:
                        handleOutgoingCall(intent);
                        break;
                    case ACTION_INCOMING_CALL:
                        handleIncomingCall(intent);
                        break;
                    case ACTION_CANCEL_CALL:
                        disconnectCall();
                        break;
                }
            }
        }
        
        return START_NOT_STICKY;
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "VoiceService onDestroy");
        
        // Clean up resources
        disconnectCall();
        stopRecording();
        releaseWakeLock();
        
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        
        super.onDestroy();
    }
    
    /**
     * Set the call state listener
     * 
     * @param listener The call state listener
     */
    public void setCallStateListener(CallStateListener listener) {
        this.callStateListener = listener;
    }
    
    /**
     * Remove the call state listener
     */
    public void removeCallStateListener() {
        this.callStateListener = null;
    }
    
    /**
     * Get the current call state
     * 
     * @return The current call state
     */
    public CallState getCallState() {
        return callState;
    }
    
    /**
     * Get the phone number of the current call
     * 
     * @return The phone number
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    /**
     * Get the Twilio phone number used for the current call
     * 
     * @return The Twilio phone number
     */
    public String getTwilioPhoneNumber() {
        return twilioPhoneNumber;
    }
    
    /**
     * Check if the call is muted
     * 
     * @return true if muted, false otherwise
     */
    public boolean isMuted() {
        return isMuted;
    }
    
    /**
     * Check if speaker is enabled
     * 
     * @return true if speaker is on, false otherwise
     */
    public boolean isSpeakerOn() {
        return isSpeakerOn;
    }
    
    /**
     * Check if call recording is active
     * 
     * @return true if recording, false otherwise
     */
    public boolean isRecording() {
        return isRecording;
    }
    
    /**
     * Make an outgoing call
     * 
     * @param phoneNumber The phone number to call
     * @param twilioPhoneNumber The Twilio phone number to use as caller ID
     */
    public void makeCall(String phoneNumber, String twilioPhoneNumber) {
        this.phoneNumber = phoneNumber;
        this.twilioPhoneNumber = twilioPhoneNumber;
        
        // Acquire wake lock to prevent CPU from sleeping during call setup
        acquireWakeLock();
        
        // Update call state
        updateCallState(CallState.CONNECTING);
        
        // Start the service as a foreground service with notification
        startForeground(NOTIFICATION_ID, createCallNotification());
        
        // Get Twilio access token from server
        executorService.execute(() -> {
            try {
                // Get Twilio access token
                String accessToken = getTwilioAccessToken(twilioPhoneNumber);
                
                // Set up call parameters
                Map<String, String> params = new HashMap<>();
                params.put("To", phoneNumber);
                params.put("From", twilioPhoneNumber);
                
                // Create connect options
                ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken)
                        .params(params)
                        .build();
                
                // Make the call on the main thread
                mainHandler.post(() -> {
                    activeCall = Voice.connect(VoiceService.this, connectOptions, new CallListener());
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error making call", e);
                mainHandler.post(() -> {
                    updateCallState(CallState.FAILED);
                    if (callStateListener != null) {
                        callStateListener.onCallFailed("Failed to connect: " + e.getMessage());
                    }
                    stopSelf();
                });
            }
        });
    }
    
    /**
     * Answer an incoming call
     * 
     * @param callInvite The incoming call invite
     */
    public void answerCall(CallInvite callInvite) {
        if (callInvite != null) {
            acquireWakeLock();
            updateCallState(CallState.CONNECTING);
            startForeground(NOTIFICATION_ID, createCallNotification());
            
            // Extract caller information
            Map<String, String> params = callInvite.getCustomParameters();
            if (params.containsKey("From")) {
                phoneNumber = params.get("From");
            }
            
            // Answer the call
            activeCall = callInvite.accept(new CallListener());
        }
    }
    
    /**
     * Disconnect the current call
     */
    public void disconnectCall() {
        if (activeCall != null) {
            activeCall.disconnect();
            activeCall = null;
        }
        
        stopRecording();
        updateCallState(CallState.DISCONNECTED);
        releaseWakeLock();
        stopForeground(true);
        stopSelf();
    }
    
    /**
     * Toggle mute state
     * 
     * @return The new mute state
     */
    public boolean toggleMute() {
        if (activeCall != null) {
            isMuted = !isMuted;
            activeCall.mute(isMuted);
            return isMuted;
        }
        return false;
    }
    
    /**
     * Toggle speaker state
     * 
     * @return The new speaker state
     */
    public boolean toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn;
        
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(isSpeakerOn);
        }
        
        return isSpeakerOn;
    }
    
    /**
     * Send DTMF tones
     * 
     * @param digits The digits to send
     */
    public void sendDigits(String digits) {
        if (activeCall != null && digits != null) {
            activeCall.sendDigits(digits);
        }
    }
    
    /**
     * Start call recording
     * 
     * @return true if recording started successfully, false otherwise
     */
    public boolean startRecording() {
        if (activeCall == null || isRecording) {
            return false;
        }
        
        try {
            // Create recording directory if it doesn't exist
            File recordingDir = getRecordingDirectory();
            if (!recordingDir.exists()) {
                recordingDir.mkdirs();
            }
            
            // Create recording file
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = "call_" + timestamp + "_" + phoneNumber.replaceAll("[^0-9]", "") + ".wav";
            recordingFile = new File(recordingDir, fileName);
            
            // Open output stream
            recordingOutputStream = new FileOutputStream(recordingFile);
            
            // Start recording
            activeCall.startRecording(new Call.AudioRecordingListener() {
                @Override
                public void onRecordingStarted() {
                    isRecording = true;
                    if (callStateListener != null) {
                        callStateListener.onRecordingStarted();
                    }
                }
                
                @Override
                public void onRecordingFailed(Exception e) {
                    Log.e(TAG, "Recording failed", e);
                    isRecording = false;
                    closeRecordingOutputStream();
                }
                
                @Override
                public void onRecordingStopped() {
                    isRecording = false;
                    closeRecordingOutputStream();
                    if (callStateListener != null && recordingFile != null) {
                        callStateListener.onRecordingStopped(recordingFile.getAbsolutePath());
                    }
                }
                
                @Override
                public void onBufferAvailable(ByteBuffer byteBuffer) {
                    if (recordingOutputStream != null) {
                        try {
                            byte[] data = new byte[byteBuffer.remaining()];
                            byteBuffer.get(data);
                            recordingOutputStream.write(data);
                        } catch (IOException e) {
                            Log.e(TAG, "Error writing recording data", e);
                        }
                    }
                }
            });
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording", e);
            closeRecordingOutputStream();
            return false;
        }
    }
    
    /**
     * Stop call recording
     */
    public void stopRecording() {
        if (activeCall != null && isRecording) {
            activeCall.stopRecording();
            isRecording = false;
        }
        closeRecordingOutputStream();
    }
    
    /**
     * Close the recording output stream
     */
    private void closeRecordingOutputStream() {
        if (recordingOutputStream != null) {
            try {
                recordingOutputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing recording output stream", e);
            } finally {
                recordingOutputStream = null;
            }
        }
    }
    
    /**
     * Get the recording directory
     * 
     * @return The recording directory
     */
    private File getRecordingDirectory() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String recordingPath = prefs.getString("recording_directory", null);
        
        if (recordingPath != null) {
            File customDir = new File(recordingPath);
            if (customDir.exists() && customDir.canWrite()) {
                return customDir;
            }
        }
        
        // Default to app's external files directory
        return new File(getExternalFilesDir(null), "call_recordings");
    }
    
    /**
     * Handle an outgoing call intent
     * 
     * @param intent The intent containing call details
     */
    private void handleOutgoingCall(Intent intent) {
        String phoneNumber = intent.getStringExtra("phone_number");
        String twilioNumber = intent.getStringExtra("twilio_number");
        
        if (phoneNumber != null && twilioNumber != null) {
            makeCall(phoneNumber, twilioNumber);
        } else {
            Log.e(TAG, "Missing phone number or Twilio number for outgoing call");
            stopSelf();
        }
    }
    
    /**
     * Handle an incoming call intent
     * 
     * @param intent The intent containing call details
     */
    private void handleIncomingCall(Intent intent) {
        // For future implementation of incoming calls
        // Would require FCM integration for push notifications
        Log.d(TAG, "Incoming call handling not yet implemented");
        stopSelf();
    }
    
    /**
     * Update the call state and notify listeners
     * 
     * @param newState The new call state
     */
    private void updateCallState(CallState newState) {
        callState = newState;
        
        // Update notification
        if (newState != CallState.DISCONNECTED && newState != CallState.FAILED) {
            NotificationManager notificationManager = 
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(NOTIFICATION_ID, createCallNotification());
        }
        
        // Notify listener
        if (callStateListener != null) {
            callStateListener.onCallStateChanged(newState, phoneNumber);
            
            switch (newState) {
                case CONNECTED:
                    callStateListener.onCallConnected();
                    break;
                case DISCONNECTED:
                    callStateListener.onCallDisconnected();
                    break;
                case FAILED:
                    callStateListener.onCallFailed("Call failed");
                    break;
            }
        }
    }
    
    /**
     * Create a notification channel for Android O and above
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_calls),
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(getString(R.string.notification_channel_calls_description));
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * Create a notification for the ongoing call
     * 
     * @return The notification
     */
    private Notification createCallNotification() {
        // Create intent for opening the call activity when notification is tapped
        Intent intent = new Intent(this, CallActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Create notification
        String title;
        String text;
        
        switch (callState) {
            case CONNECTING:
                title = getString(R.string.call_status_connecting);
                text = phoneNumber;
                break;
            case RINGING:
                title = getString(R.string.call_status_ringing);
                text = phoneNumber;
                break;
            case CONNECTED:
                title = getString(R.string.notification_ongoing_call);
                text = phoneNumber;
                break;
            case DISCONNECTED:
                title = getString(R.string.notification_call_ended);
                text = phoneNumber;
                break;
            default:
                title = getString(R.string.app_name);
                text = phoneNumber;
                break;
        }
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_phone)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
    }
    
    /**
     * Get a Twilio access token for making calls
     * 
     * @param twilioNumber The Twilio phone number to use
     * @return The access token
     */
    private String getTwilioAccessToken(String twilioNumber) {
        // In a real app, this would make a network request to your backend server
        // The server would generate a token using the Twilio SDK
        
        // For this example, we'll use a mock token
        // In production, replace this with actual token generation
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String accountSid = prefs.getString("account_sid", "");
        String authToken = prefs.getString("auth_token", "");
        
        // Log the values for debugging (remove in production)
        Log.d(TAG, "Using Account SID: " + accountSid);
        
        // TODO: Implement actual token generation via server
        // This would typically be a network request to your backend
        
        return "MOCK_TOKEN_" + accountSid + "_" + twilioNumber;
    }
    
    /**
     * Acquire wake lock to prevent CPU from sleeping during call
     */
    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(10*60*1000L /*10 minutes*/);
        }
    }
    
    /**
     * Release wake lock
     */
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
    
    /**
     * Call listener for Twilio Voice SDK callbacks
     */
    private class CallListener implements Call.Listener {
        @Override
        public void onConnectFailure(@NonNull Call call, @NonNull CallException error) {
            Log.e(TAG, "Call connect failure: " + error.getMessage());
            updateCallState(CallState.FAILED);
            if (callStateListener != null) {
                callStateListener.onCallFailed(error.getMessage());
            }
            releaseWakeLock();
            stopForeground(true);
            stopSelf();
        }

        @Override
        public void onRinging(@NonNull Call call) {
            Log.d(TAG, "Call ringing");
            updateCallState(CallState.RINGING);
        }

        @Override
        public void onConnected(@NonNull Call call) {
            Log.d(TAG, "Call connected");
            updateCallState(CallState.CONNECTED);
            
            // Check if auto-speaker is enabled
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(VoiceService.this);
            boolean autoSpeaker = prefs.getBoolean("auto_speaker", false);
            if (autoSpeaker) {
                toggleSpeaker();
            }
            
            // Request audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                
                AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener(focusChange -> {})
                        .build();
                
                audioManager.requestAudioFocus(focusRequest);
            } else {
                audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }
        }

        @Override
        public void onReconnecting(@NonNull Call call, @NonNull CallException error) {
            Log.d(TAG, "Call reconnecting: " + error.getMessage());
        }

        @Override
        public void onReconnected(@NonNull Call call) {
            Log.d(TAG, "Call reconnected");
        }

        @Override
        public void onDisconnected(@NonNull Call call, CallException error) {
            Log.d(TAG, "Call disconnected");
            
            if (error != null) {
                Log.e(TAG, "Call error: " + error.getMessage());
            }
            
            // Release audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(
                        new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build());
            } else {
                audioManager.abandonAudioFocus(null);
            }
            
            updateCallState(CallState.DISCONNECTED);
            releaseWakeLock();
            stopForeground(true);
            stopSelf();
        }
    }
}
