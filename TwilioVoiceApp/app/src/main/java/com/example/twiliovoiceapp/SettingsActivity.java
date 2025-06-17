package com.example.twiliovoiceapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

/**
 * SettingsActivity - Activity for configuring app settings.
 * This activity allows users to configure Twilio credentials,
 * call settings, and manage phone numbers.
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // Add the settings fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
        
        // Set up the action bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.title_activity_settings);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    /**
     * Settings Fragment that displays the preferences
     */
    public static class SettingsFragment extends PreferenceFragmentCompat implements
            SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            // Load preferences from XML
            setPreferencesFromResource(R.xml.preferences, rootKey);
            
            // Configure Account SID preference
            EditTextPreference accountSidPref = findPreference("account_sid");
            if (accountSidPref != null) {
                accountSidPref.setSummaryProvider(preference -> {
                    String value = ((EditTextPreference) preference).getText();
                    if (value == null || value.isEmpty()) {
                        return "Not set";
                    } else {
                        // Mask the SID for security
                        return "AC" + "••••••••••••••••••••";
                    }
                });
            }
            
            // Configure Auth Token preference (password style)
            EditTextPreference authTokenPref = findPreference("auth_token");
            if (authTokenPref != null) {
                authTokenPref.setOnBindEditTextListener(editText -> 
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
                
                authTokenPref.setSummaryProvider(preference -> {
                    String value = ((EditTextPreference) preference).getText();
                    return (value == null || value.isEmpty()) ? "Not set" : "••••••••••••••••••••";
                });
            }
            
            // Configure recording directory preference
            Preference recordingDirPref = findPreference("recording_directory");
            if (recordingDirPref != null) {
                // Set summary to current value or default
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
                String currentPath = prefs.getString("recording_directory", null);
                if (currentPath != null) {
                    recordingDirPref.setSummary(currentPath);
                } else {
                    recordingDirPref.setSummary("Default location");
                }
                
                // Handle directory selection
                recordingDirPref.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | 
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION | 
                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivityForResult(intent, REQUEST_DIRECTORY);
                    return true;
                });
            }
            
            // Configure manage numbers preference
            Preference manageNumbersPref = findPreference("manage_numbers");
            if (manageNumbersPref != null) {
                manageNumbersPref.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(getActivity(), PhoneNumbersActivity.class);
                    startActivity(intent);
                    return true;
                });
            }
        }
        
        @Override
        public void onResume() {
            super.onResume();
            // Register the preference change listener
            getPreferenceManager().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }
        
        @Override
        public void onPause() {
            super.onPause();
            // Unregister the preference change listener
            getPreferenceManager().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }
        
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // Handle preference changes
            if ("account_sid".equals(key)) {
                String accountSid = sharedPreferences.getString(key, "");
                if (!accountSid.isEmpty() && !accountSid.startsWith("AC")) {
                    // Validate Twilio Account SID format
                    Toast.makeText(getContext(), 
                            "Twilio Account SID should start with 'AC'", 
                            Toast.LENGTH_LONG).show();
                }
            }
        }
        
        @Override
        public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            if (requestCode == REQUEST_DIRECTORY && resultCode == RESULT_OK && data != null) {
                // Get the selected directory URI
                Uri uri = data.getData();
                if (uri != null) {
                    // Take persistent permissions
                    requireContext().getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                    
                    // Save the directory path
                    SharedPreferences.Editor editor = PreferenceManager
                            .getDefaultSharedPreferences(requireContext()).edit();
                    editor.putString("recording_directory", uri.toString());
                    editor.apply();
                    
                    // Update the preference summary
                    Preference recordingDirPref = findPreference("recording_directory");
                    if (recordingDirPref != null) {
                        recordingDirPref.setSummary(uri.toString());
                    }
                    
                    Toast.makeText(getContext(), 
                            "Recording directory set", 
                            Toast.LENGTH_SHORT).show();
                }
            }
            super.onActivityResult(requestCode, resultCode, data);
        }
        
        private static final int REQUEST_DIRECTORY = 1001;
        private static final int RESULT_OK = -1; // Activity.RESULT_OK
    }
}
