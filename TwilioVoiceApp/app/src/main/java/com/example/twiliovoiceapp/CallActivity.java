package com.example.twiliovoiceapp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.twiliovoiceapp.databinding.ActivityCallBinding;
import com.example.twiliovoiceapp.model.TwilioPhoneNumber;
import com.example.twiliovoiceapp.service.VoiceService;
import com.example.twiliovoiceapp.viewmodel.PhoneNumberViewModel;

/**
 * CallActivity - Handles the active call UI and interaction.
 * This activity displays call information, call status, and provides controls
 * for managing the active call (mute, speaker, keypad, recording, end call).
 */
public class CallActivity extends AppCompatActivity implements VoiceService.CallStateListener {
    private static final String TAG = "CallActivity";

    private ActivityCallBinding binding;
    private PhoneNumberViewModel phoneNumberViewModel;
    private VoiceService voiceService;
    private boolean bound = false;
    private boolean keypadVisible = false;
    
    // Call details
    private String phoneNumber;
    private String twilioNumber;
    private String displayName;
    private AudioManager audioManager;

    // Service connection
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            VoiceService.VoiceBinder binder = (VoiceService.VoiceBinder) service;
            voiceService = binder.getService();
            voiceService.setCallStateListener(CallActivity.this);
            bound = true;
            
