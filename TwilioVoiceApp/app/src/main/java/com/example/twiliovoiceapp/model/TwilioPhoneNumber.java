package com.example.twiliovoiceapp.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity class representing a Twilio phone number.
 * This class stores information about phone numbers retrieved from the user's Twilio account,
 * including user-assigned nicknames and default status.
 */
@Entity(tableName = "twilio_phone_numbers")
public class TwilioPhoneNumber {

    @PrimaryKey
    @NonNull
    private String phoneNumber;
    
    private String nickname;
    private String friendlyName;
    private String sid;
    private boolean isDefault;

    /**
     * Default constructor required by Room
     */
    public TwilioPhoneNumber() {
    }

    /**
     * Constructor with essential fields
     * 
     * @param phoneNumber The phone number in E.164 format (e.g., +15551234567)
     * @param friendlyName The friendly name provided by Twilio
     * @param sid The Twilio SID for this phone number
     */
    public TwilioPhoneNumber(@NonNull String phoneNumber, String friendlyName, String sid) {
        this.phoneNumber = phoneNumber;
        this.friendlyName = friendlyName;
        this.sid = sid;
        this.isDefault = false;
        this.nickname = null;
    }

    /**
     * Full constructor
     * 
     * @param phoneNumber The phone number in E.164 format
     * @param nickname User-assigned nickname for this number
     * @param friendlyName The friendly name provided by Twilio
     * @param sid The Twilio SID for this phone number
     * @param isDefault Whether this is the default number for making calls
     */
    public TwilioPhoneNumber(@NonNull String phoneNumber, String nickname, String friendlyName, 
                            String sid, boolean isDefault) {
        this.phoneNumber = phoneNumber;
        this.nickname = nickname;
        this.friendlyName = friendlyName;
        this.sid = sid;
        this.isDefault = isDefault;
    }

    @NonNull
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(@NonNull String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    /**
     * Returns the display name for this phone number.
     * If a nickname is set, it returns the nickname.
     * Otherwise, it returns the phone number.
     * 
     * @return The display name for this phone number
     */
    public String getDisplayName() {
        return nickname != null && !nickname.isEmpty() ? nickname : phoneNumber;
    }

    /**
     * Returns a formatted representation of this phone number for display in UI.
     * Format: "Nickname (PhoneNumber)" or just "PhoneNumber" if no nickname is set.
     * 
     * @return The formatted display string
     */
    public String getFormattedDisplay() {
        if (nickname != null && !nickname.isEmpty()) {
            return nickname + " (" + phoneNumber + ")";
        }
        return phoneNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TwilioPhoneNumber that = (TwilioPhoneNumber) o;
        return phoneNumber.equals(that.phoneNumber);
    }

    @Override
    public int hashCode() {
        return phoneNumber.hashCode();
    }

    @Override
    public String toString() {
        return getFormattedDisplay();
    }
}
