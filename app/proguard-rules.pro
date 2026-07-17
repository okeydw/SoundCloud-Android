-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.scd.android.**$$serializer { *; }
-keepclassmembers class com.scd.android.** { *** Companion; }
-keepclasseswithmembers class com.scd.android.** { kotlinx.serialization.KSerializer serializer(...); }

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
