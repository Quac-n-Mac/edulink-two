-keep class dev.edulinktwo.RustBridge { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
# kotlinx-serialization generated serializers
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class dev.edulinktwo.** { *; }
