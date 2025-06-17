package com.example.twiliovoiceapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.twiliovoiceapp.databinding.ActivityMainBinding;
import com.example.twiliovoiceapp.model.TwilioPhoneNumber;
import com.example.twiliovoiceapp.service.VoiceService;
import com.example.twiliovoiceapp.viewmodel.PhoneNumberViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity - The main entry point of the application.
 * This activity displays the dialer interface and handles call initiation.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;
    
    private ActivityMainBinding binding;
    private PhoneNumberViewModel phoneNumberViewModel;
    private List<TwilioPhoneNumber> twilioPhoneNumbers = new ArrayList<>();
    private TwilioPhoneNumber selectedTwilioNumber = null;
    
    // Required permissions for making calls
    private final String[] requiredPermissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize ViewModel
        phoneNumberViewModel = new ViewModelProvider(this).get(PhoneNumberViewModel.class);
        
        // Set up UI components
        setupDialerPad();
        setupActionButtons();
        setupPhoneNumberInput();
        
        // Load Twilio phone numbers
        loadTwilioPhoneNumbers();
        
        // Check for required permissions
        checkPermissions();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh phone numbers when returning to the activity
        loadTwilioPhoneNumbers();
    }
    
    /**
     * Set up the dialer pad buttons
     */
    private void setupDialerPad() {
        // Set up number buttons (0-9)
        binding.buttonDialer0.setOnClickListener(v -> appendToPhoneNumber("0"));
        binding.buttonDialer1.setOnClickListener(v -> appendToPhoneNumber("1"));
        binding.buttonDialer2.setOnClickListener(v -> appendToPhoneNumber("2"));
        binding.buttonDialer3.setOnClickListener(v -> appendToPhoneNumber("3"));
        binding.buttonDialer4.setOnClickListener(v -> appendToPhoneNumber("4"));
        binding.buttonDialer5.setOnClickListener(v -> appendToPhoneNumber("5"));
        binding.buttonDialer6.setOnClickListener(v -> appendToPhoneNumber("6"));
        binding.buttonDialer7.setOnClickListener(v -> appendToPhoneNumber("7"));
        binding.buttonDialer8.setOnClickListener(v -> appendToPhoneNumber("8"));
        binding.buttonDialer9.setOnClickListener(v -> appendToPhoneNumber("9"));
        
        // Set up special character buttons
        binding.buttonDialerStar.setOnClickListener(v -> appendToPhoneNumber("*"));
        binding.buttonDialerHash.setOnClickListener(v -> appendToPhoneNumber("#"));
        
        // Set up clear button
        binding.buttonClear.setOnClickListener(v -> {
            Editable text = binding.editTextPhoneNumber.getText();
            if (text != null && text.length() > 0) {
                text.delete(text.length() - 1, text.length());
            }
        });
        
        // Long press on clear button to clear all
        binding.buttonClear.setOnLongClickListener(v -> {
            binding.editTextPhoneNumber.setText("");
            return true;
        });
    }
    
    /**
     * Set up the action buttons (call, contacts, settings)
     */
    private void setupActionButtons() {
        // Call button
        binding.fabCall.setOnClickListener(v -> initiateCall());
        
        // Contacts button
        binding.buttonContacts.setOnClickListener(v -> {
            // For future implementation - would open contacts
            Toast.makeText(this, "Contacts feature coming soon", Toast.LENGTH_SHORT).show();
        });
        
        // Settings button
        binding.buttonSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
    }
    
    /**
     * Set up the phone number input field
     */
    private void setupPhoneNumberInput() {
        binding.editTextPhoneNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not used
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Not used
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Update UI based on input (could add formatting here)
            }
        });
    }
    
    /**
     * Load Twilio phone numbers from the repository
     */
    private void loadTwilioPhoneNumbers() {
        phoneNumberViewModel.getAllPhoneNumbers().observe(this, phoneNumbers -> {
            twilioPhoneNumbers = phoneNumbers;
            
            if (phoneNumbers == null || phoneNumbers.isEmpty()) {
                // No phone numbers available
                binding.tvNoTwilioNumbers.setVisibility(View.VISIBLE);
                binding.spinnerTwilioNumbers.setVisibility(View.GONE);
                selectedTwilioNumber = null;
            } else {
                // Phone numbers available
                binding.tvNoTwilioNumbers.setVisibility(View.GONE);
                binding.spinnerTwilioNumbers.setVisibility(View.VISIBLE);
                
                // Create adapter for spinner
                ArrayAdapter<TwilioPhoneNumber> adapter = new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        phoneNumbers
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                binding.spinnerTwilioNumbers.setAdapter(adapter);
                
                // Select default number if available
                TwilioPhoneNumber defaultNumber = phoneNumberViewModel.getDefaultPhoneNumber();
                if (defaultNumber != null) {
                    int position = phoneNumbers.indexOf(defaultNumber);
                    if (position >= 0) {
                        binding.spinnerTwilioNumbers.setSelection(position);
                        selectedTwilioNumber = defaultNumber;
                    }
                } else if (!phoneNumbers.isEmpty()) {
                    selectedTwilioNumber = phoneNumbers.get(0);
                }
                
                // Handle selection changes
                binding.spinnerTwilioNumbers.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        selectedTwilioNumber = phoneNumbers.get(position);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // Not used
                    }
                });
            }
        });
    }
    
    /**
     * Append a digit to the phone number input
     * 
     * @param digit The digit to append
     */
    private void appendToPhoneNumber(String digit) {
        Editable text = binding.editTextPhoneNumber.getText();
        if (text != null) {
            text.append(digit);
        }
    }
    
    /**
     * Initiate a call to the entered phone number
     */
    private void initiateCall() {
        // Get the entered phone number
        String phoneNumber = binding.editTextPhoneNumber.getText().toString().trim();
        
        // Validate phone number
        if (phoneNumber.isEmpty()) {
            Toast.makeText(this, R.string.error_invalid_phone, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Validate Twilio number selection
        if (selectedTwilioNumber == null) {
            Toast.makeText(this, R.string.error_no_twilio_number, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Format phone number if needed (could add E.164 formatting here)
        if (!phoneNumber.startsWith("+")) {
            phoneNumber = "+" + phoneNumber;
        }
        
        // Check permissions before making the call
        if (!checkPermissions()) {
            return;
        }
        
        // Start the call activity
        Intent intent = new Intent(this, CallActivity.class);
        intent.putExtra("phone_number", phoneNumber);
        intent.putExtra("twilio_number", selectedTwilioNumber.getPhoneNumber());
        startActivity(intent);
    }
    
    /**
     * Check if all required permissions are granted
     * 
     * @return true if all permissions are granted, false otherwise
     */
    private boolean checkPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
            return false;
        }
        
        return true;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                // Show dialog explaining why permissions are needed
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Permissions Required")
                        .setMessage("This app requires microphone and phone permissions to make calls. " +
                                "Please grant these permissions to use the calling features.")
                        .setPositiveButton("Settings", (dialog, which) -> {
                            // Open app settings
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }
    }
}
