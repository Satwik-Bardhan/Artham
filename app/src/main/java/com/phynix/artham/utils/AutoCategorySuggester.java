package com.phynix.artham.utils;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;

import androidx.annotation.Nullable;

import com.phynix.artham.models.CategoryModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Smart Auto-Category Suggester — analyzes entry remark text and suggests
 * the best matching category based on keyword matching.
 *
 * Keywords are loaded from two sources (merged together):
 *   1. Built-in hardcoded defaults (fallback)
 *   2. assets/category_keywords.json (user-editable, takes priority)
 *
 * Usage:
 *   AutoCategorySuggester.suggest("electric bill paid") → "Bills & Utility"
 *   AutoCategorySuggester.suggest("swiggy order")       → "Food & Dining"
 *   AutoCategorySuggester.suggest("random text")        → null (no match)
 */
public class AutoCategorySuggester {

    private static final String TAG = "AutoCategorySuggester";
    private static final String KEYWORDS_ASSET_FILE = "category_keywords.json";

    // Ordered map: category name → array of keywords
    // LinkedHashMap preserves insertion order for priority (more specific categories first)
    private static final LinkedHashMap<String, String[]> BUILTIN_KEYWORDS = new LinkedHashMap<>();

    // Merged keywords (built-in + JSON file) — populated on first use
    private static LinkedHashMap<String, String[]> mergedKeywords = null;
    private static boolean jsonLoaded = false;

