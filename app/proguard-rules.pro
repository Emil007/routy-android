# kotlinx.serialization needs its generated serializer classes kept — see
# https://github.com/Kotlin/kotlinx.serialization/blob/master/rules/common.pro for the full
# upstream rule set if minification ever strips something unexpectedly.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.routy.app.**$$serializer { *; }
-keepclassmembers class com.routy.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.routy.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
