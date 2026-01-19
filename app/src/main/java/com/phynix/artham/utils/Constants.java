package com.phynix.artham.utils;

public class Constants {

    // --- Firebase Nodes ---
    public static final String NODE_USERS = "users";
    public static final String NODE_CASHBOOKS = "cashbooks";
    public static final String NODE_TRANSACTIONS = "transactions";

    // --- Transaction Types ---
    public static final String TRANSACTION_TYPE_IN = "IN";
    public static final String TRANSACTION_TYPE_OUT = "OUT";

    // --- Intent Extras ---
    public static final String EXTRA_CASHBOOK_ID = "cashbook_id";
    public static final String EXTRA_TRANSACTION = "transaction_data";
    public static final String EXTRA_TRANSACTION_TYPE = "transaction_type";

    // --- SharedPreferences ---
    public static final String PREF_NAME = "AppPrefs";
    public static final String PREF_ACTIVE_CASHBOOK_PREFIX = "active_cashbook_id_";

    // --- Date Formats ---
    public static final String DATE_FORMAT_DISPLAY = "dd MMM yyyy";
    public static final String TIME_FORMAT_DISPLAY = "hh:mm a";
}