    static {
        // --- Built-in defaults (fallback if JSON is unavailable) ---

        // Rental Income (check before "Rent" to avoid conflict)
        BUILTIN_KEYWORDS.put("Rental Income", new String[]{
                "rent income", "rental income", "tenant", "tenant payment", "rent received",
                "rent collection"
        });

        // Interest & Dividends
        BUILTIN_KEYWORDS.put("Interest & Dividends", new String[]{
                "interest", "dividend", "bank interest", "fd interest", "rd interest",
                "savings interest", "bond interest"
        });

        // Bills & Utility
        BUILTIN_KEYWORDS.put("Bills & Utility", new String[]{
                "ebill", "e-bill", "electric", "electricity", "power bill", "water bill",
                "gas bill", "wifi", "broadband", "internet bill", "recharge", "dth",
                "postpaid", "prepaid", "phone bill", "mobile bill", "utility", "utilities",
                "current bill", "bijli", "pani", "light bill", "telephone", "landline",
                "sewage", "garbage", "municipal", "bescom", "bses", "tata power",
                "adani electricity", "jio fiber", "airtel fiber", "act fibernet",
                "bill paid", "bill payment"
        });

        // Groceries
        BUILTIN_KEYWORDS.put("Groceries", new String[]{
                "grocery", "groceries", "vegetables", "veggies", "fruits", "milk", "eggs",
                "supermarket", "kirana", "bigbasket", "big basket", "blinkit", "zepto",
                "instamart", "dmart", "d-mart", "reliance fresh", "more supermarket",
                "sabzi", "ration", "provisions", "daily needs", "kitchen supplies",
                "cooking oil", "atta", "dal", "rice", "spices", "masala"
        });

        // Food & Dining
        BUILTIN_KEYWORDS.put("Food & Dining", new String[]{
                "food", "lunch", "dinner", "breakfast", "snack", "snacks", "biryani",
                "pizza", "burger", "zomato", "swiggy", "restaurant", "cafe", "coffee",
                "tea", "dosa", "idli", "meal", "tiffin", "canteen", "mess", "dhaba",
                "thali", "noodles", "momos", "sandwich", "juice", "milkshake",
                "ice cream", "dessert", "bakery", "cake", "starbucks", "dominos",
                "mcdonalds", "kfc", "subway", "chaiwala", "eating out", "dining",
                "takeaway", "delivery", "brunch"
        });

        // Transport
        BUILTIN_KEYWORDS.put("Transport", new String[]{
                "uber", "ola", "cab", "taxi", "auto", "rickshaw", "bus", "metro",
                "train", "petrol", "diesel", "fuel", "parking", "toll", "fastag",
                "rapido", "bike taxi", "local train", "railway", "irctc", "commute",
                "transport", "transportation", "car wash", "servicing", "car service",
                "bike service", "tyre", "puncture", "cng"
        });

        // Travel
        BUILTIN_KEYWORDS.put("Travel", new String[]{
                "flight", "hotel", "trip", "travel", "holiday", "vacation", "booking",
                "airbnb", "makemytrip", "goibibo", "oyo", "resort", "tourism", "tour",
                "passport", "visa", "luggage", "suitcase", "cleartrip", "yatra",
                "ixigo", "indigo", "air india", "spicejet", "vistara", "homestay"
        });

        // Rent
        BUILTIN_KEYWORDS.put("Rent", new String[]{
                "rent", "house rent", "room rent", "pg", "hostel", "maintenance",
                "society maintenance", "flat rent", "apartment rent", "rent paid",
                "monthly rent", "paying guest"
        });

        // Subscriptions
        BUILTIN_KEYWORDS.put("Subscriptions", new String[]{
                "netflix", "hotstar", "disney", "prime video", "amazon prime",
                "spotify", "youtube premium", "subscription", "jio", "airtel",
                "membership", "apple music", "gaana", "wynk", "zee5", "sonyliv",
                "hbo", "crunchyroll", "linkedin premium", "icloud", "google one",
                "cloud storage", "vpn", "antivirus", "microsoft 365", "adobe",
                "canva pro", "chatgpt", "premium plan", "annual plan", "monthly plan"
        });

        // Shopping
        BUILTIN_KEYWORDS.put("Shopping", new String[]{
                "amazon", "flipkart", "myntra", "shopping", "clothes", "shoes",
                "dress", "electronics", "gadget", "mobile", "phone", "laptop",
                "meesho", "ajio", "nykaa", "tata cliq", "croma", "reliance digital",
                "watch", "jewellery", "jewelry", "accessories", "bag", "purse",
                "cosmetics", "makeup", "grooming", "perfume", "appliance",
                "furniture", "home decor", "curtains", "bedsheet", "kitchen appliance"
        });

        // Entertainment
        BUILTIN_KEYWORDS.put("Entertainment", new String[]{
                "movie", "cinema", "theatre", "theater", "game", "gaming", "concert",
                "party", "outing", "pvr", "inox", "carnival", "imax", "amusement",
                "theme park", "water park", "event", "show", "standup", "comedy",
                "sports", "match", "stadium", "bowling", "arcade", "pub", "bar",
                "nightclub", "karaoke", "picnic"
        });

        // Health
        BUILTIN_KEYWORDS.put("Health", new String[]{
                "doctor", "hospital", "medicine", "pharmacy", "medical", "health",
                "checkup", "check up", "lab test", "blood test", "dental", "dentist",
                "eye", "optician", "glasses", "gym", "fitness", "yoga", "ayurveda",
                "homeopathy", "physiotherapy", "surgery", "operation", "clinic",
                "apollo", "medplus", "1mg", "pharmeasy", "netmeds", "practo",
                "therapy", "counseling", "wellness", "vitamin", "supplement"
        });

        // Education
        BUILTIN_KEYWORDS.put("Education", new String[]{
                "school", "college", "tuition", "course", "exam", "books", "fees",
                "coaching", "udemy", "skillshare", "coursera", "unacademy",
                "byjus", "education", "training", "workshop", "seminar", "webinar",
                "library", "stationery", "notebook", "pen", "pencil", "study",
                "scholarship", "admission", "university", "degree", "certificate"
        });

        // Salary
        BUILTIN_KEYWORDS.put("Salary", new String[]{
                "salary", "wages", "pay", "paycheck", "stipend", "monthly salary",
                "salary credited", "pay day", "payday", "income received"
        });

        // Freelance
        BUILTIN_KEYWORDS.put("Freelance", new String[]{
                "freelance", "project payment", "client payment", "gig", "consulting",
                "contract work", "side hustle", "upwork", "fiverr", "freelancing",
                "commission", "consultancy"
        });

        // Gifts & Charity
        BUILTIN_KEYWORDS.put("Gifts & Charity", new String[]{
                "donation", "charity", "temple", "church", "mosque", "gurudwara",
                "zakat", "tithe", "ngo", "fundraiser", "crowdfunding", "helping",
                "orphanage", "old age home", "daan", "seva"
        });

        // Gifts (standalone)
        BUILTIN_KEYWORDS.put("Gifts", new String[]{
                "gift", "present", "birthday gift", "wedding gift", "anniversary gift",
                "surprise", "gift card", "bouquet", "flowers"
        });

        // Insurance
        BUILTIN_KEYWORDS.put("Insurance", new String[]{
                "insurance", "lic", "premium", "policy", "term plan", "health insurance",
                "car insurance", "bike insurance", "vehicle insurance", "life insurance",
                "mediclaim", "accidental", "endowment"
        });

        // Taxes
        BUILTIN_KEYWORDS.put("Taxes", new String[]{
                "tax", "gst", "income tax", "tds", "advance tax", "tax filing",
                "tax return", "itr", "property tax", "road tax", "professional tax",
                "tax paid", "tax payment"
        });

        // Investment
        BUILTIN_KEYWORDS.put("Investment", new String[]{
                "investment", "mutual fund", "sip", "stock", "shares", "fd",
                "fixed deposit", "ppf", "nps", "crypto", "bitcoin", "ethereum",
                "demat", "zerodha", "groww", "paytm money", "kuvera",
                "gold", "silver", "bond", "elss", "nifty", "sensex", "trading",
                "portfolio", "equity", "debt fund"
        });

        // Refunds
        BUILTIN_KEYWORDS.put("Refunds", new String[]{
                "refund", "cashback", "return", "reimbursement", "money back",
                "refund received", "claim", "reversal", "chargeback"
        });

        // Business
        BUILTIN_KEYWORDS.put("Business", new String[]{
                "business", "office", "supplies", "stationery", "printing", "courier",
                "postage", "stamp", "xerox", "photocopy", "visiting card",
                "business expense", "office expense", "coworking", "meeting"
        });

        // Business Revenue
        BUILTIN_KEYWORDS.put("Business Revenue", new String[]{
                "business revenue", "sales", "revenue", "business income",
                "client invoice", "invoice paid", "payment received"
        });
    }

