# operator-xconsole — R8 / ProGuard rules
#
# The capability codec is a wire format: its field order and its base64url framing are
# part of the contract with the gateway (design review §5). Keep the model classes
# intact so a refactor cannot silently change the encoding.

-keep class com.inscopelabs.buildengine.capability.model.** { *; }
-keep class com.inscopelabs.buildengine.capability.codec.** { *; }

# kotlinx.serialization keeps generated serializers on the companion; R8 must not
# rename the serial names.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.inscopelabs.buildengine.core.network.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.inscopelabs.buildengine.core.network.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Nothing in this app may emit a capability signature or nonce to a log sink.
# The redaction gate in util/Redactor.kt is the primary control; these keeps stop
# R8 from inlining constants that would bypass it.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
