package com.example.twiliovoiceapp.repository;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.twiliovoiceapp.database.AppDatabase;
import com.example.twiliovoiceapp.database.PhoneNumberDao;
import com.example.twiliovoiceapp.model.TwilioPhoneNumber;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Repository for managing Twilio phone numbers.
 * This class abstracts the data sources (local database and Twilio API)
 * and provides a clean API for the rest of the app to work with phone numbers.
 */
public class PhoneNumberRepository {
    private static final String TAG = "PhoneNumberRepository";
    private static final String TWILIO_API_BASE_URL = "https://api.twilio.com/2010-04-01/Accounts/";

    private final PhoneNumberDao phoneNumberDao;
    private final LiveData<List<TwilioPhoneNumber>> allPhoneNumbers;
    private final ExecutorService executorService;
    private final Application application;
    private final OkHttpClient httpClient;

    /**
     * Constructor for the repository
     * 
     * @param application The application context
     */
    public PhoneNumberRepository(Application application) {
        this.application = application;
        AppDatabase database = AppDatabase.getDatabase(application);
        phoneNumberDao = database.phoneNumberDao();
        allPhoneNumbers = phoneNumberDao.getAllPhoneNumbers();
        executorService = Executors.newFixedThreadPool(4);
        httpClient = new OkHttpClient();
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
        return phoneNumberDao.getDefaultPhoneNumber();
    }

    /**
     * Get the default phone number as LiveData
     * 
     * @return LiveData containing the default phone number
     */
    public LiveData<TwilioPhoneNumber> getDefaultPhoneNumberLive() {
        return phoneNumberDao.getDefaultPhoneNumberLive();
    }

    /**
     * Set a phone number as the default
     * 
     * @param phoneNumber The phone number to set as default
     */
    public void setAsDefault(String phoneNumber) {
        executorService.execute(() -> phoneNumberDao.setAsDefault(phoneNumber));
    }

    /**
     * Update the nickname for a phone number
     * 
     * @param phoneNumber The phone number to update
     * @param nickname The new nickname
     */
    public void updateNickname(String phoneNumber, String nickname) {
        executorService.execute(() -> phoneNumberDao.updateNickname(phoneNumber, nickname));
    }

    /**
     * Insert a new phone number
     * 
     * @param phoneNumber The phone number to insert
     */
    public void insert(TwilioPhoneNumber phoneNumber) {
        executorService.execute(() -> phoneNumberDao.insert(phoneNumber));
    }

    /**
     * Delete a phone number
     * 
     * @param phoneNumber The phone number to delete
     */
    public void delete(TwilioPhoneNumber phoneNumber) {
        executorService.execute(() -> phoneNumberDao.delete(phoneNumber));
    }

    /**
     * Delete a phone number by its phone number string
     * 
     * @param phoneNumber The phone number string to delete
     */
    public void deleteByPhoneNumber(String phoneNumber) {
        executorService.execute(() -> phoneNumberDao.deleteByPhoneNumber(phoneNumber));
    }

    /**
     * Check if a phone number exists in the database
     * 
     * @param phoneNumber The phone number to check
     * @return true if the phone number exists, false otherwise
     */
    public boolean exists(String phoneNumber) {
        return phoneNumberDao.exists(phoneNumber);
    }

    /**
     * Get a phone number by its phone number string
     * 
     * @param phoneNumber The phone number string to find
     * @return The phone number entity, or null if not found
     */
    public TwilioPhoneNumber getByPhoneNumber(String phoneNumber) {
        return phoneNumberDao.getByPhoneNumber(phoneNumber);
    }

    /**
     * Count the total number of phone numbers in the database
     * 
     * @return The count of phone numbers
     */
    public int count() {
        return phoneNumberDao.count();
    }

    /**
     * Fetch phone numbers from Twilio API and sync with local database
     * 
     * @param callback Callback to notify when the operation is complete
     */
    public void syncPhoneNumbersWithTwilio(final SyncCallback callback) {
        executorService.execute(() -> {
            try {
                // Get Twilio credentials from preferences
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(application);
                String accountSid = prefs.getString("account_sid", "");
                String authToken = prefs.getString("auth_token", "");

                if (accountSid.isEmpty() || authToken.isEmpty()) {
                    callback.onError("Twilio credentials not set");
                    return;
                }

                // Build API URL
                String apiUrl = TWILIO_API_BASE_URL + accountSid + "/IncomingPhoneNumbers.json";

                // Build request with Basic Auth
                Request request = new Request.Builder()
                        .url(apiUrl)
                        .header("Authorization", Credentials.basic(accountSid, authToken))
                        .build();

                // Execute request
                Response response = httpClient.newCall(request).execute();
                if (!response.isSuccessful()) {
                    callback.onError("API request failed: " + response.code());
                    return;
                }

                // Parse response
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);
                JSONArray incomingPhoneNumbers = jsonResponse.getJSONArray("incoming_phone_numbers");

                // Convert to TwilioPhoneNumber objects
                List<TwilioPhoneNumber> phoneNumbers = new ArrayList<>();
                for (int i = 0; i < incomingPhoneNumbers.length(); i++) {
                    JSONObject phoneNumberJson = incomingPhoneNumbers.getJSONObject(i);
                    String phoneNumber = phoneNumberJson.getString("phone_number");
                    String friendlyName = phoneNumberJson.getString("friendly_name");
                    String sid = phoneNumberJson.getString("sid");

                    // Check if this phone number already exists in the database
                    TwilioPhoneNumber existingNumber = phoneNumberDao.getByPhoneNumber(phoneNumber);
                    if (existingNumber != null) {
                        // Update existing number's Twilio-provided fields
                        existingNumber.setFriendlyName(friendlyName);
                        existingNumber.setSid(sid);
                        phoneNumbers.add(existingNumber);
                    } else {
                        // Create new number
                        phoneNumbers.add(new TwilioPhoneNumber(phoneNumber, friendlyName, sid));
                    }
                }

                // Update database
                phoneNumberDao.insertAll(phoneNumbers);

                // Set first number as default if no default exists
                if (phoneNumberDao.getDefaultPhoneNumber() == null && !phoneNumbers.isEmpty()) {
                    phoneNumberDao.setAsDefault(phoneNumbers.get(0).getPhoneNumber());
                }

                callback.onSuccess(phoneNumbers.size());
            } catch (IOException e) {
                Log.e(TAG, "Network error fetching phone numbers", e);
                callback.onError("Network error: " + e.getMessage());
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing Twilio API response", e);
                callback.onError("Error parsing API response: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error syncing phone numbers", e);
                callback.onError("Unexpected error: " + e.getMessage());
            }
        });
    }

    /**
     * Interface for sync operation callbacks
     */
    public interface SyncCallback {
        void onSuccess(int count);
        void onError(String errorMessage);
    }
}
