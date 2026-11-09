# kotlinx.serialization: keep generated serializers of the API models
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.genusit.prms.admin.**$$serializer { *; }
-keepclassmembers class com.genusit.prms.admin.** { *** Companion; }
-keepclasseswithmembers class com.genusit.prms.admin.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Tink (used by EncryptedSharedPreferences) refers to compile-time-only annotations
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
