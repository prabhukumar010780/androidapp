# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# ============================================================
# Generic attributes required by reflection-based libs
# ============================================================
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes *Annotation*

# ============================================================
# Domain models + remote DTOs (Gson reflects on field names)
# ============================================================
-keep class com.destinyai.astrology.domain.model.** { *; }
-keep class com.destinyai.astrology.data.remote.dto.** { *; }
-keep class com.destinyai.astrology.data.remote.** { *; }
-keep class com.destinyai.astrology.data.local.entity.** { *; }

# Honor @SerializedName on any class
-keep @com.google.gson.annotations.SerializedName class *
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ============================================================
# Gson
# ============================================================
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements com.google.gson.JsonSerializer
-keep public class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Prevent stripping of generic type info used by TypeToken
-keepattributes Signature

# ============================================================
# Retrofit
# ============================================================
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Retrofit calls reflection on parameter types
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ============================================================
# OkHttp / Okio
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ============================================================
# Kotlin / Coroutines
# ============================================================
-keepclassmembers class kotlinx.coroutines.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ============================================================
# Hilt / Dagger
# ============================================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keep class com.destinyai.astrology.DestinyApp { *; }
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep,allowobfuscation @interface dagger.hilt.InstallIn
-keep,allowobfuscation @interface javax.inject.Inject
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
}

# Hilt-generated classes
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class dagger.hilt.internal.aggregatedroot.codegen.** { *; }
-keep class dagger.hilt.internal.processedrootsentinel.codegen.** { *; }

# ============================================================
# Room
# ============================================================
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep @androidx.room.Database class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# ============================================================
# Firebase / FCM
# ============================================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-keep class com.destinyai.astrology.services.DestinyFirebaseMessagingService { *; }
-keep class * extends com.google.firebase.messaging.FirebaseMessagingService

# ============================================================
# Google Play Billing
# ============================================================
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# ============================================================
# Credential Manager / Google Sign-In
# ============================================================
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# ============================================================
# AndroidX / Compose / Lifecycle
# ============================================================
-keep class androidx.lifecycle.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep all ViewModel constructors injected by Hilt
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Compose runtime/compiler reflective access — keep public Composable surface
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.platform.** { *; }
-dontwarn androidx.compose.**

# DataStore (Preferences) reflection
-keep class androidx.datastore.*.** { *; }
-dontwarn androidx.datastore.**

# Security crypto (Tink-backed)
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
-dontwarn com.google.crypto.tink.**

# ============================================================
# Enum values (used by Gson + Retrofit converters)
# ============================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================
# Parcelable
# ============================================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ============================================================
# Native methods + Kotlin reflection metadata
# ============================================================
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class kotlin.reflect.** { *; }
