# R8 rules for the release build. Most libraries (kotlinx-serialization, Ktor, Supabase, Room, Compose)
# ship their own rules; what's here covers the gaps.

# Readable stack traces in Play's crash reports, once the mapping file is uploaded with the bundle.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Ktor looks up optional classes it never uses on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

# ML Kit (the on-device model behind Insights) creates its registrars by name at startup.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.mlkit.**.*Registrar { <init>(); }
