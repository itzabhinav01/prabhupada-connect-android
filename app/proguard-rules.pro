# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.prabhupadaconnect.vedabase.**$$serializer { *; }
-keepclassmembers class com.prabhupadaconnect.vedabase.** { *** Companion; }
-keepclasseswithmembers class com.prabhupadaconnect.vedabase.** { kotlinx.serialization.KSerializer serializer(...); }

# Our own Room entities / sync DTOs - keep field names so serialization and
# Room's generated binders never see a renamed/removed member at runtime.
-keep class com.prabhupadaconnect.vedabase.data.user.entity.** { *; }
-keep class com.prabhupadaconnect.vedabase.data.sync.** { *; }
-keep class com.prabhupadaconnect.vedabase.core.model.** { *; }

# Room - the library's own consumer rules cover most of this, but Requery's
# SQLite driver sits underneath Room here (not the platform's own SQLite),
# so keep generated DAO/database implementations and entity fields explicit.
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class *
-keep class **_Impl { *; }
-dontwarn androidx.room.paging.**

# Requery SQLite Android - JNI-backed native SQLite build; keep it whole so
# the bundled FTS5-capable driver isn't stripped or renamed under the hood.
-keep class io.requery.android.database.** { *; }
-dontwarn io.requery.android.database.**

# Hilt / Dagger - generated components are compile-time only, but keep the
# entry points and injected-constructor markers R8 can't otherwise trace.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep,allowobfuscation @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}
-dontwarn dagger.hilt.**

# Ktor - the client engine is discovered via ServiceLoader at runtime, and
# its multiplatform core relies on reflection for content negotiation.
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-keep class * implements io.ktor.client.HttpClientEngineContainer
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.debug.**

# androidx.security-crypto pulls in Google Tink, which references optional
# compile-time-only annotation/logging classes (error-prone, slf4j) that are
# never actually on the runtime classpath - R8 flags the reference, but
# nothing exercises it at runtime.
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.slf4j.**
