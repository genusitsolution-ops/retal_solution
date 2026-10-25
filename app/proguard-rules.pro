# kotlinx.serialization — keep generated serializers of the :core models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.genusit.prms.core.model.**$$serializer { *; }
-keepclassmembers class com.genusit.prms.core.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.genusit.prms.core.util.Flex* { *; }
