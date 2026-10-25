# kotlinx.serialization — keep generated serializers of the :domain models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.genusit.prms.domain.model.**$$serializer { *; }
-keepclassmembers class com.genusit.prms.domain.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.genusit.prms.domain.util.Flex* { *; }

# Retrofit (2.11 ships its own rules; these keep the API interface and its generic signatures under R8 full mode)
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep interface com.genusit.prms.data.remote.PrmsApiService { *; }
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