            // Start the call if we're just binding
            if (voiceService.getCallState() == VoiceService.CallState.DISCONNECTED) {
                startCall();
            } else {
                // Update UI to match current call state
                updateUIForCallState(voiceService.getCallState());
                
                // Update mute and speaker state
                updateMuteButton(voiceService.isMuted());
                updateSpeakerButton(voiceService.isSpeakerOn());
                
                // Update recording state
                if (voiceService.isRecording()) {
                    binding.textViewRecordingIndicator.setVisibility(View.VISIBLE);
                    binding.textViewRecord.setText(R.string.btn_stop_recording);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            voiceService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCallBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize audio manager
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        
        // Initialize ViewModel
        phoneNumberViewModel = new ViewModelProvider(this).get(PhoneNumberViewModel.class);
        
        // Get call details from intent
        Intent intent = getIntent();
        phoneNumber = intent.getStringExtra("phone_number");
        twilioNumber = intent.getStringExtra("twilio_number");
        
        if (phoneNumber == null || twilioNumber == null) {
            Toast.makeText(this, "Invalid call parameters", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Set up UI
        setupUI();
        setupCallControls();
        
        // Bind to the voice service
        Intent serviceIntent = new Intent(this, VoiceService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        
        // If we're not bound to the service, bind now
        if (!bound) {
            Intent serviceIntent = new Intent(this, VoiceService.class);
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        
        // Don't unbind the service when stopping the activity
        // This allows the call to continue in the background
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Unbind from the service, but don't stop it
        if (bound) {
            voiceService.removeCallStateListener();
            unbindService(serviceConnection);
            bound = false;
        }
    }
    
    /**
     * Set up the UI with call details
     */
    private void setupUI() {
        // Set the phone number in the UI
        binding.textViewContactName.setText(phoneNumber);
        
        // Look up the Twilio number to get its display name
        phoneNumberViewModel.getByPhoneNumber(twilioNumber).observe(this, twilioPhoneNumber -> {
            if (twilioPhoneNumber != null) {
                displayName = twilioPhoneNumber.getDisplayName();
                binding.textViewTwilioNumber.setText(getString(R.string.label_select_twilio_number) + " " + displayName);
            } else {
                binding.textViewTwilioNumber.setText(getString(R.string.label_select_twilio_number) + " " + twilioNumber);
            }
        });
        
        // Initially hide the keypad
        binding.cardKeypad.setVisibility(View.GONE);
        
        // Initially hide the recording indicator
        binding.textViewRecordingIndicator.setVisibility(View.GONE);
    }
    
    /**
     * Set up the call control buttons
     */
    private void setupCallControls() {
        // End call button
        binding.fabEndCall.setOnClickListener(v -> endCall());
        
        // Mute button
        binding.fabMute.setOnClickListener(v -> toggleMute());
        
        // Speaker button
        binding.fabSpeaker.setOnClickListener(v -> toggleSpeaker());
        
        // Keypad button
        binding.fabKeypad.setOnClickListener(v -> toggleKeypad());
        
        // Recording button
        binding.fabRecord.setOnClickListener(v -> toggleRecording());
        
        // Keypad digit buttons
        binding.buttonKeypad0.setOnClickListener(v -> sendDTMF("0"));
        binding.buttonKeypad1.setOnClickListener(v -> sendDTMF("1"));
        binding.buttonKeypad2.setOnClickListener(v -> sendDTMF("2"));
        binding.buttonKeypad3.setOnClickListener(v -> sendDTMF("3"));
        binding.buttonKeypad4.setOnClickListener(v -> sendDTMF("4"));
        binding.buttonKeypad5.setOnClickListener(v -> sendDTMF("5"));
        binding.buttonKeypad6.setOnClickListener(v -> sendDTMF("6"));
        binding.buttonKeypad7.setOnClickListener(v -> sendDTMF("7"));
        binding.buttonKeypad8.setOnClickListener(v -> sendDTMF("8"));
        binding.buttonKeypad9.setOnClickListener(v -> sendDTMF("9"));
        binding.buttonKeypadStar.setOnClickListener(v -> sendDTMF("*"));
        binding.buttonKeypadHash.setOnClickListener(v -> sendDTMF("#"));
    }
    
    /**
     * Start a new call
     */
    private void startCall() {
        if (bound && voiceService != null) {
            // Start the call service
            Intent serviceIntent = new Intent(this, VoiceService.class);
            serviceIntent.setAction(VoiceService.ACTION_OUTGOING_CALL);
            serviceIntent.putExtra("phone_number", phoneNumber);
            serviceIntent.putExtra("twilio_number", twilioNumber);
            startService(serviceIntent);
            
            // Update UI
            updateUIForCallState(VoiceService.CallState.CONNECTING);
        }
    }
    
    /**
     * End the current call
     */
    private void endCall() {
        if (bound && voiceService != null) {
            voiceService.disconnectCall();
        }
        finish();
    }
    
    /**
     * Toggle mute state
     */
    private void toggleMute() {
        if (bound && voiceService != null) {
            boolean isMuted = voiceService.toggleMute();
            updateMuteButton(isMuted);
        }
    }
    
    /**
     * Update the mute button UI based on mute state
     * 
     * @param isMuted Whether the call is muted
     */
    private void updateMuteButton(boolean isMuted) {
        if (isMuted) {
            binding.fabMute.setImageResource(R.drawable.ic_mic_off);
            binding.textViewMute.setText(R.string.btn_unmute);
        } else {
            binding.fabMute.setImageResource(R.drawable.ic_mic);
            binding.textViewMute.setText(R.string.btn_mute);
        }
    }
    
    /**
     * Toggle speaker state
     */
    private void toggleSpeaker() {
        if (bound && voiceService != null) {
            boolean isSpeakerOn = voiceService.toggleSpeaker();
            updateSpeakerButton(isSpeakerOn);
        }
    }
    
    /**
     * Update the speaker button UI based on speaker state
     * 
     * @param isSpeakerOn Whether the speaker is on
     */
    private void updateSpeakerButton(boolean isSpeakerOn) {
        if (isSpeakerOn) {
            binding.fabSpeaker.setImageResource(R.drawable.ic_speaker_on);
            binding.textViewSpeaker.setText(R.string.btn_speaker_off);
        } else {
            binding.fabSpeaker.setImageResource(R.drawable.ic_speaker_off);
            binding.textViewSpeaker.setText(R.string.btn_speaker_on);
        }
    }
    
    /**
     * Toggle keypad visibility
     */
    private void toggleKeypad() {
        keypadVisible = !keypadVisible;
        binding.cardKeypad.setVisibility(keypadVisible ? View.VISIBLE : View.GONE);
    }
    
    /**
     * Toggle call recording
     */
    private void toggleRecording() {
        if (bound && voiceService != null) {
            if (voiceService.isRecording()) {
                // Stop recording
                voiceService.stopRecording();
                binding.textViewRecordingIndicator.setVisibility(View.GONE);
                binding.textViewRecord.setText(R.string.btn_start_recording);
            } else {
                // Start recording
                boolean success = voiceService.startRecording();
                if (success) {
                    binding.textViewRecordingIndicator.setVisibility(View.VISIBLE);
                    binding.textViewRecord.setText(R.string.btn_stop_recording);
                } else {
                    Toast.makeText(this, R.string.error_recording_failed, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    
    /**
     * Send DTMF tones
     * 
     * @param digits The digits to send
     */
    private void sendDTMF(String digits) {
        if (bound && voiceService != null) {
            voiceService.sendDigits(digits);
        }
    }
    
    /**
     * Update the UI based on call state
     * 
     * @param state The current call state
     */
    private void updateUIForCallState(VoiceService.CallState state) {
        switch (state) {
            case CONNECTING:
                binding.textViewCallStatus.setText(R.string.call_status_connecting);
                binding.textViewCallStatus.setTextColor(getResources().getColor(R.color.callConnecting));
                binding.chronometerCallDuration.setVisibility(View.GONE);
                break;
                
            case RINGING:
                binding.textViewCallStatus.setText(R.string.call_status_ringing);
                binding.textViewCallStatus.setTextColor(getResources().getColor(R.color.callConnecting));
                binding.chronometerCallDuration.setVisibility(View.GONE);
                break;
                
            case CONNECTED:
                binding.textViewCallStatus.setText(R.string.call_status_in_progress);
                binding.textViewCallStatus.setTextColor(getResources().getColor(R.color.callActive));
                binding.chronometerCallDuration.setVisibility(View.VISIBLE);
                binding.chronometerCallDuration.setBase(SystemClock.elapsedRealtime());
                binding.chronometerCallDuration.start();
                break;
                
            case DISCONNECTED:
            case FAILED:
                binding.textViewCallStatus.setText(R.string.call_status_disconnected);
                binding.textViewCallStatus.setTextColor(getResources().getColor(R.color.callEnded));
                binding.chronometerCallDuration.stop();
                
                // Finish activity after a delay
                binding.getRoot().postDelayed(this::finish, 2000);
                break;
        }
    }

    // VoiceService.CallStateListener implementation
    
    @Override
    public void onCallStateChanged(VoiceService.CallState state, String phoneNumber) {
        Log.d(TAG, "Call state changed: " + state);
        updateUIForCallState(state);
    }

    @Override
    public void onCallConnected() {
        Log.d(TAG, "Call connected");
    }

    @Override
    public void onCallDisconnected() {
        Log.d(TAG, "Call disconnected");
    }

    @Override
    public void onCallFailed(String errorMessage) {
        Log.e(TAG, "Call failed: " + errorMessage);
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRecordingStarted() {
        Log.d(TAG, "Recording started");
        Toast.makeText(this, R.string.recording_started, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordingStopped(String filePath) {
        Log.d(TAG, "Recording stopped: " + filePath);
        Toast.makeText(this, getString(R.string.recording_saved, filePath), Toast.LENGTH_LONG).show();
    }
}
