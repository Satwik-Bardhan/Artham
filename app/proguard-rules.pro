# ===========================================================================
# Artham - Expenses Manager: ProGuard / R8 Rules for Release Builds
# ===========================================================================
# Libraries that ship their own consumer rules (no manual rules needed):
#   - Firebase (Auth, RTDB, Storage, Analytics, Crashlytics)
#   - Google Play Services (Auth, Location)
#   - Google Play In-App Review
#   - Glide 4.x
#   - ViewBinding (compile-time code gen, no reflection)
# ===========================================================================

# ===========================================================================
# GENERAL ANDROID / CRASHLYTICS
# ===========================================================================
# Keep source file names and line numbers for readable Crashlytics reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations and signatures (needed by Gson, Firebase, etc.)
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses,EnclosingMethod

# Keep custom exceptions readable in Crashlytics
-keep public class * extends java.lang.Exception

# ===========================================================================
# APP MODEL CLASSES (Firebase RTDB reflection + Gson serialization)
# These classes do NOT use @SerializedName — they rely on field name matching,
# so we must keep all fields and constructors.
# ===========================================================================
-keep class com.phynix.artham.models.** { *; }

# Inner classes used with Gson for local JSON serialization
-keep class com.phynix.artham.db.DataRepository$LocalDataWrapper { *; }
-keep class com.phynix.artham.utils.OfflineTransactionManager$PendingTransaction { *; }

# ===========================================================================
# GSON (v2.10.1 — does NOT ship full consumer rules)
# ===========================================================================
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Keep generic type info for TypeToken
-keep class * extends com.google.gson.reflect.TypeToken { *; }

# ===========================================================================
# MPAndroidChart (v3.1.0 — does NOT ship consumer rules)
# ===========================================================================
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ===========================================================================
# iTextPDF (5.5.13.3 — old library, no consumer rules)
# ===========================================================================
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ===========================================================================
# ColorPicker Libraries (precautionary — consumer rules unconfirmed)
# ===========================================================================
-keep class com.github.dhaval2404.colorpicker.** { *; }
-dontwarn com.github.dhaval2404.colorpicker.**
-keep class com.skydoves.colorpickerview.** { *; }
-dontwarn com.skydoves.colorpickerview.**

# ===========================================================================
# Facebook Shimmer (archived library — precautionary)
# ===========================================================================
-keep class com.facebook.shimmer.** { *; }
-dontwarn com.facebook.shimmer.**

# ===========================================================================
# ANDROID WIDGETS
# ===========================================================================
-keep class com.phynix.artham.widget.** { *; }

# ===========================================================================
# JAVA SERIALIZABLE
# ===========================================================================
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}