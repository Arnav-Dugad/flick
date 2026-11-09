# Flick release shrinking rules.

# MediaPipe Tasks: native code calls back into these classes by name, and the task graphs are
# described with protobuf messages looked up reflectively.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**

# Flogger (MediaPipe's logger) finds its caller by walking the stack for its own class names;
# renaming them throws "no caller found on the stack" when a task is created.
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**

# Annotation-only dependencies referenced by MediaPipe and Guava; not needed at runtime.
-dontwarn javax.annotation.**
-dontwarn javax.lang.model.**
-dontwarn com.google.auto.value.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**

# Navigation 3 keys and stored settings are restored with kotlinx.serialization; the library's own
# consumer rules keep generated serializers, these keep our serializable types' companions.
-keepclassmembers @kotlinx.serialization.Serializable class io.github.arnavdugad.flick.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.arnavdugad.flick.**$$serializer { *; }

# Keep stack traces readable in crash reports users paste into issues.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
