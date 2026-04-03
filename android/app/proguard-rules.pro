-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-dontwarn okhttp3.**
-dontwarn org.jetbrains.annotations.**

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer { *; }
