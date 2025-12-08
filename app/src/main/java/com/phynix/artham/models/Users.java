package com.phynix.artham.models;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Users {
    private String profile;
    private String mail;
    private String userName;
    private String userId;
    private String phoneNumber;
    private long dateOfBirthTimestamp;

    // Required Default Constructor for Firebase
    public Users() {
    }

    // Parameterized Constructor
    public Users(String userId, String userName, String mail) {
        this.userId = userId;
        // [FIX] Prevent NullPointerException if userName is null
        this.userName = (userName != null) ? userName : "User";
        this.mail = (mail != null) ? mail : "";
    }

    // Getters and Setters
    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }

    public String getMail() { return mail; }
    public void setMail(String mail) { this.mail = mail; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public long getDateOfBirthTimestamp() { return dateOfBirthTimestamp; }
    public void setDateOfBirthTimestamp(long dateOfBirthTimestamp) { this.dateOfBirthTimestamp = dateOfBirthTimestamp; }
}