    /**
     * Initialize the suggester by loading keywords from the JSON asset file.
     * Call this once during app startup (e.g., in Application.onCreate or first Activity).
     * Safe to call multiple times — only loads once.
     *
     * @param context Application or Activity context
     */
    public static void init(Context context) {
        if (jsonLoaded) return;
        loadKeywordsFromAssets(context);
    }

    /**
     * Loads keywords from the JSON asset file and merges them with built-in defaults.
     * JSON entries take priority — if a category exists in both, JSON keywords replace built-in ones.
     * New categories in JSON are added to the merged map.
     */
    private static synchronized void loadKeywordsFromAssets(Context context) {
        if (jsonLoaded) return;

        // Start with a copy of built-in keywords
        mergedKeywords = new LinkedHashMap<>(BUILTIN_KEYWORDS);

        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(context.getAssets().open(KEYWORDS_ASSET_FILE), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(sb.toString());
            Iterator<String> keys = json.keys();

            while (keys.hasNext()) {
                String categoryName = keys.next();

                // Skip the _README key
                if (categoryName.startsWith("_")) continue;

                JSONArray keywordsArray = json.optJSONArray(categoryName);
                if (keywordsArray != null && keywordsArray.length() > 0) {
                    String[] keywords = new String[keywordsArray.length()];
                    for (int i = 0; i < keywordsArray.length(); i++) {
                        keywords[i] = keywordsArray.getString(i);
                    }
                    // JSON entries override built-in ones for the same category name
                    mergedKeywords.put(categoryName, keywords);
                }
            }

            Log.d(TAG, "Loaded " + mergedKeywords.size() + " categories from JSON + built-in defaults");
        } catch (Exception e) {
            Log.w(TAG, "Could not load " + KEYWORDS_ASSET_FILE + ", using built-in defaults only: " + e.getMessage());
            // mergedKeywords already has built-in defaults, so this is safe
        }

        jsonLoaded = true;
    }

    /**
     * Get the active keyword map (merged or built-in fallback).
     */
    private static LinkedHashMap<String, String[]> getKeywords() {
        if (mergedKeywords != null) {
            return mergedKeywords;
        }
        // If init() was never called, fall back to built-in
        return BUILTIN_KEYWORDS;
    }

    /**
     * Analyze the given text and return the best matching category name.
     *
     * @param remarkText The remark/description text entered by the user
     * @return The category name if a match is found, or null if no match
     */
    @Nullable
    public static String suggest(String remarkText) {
        if (remarkText == null || remarkText.trim().isEmpty()) {
            return null;
        }

        String normalizedText = remarkText.trim().toLowerCase();

        // Track best match: category with most keyword hits wins
        String bestCategory = null;
        int bestScore = 0;

        for (Map.Entry<String, String[]> entry : getKeywords().entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (normalizedText.contains(keyword.toLowerCase())) {
                    // Longer keyword matches score higher (more specific = better)
                    score += keyword.length();
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestCategory = entry.getKey();
            }
        }

        return bestCategory;
    }

    /**
     * Get the CategoryModel (with color + icon) for a suggested category name.
     * Falls back to DefaultCategoryManager for icon and color resolution.
     *
     * @param categoryName The category name returned by suggest()
     * @return CategoryModel with name, color, and icon, or null
     */
    @Nullable
    public static CategoryModel getSuggestedCategoryModel(String categoryName) {
        if (categoryName == null) return null;
        return DefaultCategoryManager.getCategoryByName(categoryName);
    }

    /**
     * Creates a TextWatcher that auto-suggests categories as the user types.
     * The callback is invoked with the suggested category name (or null if no match).
     */
    public static TextWatcher createAutoSuggestWatcher(OnCategorySuggested callback) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                String suggested = suggest(text);
                if (callback != null) {
                    callback.onSuggested(suggested);
                }
            }
        };
    }

    /**
     * Force reload keywords from the JSON asset file.
     * Useful if the file has been updated at runtime (hot reload during development).
     *
     * @param context Application or Activity context
     */
    public static void reload(Context context) {
        jsonLoaded = false;
        mergedKeywords = null;
        init(context);
    }

    /**
     * Callback interface for auto-category suggestions.
     */
    public interface OnCategorySuggested {
        /**
         * Called when a category suggestion is available or cleared.
         *
         * @param categoryName The suggested category name, or null if no match found
         */
        void onSuggested(@Nullable String categoryName);
    }
}
