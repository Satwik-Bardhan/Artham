package com.phynix.artham.models;

public class Users {
    private String uid;
    private String name;
    private String email;
    private String phone;
    private String profile;
    private long dateOfBirthTimestamp;

    // Empty constructor required for Firebase
    public Users() { }

    public Users(String uid, String name, String email, String phone, String profile) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.profile = profile;
    }

    // --- Core Getters & Setters ---

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }

    public long getDateOfBirthTimestamp() { return dateOfBirthTimestamp; }
    public void setDateOfBirthTimestamp(long dateOfBirthTimestamp) { this.dateOfBirthTimestamp = dateOfBirthTimestamp; }

    // --- ALIAS METHODS (Fixes Compatibility Issues) ---
    // These allow your code to use getUserName() OR getName() without crashing.

    public String getUserName() { return name; }
    public void setUserName(String name) { this.name = name; }

    public String getPhoneNumber() { return phone; }
    public void setPhoneNumber(String phone) { this.phone = phone; }
}