package com.example.twiliovoiceapp.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.twiliovoiceapp.model.TwilioPhoneNumber;
import com.example.twiliovoiceapp.repository.PhoneNumberRepository;

import java.util.List;

/**
 * ViewModel for phone number operations.
 * This class provides a clean API for the UI to interact with the data layer,
 * and ensures that the UI components don't need to be concerned with the origin of the data.
 * It also survives configuration changes like screen rotations.
 */
public class PhoneNumberViewModel extends AndroidViewModel {
    
    private final PhoneNumberRepository repository;
    private final LiveData<List<TwilioPhoneNumber>> allPhoneNumbers;
    
    /**
     * Constructor for the ViewModel
     * 
     * @param application The application context
     */
    public PhoneNumberViewModel(@NonNull Application application) {
        super(application);
        repository = new PhoneNumberRepository(application);
        allPhoneNumbers = repository.getAllPhoneNumbers();
    }
    
    /**
     * Get all phone numbers as LiveData
     * 
     * @return LiveData containing a list of all phone numbers
     */
    public LiveData<List<TwilioPhoneNumber>> getAllPhoneNumbers() {
        return allPhoneNumbers;
    }
    
    /**
     * Get the default phone number
     * 
     * @return The default phone number, or null if none is set
     */
    public TwilioPhoneNumber getDefaultPhoneNumber() {
        return repository.getDefaultPhoneNumber();
    }
    
    /**
     * Get the default phone number as LiveData
     * 
     * @return LiveData containing the default phone number
     */
    public LiveData<TwilioPhoneNumber> getDefaultPhoneNumberLive() {
        return repository.getDefaultPhoneNumberLive();
    }
    
    /**
     * Set a phone number as the default
     * 
     * @param phoneNumber The phone number to set as default
     */
    public void setAsDefault(String phoneNumber) {
        repository.setAsDefault(phoneNumber);
    }
    
    /**
     * Update the nickname for a phone number
     * 
     * @param phoneNumber The phone number to update
     * @param nickname The new nickname
     */
    public void updateNickname(String phoneNumber, String nickname) {
        repository.updateNickname(phoneNumber, nickname);
    }
    
    /**
     * Insert a new phone number
     * 
     * @param phoneNumber The phone number to insert
     */
    public void insert(TwilioPhoneNumber phoneNumber) {
        repository.insert(phoneNumber);
    }
    
    /**
     * Delete a phone number
     * 
     * @param phoneNumber The phone number to delete
     */
    public void delete(TwilioPhoneNumber phoneNumber) {
        repository.delete(phoneNumber);
    }
    
    /**
     * Delete a phone number by its phone number string
     * 
     * @param phoneNumber The phone number string to delete
     */
    public void deleteByPhoneNumber(String phoneNumber) {
        repository.deleteByPhoneNumber(phoneNumber);
    }
    
    /**
     * Get a phone number by its phone number string
     * 
     * @param phoneNumber The phone number string to find
     * @return LiveData containing the phone number entity
     */
    public LiveData<TwilioPhoneNumber> getByPhoneNumber(String phoneNumber) {
        // Since the repository doesn't provide a LiveData version of this method,
        // we need to create one here for the UI to observe
        return new LiveData<TwilioPhoneNumber>() {
            @Override
            protected void onActive() {
                super.onActive();
                // When the LiveData becomes active, post the value from the repository
                TwilioPhoneNumber number = repository.getByPhoneNumber(phoneNumber);
                postValue(number);
            }
        };
    }
    
    /**
     * Sync phone numbers with Twilio API
     * 
     * @param callback Callback to notify when the operation is complete
     */
    public void syncPhoneNumbersWithTwilio(PhoneNumberRepository.SyncCallback callback) {
        repository.syncPhoneNumbersWithTwilio(callback);
    }
}
