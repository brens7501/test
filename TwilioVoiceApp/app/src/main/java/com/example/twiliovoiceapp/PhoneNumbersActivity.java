package com.example.twiliovoiceapp;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.twiliovoiceapp.adapter.PhoneNumberAdapter;
import com.example.twiliovoiceapp.databinding.ActivityPhoneNumbersBinding;
import com.example.twiliovoiceapp.model.TwilioPhoneNumber;
import com.example.twiliovoiceapp.repository.PhoneNumberRepository;
import com.example.twiliovoiceapp.viewmodel.PhoneNumberViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * PhoneNumbersActivity - Activity for managing Twilio phone numbers.
 * This activity allows users to view, add, edit, and delete Twilio phone numbers.
 * Users can also set a default number for outgoing calls and assign nicknames to numbers.
 */
public class PhoneNumbersActivity extends AppCompatActivity implements PhoneNumberAdapter.PhoneNumberActionListener {
    private ActivityPhoneNumbersBinding binding;
    private PhoneNumberViewModel phoneNumberViewModel;
    private PhoneNumberAdapter adapter;
    private List<TwilioPhoneNumber> phoneNumbers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPhoneNumbersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set up the toolbar
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_activity_phone_numbers);
        }

        // Initialize ViewModel
        phoneNumberViewModel = new ViewModelProvider(this).get(PhoneNumberViewModel.class);

        // Set up RecyclerView
        setupRecyclerView();

        // Set up action buttons
        setupActionButtons();

        // Load phone numbers
        loadPhoneNumbers();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    /**
     * Set up the RecyclerView for displaying phone numbers
     */
    private void setupRecyclerView() {
        adapter = new PhoneNumberAdapter(this, phoneNumbers);
        binding.recyclerViewPhoneNumbers.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewPhoneNumbers.setAdapter(adapter);
    }

    /**
     * Set up action buttons (Add Number, Refresh Numbers)
     */
    private void setupActionButtons() {
        // Add Number button
        binding.buttonAddNumber.setOnClickListener(v -> showAddNumberDialog());

        // Refresh Numbers button
        binding.buttonRefreshNumbers.setOnClickListener(v -> refreshPhoneNumbers());
    }

    /**
     * Load phone numbers from the repository
     */
    private void loadPhoneNumbers() {
        phoneNumberViewModel.getAllPhoneNumbers().observe(this, numbers -> {
            phoneNumbers.clear();
            if (numbers != null) {
                phoneNumbers.addAll(numbers);
            }
            adapter.notifyDataSetChanged();
            updateEmptyState();
        });
    }

    /**
     * Update the empty state visibility based on whether there are phone numbers
     */
    private void updateEmptyState() {
        if (phoneNumbers.isEmpty()) {
            binding.layoutEmptyState.setVisibility(View.VISIBLE);
            binding.recyclerViewPhoneNumbers.setVisibility(View.GONE);
        } else {
            binding.layoutEmptyState.setVisibility(View.GONE);
            binding.recyclerViewPhoneNumbers.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Show dialog for adding a new Twilio number
     */
    private void showAddNumberDialog() {
        // In a real app, this would fetch available numbers from Twilio API
        // For now, we'll just show a simple dialog to manually enter a number

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_phone_number, null);
        TextInputLayout inputLayoutPhoneNumber = dialogView.findViewById(R.id.inputLayoutPhoneNumber);
        TextInputEditText editTextPhoneNumber = dialogView.findViewById(R.id.editTextPhoneNumber);
        TextInputLayout inputLayoutNickname = dialogView.findViewById(R.id.inputLayoutNickname);
        TextInputEditText editTextNickname = dialogView.findViewById(R.id.editTextNickname);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_add_number)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save_nickname, (dialog, which) -> {
                    String phoneNumber = editTextPhoneNumber.getText().toString().trim();
                    String nickname = editTextNickname.getText().toString().trim();

                    if (phoneNumber.isEmpty()) {
                        Toast.makeText(this, "Please enter a valid phone number", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Format phone number if needed
                    if (!phoneNumber.startsWith("+")) {
                        phoneNumber = "+" + phoneNumber;
                    }

                    // Create new phone number object
                    TwilioPhoneNumber twilioNumber = new TwilioPhoneNumber(
                            phoneNumber,
                            nickname,
                            "Manual Entry",
                            "manual_sid_" + System.currentTimeMillis(),
                            false
                    );

                    // Add to repository
                    phoneNumberViewModel.insert(twilioNumber);
                    Toast.makeText(this, "Phone number added", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /**
     * Refresh phone numbers from Twilio API
     */
    private void refreshPhoneNumbers() {
        // Show loading indicator
        binding.progressLoading.setVisibility(View.VISIBLE);
        binding.recyclerViewPhoneNumbers.setVisibility(View.GONE);
        binding.layoutEmptyState.setVisibility(View.GONE);

        // Call repository to sync with Twilio
        phoneNumberViewModel.syncPhoneNumbersWithTwilio(new PhoneNumberRepository.SyncCallback() {
            @Override
            public void onSuccess(int count) {
                runOnUiThread(() -> {
                    binding.progressLoading.setVisibility(View.GONE);
                    Toast.makeText(PhoneNumbersActivity.this, 
                            "Successfully synced " + count + " phone numbers", 
                            Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    binding.progressLoading.setVisibility(View.GONE);
                    binding.recyclerViewPhoneNumbers.setVisibility(View.VISIBLE);
                    Toast.makeText(PhoneNumbersActivity.this, 
                            "Error: " + errorMessage, 
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // PhoneNumberAdapter.PhoneNumberActionListener implementation

    @Override
    public void onEditNickname(TwilioPhoneNumber phoneNumber) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_nickname, null);
        EditText editTextNickname = dialogView.findViewById(R.id.editTextNickname);
        
        // Set current nickname if available
        if (phoneNumber.getNickname() != null) {
            editTextNickname.setText(phoneNumber.getNickname());
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_edit_nickname)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save_nickname, (dialog, which) -> {
                    String nickname = editTextNickname.getText().toString().trim();
                    phoneNumberViewModel.updateNickname(phoneNumber.getPhoneNumber(), nickname);
                    Toast.makeText(this, R.string.msg_nickname_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    @Override
    public void onSetDefault(TwilioPhoneNumber phoneNumber) {
        phoneNumberViewModel.setAsDefault(phoneNumber.getPhoneNumber());
        Toast.makeText(this, 
                getString(R.string.msg_default_number_set, phoneNumber.getDisplayName()), 
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDelete(TwilioPhoneNumber phoneNumber) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_title_confirm_delete)
                .setMessage(R.string.dialog_msg_confirm_delete)
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> {
                    phoneNumberViewModel.delete(phoneNumber);
                    Toast.makeText(this, "Phone number removed", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
