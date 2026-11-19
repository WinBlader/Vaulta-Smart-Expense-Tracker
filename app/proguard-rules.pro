# ============================================================================
# ProGuard / R8 Configuration for Vaulta (com.waleed.vaulta.android)
# ============================================================================

# Preserve line numbers for stack trace de-obfuscation in Play Console
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Room Database ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public abstract <methods>;
}

# --- Dagger / Hilt ---
-keep class * extends dagger.hilt.**
-keep class *_HiltModules* { *; }
-keep class dagger.hilt.** { *; }
-keep interface dagger.hilt.** { *; }
-dontwarn dagger.hilt.**

# --- Kotlinx Serialization ---
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class **$Companion {
    public kotlinx.serialization.KSerializer serializer();
}
-keepclasseswithmembers class * {
    public static final **$Companion Companion;
}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# --- Kotlin Coroutines ---
-keepclassmembernames class kotlinx.coroutines.internal.MainDispatcherFactory {
    java.lang.String getLoadPriority();
    kotlinx.coroutines.MainCoroutineDispatcher createDispatcher(java.util.List);
}
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

# --- MPAndroidChart ---
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# --- Supabase & Ktor & OkHttp ---
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Google Sign-In & Credential Manager ---
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# --- Application Models & DAOs ---
-keep class com.waleed.vaulta.android.data.model.** { *; }
-keep class com.waleed.vaulta.android.data.dao.** { *; }
-keep class com.waleed.vaulta.android.data.auth.supabase.** { *; }
