package com.example.twiliovoiceapp.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.twiliovoiceapp.model.TwilioPhoneNumber;

import java.util.List;

/**
 * Data Access Object (DAO) for TwilioPhoneNumber entities.
 * Defines database operations for phone numbers including CRUD operations
 * and specialized queries for default number management.
 */
@Dao
public interface PhoneNumberDao {

    /**
     * Insert a new phone number into the database.
     * If a phone number with the same ID already exists, it will be replaced.
     * 
     * @param phoneNumber The phone number to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TwilioPhoneNumber phoneNumber);

    /**
     * Insert multiple phone numbers into the database.
     * 
     * @param phoneNumbers The list of phone numbers to insert
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<TwilioPhoneNumber> phoneNumbers);

    /**
     * Update an existing phone number in the database.
     * 
     * @param phoneNumber The phone number to update
     */
    @Update
    void update(TwilioPhoneNumber phoneNumber);

    /**
     * Delete a phone number from the database.
     * 
     * @param phoneNumber The phone number to delete
     */
    @Delete
    void delete(TwilioPhoneNumber phoneNumber);

    /**
     * Delete a phone number by its phone number string.
     * 
     * @param phoneNumber The phone number string to delete
     */
    @Query("DELETE FROM twilio_phone_numbers WHERE phoneNumber = :phoneNumber")
    void deleteByPhoneNumber(String phoneNumber);

    /**
     * Get all phone numbers from the database.
     * 
     * @return LiveData list of all phone numbers
     */
    @Query("SELECT * FROM twilio_phone_numbers ORDER BY isDefault DESC, nickname ASC, phoneNumber ASC")
    LiveData<List<TwilioPhoneNumber>> getAllPhoneNumbers();

    /**
     * Get all phone numbers as a regular list (non-LiveData).
     * 
     * @return List of all phone numbers
     */
    @Query("SELECT * FROM twilio_phone_numbers ORDER BY isDefault DESC, nickname ASC, phoneNumber ASC")
    List<TwilioPhoneNumber> getAllPhoneNumbersList();

    /**
     * Get the default phone number.
     * 
     * @return The default phone number, or null if none is set
     */
    @Query("SELECT * FROM twilio_phone_numbers WHERE isDefault = 1 LIMIT 1")
    TwilioPhoneNumber getDefaultPhoneNumber();

    /**
     * Get the default phone number as LiveData.
     * 
     * @return LiveData containing the default phone number
     */
    @Query("SELECT * FROM twilio_phone_numbers WHERE isDefault = 1 LIMIT 1")
    LiveData<TwilioPhoneNumber> getDefaultPhoneNumberLive();

    /**
     * Check if a phone number exists in the database.
     * 
     * @param phoneNumber The phone number to check
     * @return true if the phone number exists, false otherwise
     */
    @Query("SELECT COUNT(*) FROM twilio_phone_numbers WHERE phoneNumber = :phoneNumber")
    boolean exists(String phoneNumber);

    /**
     * Get a phone number by its phone number string.
     * 
     * @param phoneNumber The phone number string to find
     * @return The phone number entity, or null if not found
     */
    @Query("SELECT * FROM twilio_phone_numbers WHERE phoneNumber = :phoneNumber LIMIT 1")
    TwilioPhoneNumber getByPhoneNumber(String phoneNumber);

    /**
     * Set a phone number as the default number.
     * This will unset any previous default number.
     * 
     * @param phoneNumber The phone number to set as default
     */
    @Transaction
    default void setAsDefault(String phoneNumber) {
        // First, unset any existing default
        clearDefaultFlag();
        
        // Then set the new default
        setDefaultFlag(phoneNumber, true);
    }

    /**
     * Clear the default flag for all phone numbers.
     */
    @Query("UPDATE twilio_phone_numbers SET isDefault = 0")
    void clearDefaultFlag();

    /**
     * Set the default flag for a specific phone number.
     * 
     * @param phoneNumber The phone number to update
     * @param isDefault The default flag value
     */
    @Query("UPDATE twilio_phone_numbers SET isDefault = :isDefault WHERE phoneNumber = :phoneNumber")
    void setDefaultFlag(String phoneNumber, boolean isDefault);

    /**
     * Update the nickname for a phone number.
     * 
     * @param phoneNumber The phone number to update
     * @param nickname The new nickname
     */
    @Query("UPDATE twilio_phone_numbers SET nickname = :nickname WHERE phoneNumber = :phoneNumber")
    void updateNickname(String phoneNumber, String nickname);

    /**
     * Count the total number of phone numbers in the database.
     * 
     * @return The count of phone numbers
     */
    @Query("SELECT COUNT(*) FROM twilio_phone_numbers")
    int count();
}
