package com.phynix.artham.utils;

import com.phynix.artham.models.Users;

/**
 * In-memory session cache that survives across activity switches within a single app session.
 * Prevents UI flicker by allowing activities to instantly display cached data
 * instead of showing skeleton loaders while re-fetching from Firebase.
 *
 * Cleared on sign-out. NOT persisted to disk — only lives for the current app session.
 */
public class SessionCache {

    private static final SessionCache INSTANCE = new SessionCache();

    // User profile
    private Users cachedUserProfile;
    private boolean userProfileLoaded = false;

    // Cashbook name
    private String cachedCashbookName;
    private String cachedCashbookId;

    private SessionCache() { }

    public static SessionCache getInstance() {
        return INSTANCE;
    }

    // ── User Profile ──────────────────────────────────────

    public void cacheUserProfile(Users user) {
        this.cachedUserProfile = user;
        this.userProfileLoaded = true;
    }

    public Users getCachedUserProfile() {
        return cachedUserProfile;
    }

    public boolean hasUserProfile() {
        return userProfileLoaded;
    }

    // ── Cashbook Name ─────────────────────────────────────

    public void cacheCashbookName(String cashbookId, String name) {
        this.cachedCashbookId = cashbookId;
        this.cachedCashbookName = name;
    }

    public String getCachedCashbookName(String cashbookId) {
        if (cashbookId != null && cashbookId.equals(cachedCashbookId)) {
            return cachedCashbookName;
        }
        return null;
    }

    // ── Clear ─────────────────────────────────────────────

    public void clear() {
        cachedUserProfile = null;
        userProfileLoaded = false;
        cachedCashbookName = null;
        cachedCashbookId = null;
    }
}
