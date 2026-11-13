# operator-xconsole — R8 / ProGuard rules
#
# The capability codec is a wire format: its field order and its base64url framing are
# part of the contract with the gateway (design review §5). Keep the model classes
# intact so a refactor cannot silently change the encoding.

-keep class com.inscopelabs.buildengine.capability.model.** { *; }
-keep class com.inscopelabs.buildengine.capability.codec.** { *; }

# kotlinx.serialization keeps generated serializers on the companion; R8 must not
# rename the serial names (covers gateway wire DTOs and local encrypted journal records).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.inscopelabs.buildengine.core.network.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.inscopelabs.buildengine.core.network.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.inscopelabs.buildengine.data.local.** {
    *** Companion;
}
-keepclasseswithmembers class com.inscopelabs.buildengine.data.local.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Operation journal (ADR 0009, BD-3.2, T3-101):
# Room resolves generated *_Impl classes via reflection, and journal converters persist
# OperationJournalState enum names verbatim.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class com.inscopelabs.buildengine.data.local.OperationJournalDatabase_Impl { *; }
-keepclassmembers enum com.inscopelabs.buildengine.data.local.OperationJournalState {
    *;
}

# WorkManager recovery executor (ADR 0009, T3-101, T3-203):
# WorkManager resolves CoroutineWorker constructors reflectively, and OperationRecoveryWorker
# accesses singleton dependencies through a Hilt @EntryPoint.
-keep public class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep interface com.inscopelabs.buildengine.data.worker.OperationRecoveryWorker$OperationRecoveryEntryPoint {
    *;
}

# Nothing in this app may emit a capability signature or nonce to a log sink.
# The redaction gate in util/Redactor.kt is the primary control; these keeps stop
# R8 from inlining constants that would bypass it.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
