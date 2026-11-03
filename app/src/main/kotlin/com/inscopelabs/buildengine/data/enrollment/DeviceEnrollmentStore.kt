package com.inscopelabs.buildengine.data.enrollment

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException

/**
 * Encrypted local index of enrolled device keys.
 *
 * This is operational metadata, not an authority source. Gateway-side enrolment and
 * signature verification remain authoritative. Every update is committed atomically;
 * a failed commit is surfaced rather than reported as success.
 */
class DeviceEnrollmentStore(
    context: Context? = null,
    preferences: SharedPreferences? = null,
) {
    constructor(context: Context) : this(context, null)

    private val preferences: SharedPreferences = preferences
        ?: createPreferences(checkNotNull(context) { "Context must be provided if preferences is null" }.applicationContext)

    data class Record(
        val kid: String,
        val keyAlias: String,
        val publicKeyBase64: String,
        val status: Status,
        val createdAtEpochSeconds: Long,
        val retiredAtEpochSeconds: Long? = null,
        val authTier: String? = null,
    )

    enum class Status { PENDING, ACTIVE, RETIRED }

    @Synchronized
    fun recordPending(
        kid: String,
        keyAlias: String,
        publicKeyBase64: String,
        now: Long,
        authTier: String? = null,
    ) {
        validateKid(kid)
        validateAlias(keyAlias)
        require(publicKeyBase64.isNotBlank())
        val encoded = encode(Record(kid, keyAlias, publicKeyBase64, Status.PENDING, now, authTier = authTier))
        check(preferences.edit().putString(recordKey(kid), encoded).commit()) {
            "Unable to persist pending device enrolment"
        }
    }

    @Synchronized
    fun activate(kid: String, now: Long) {
        val target = record(kid) ?: error("Pending enrolment does not exist")
        check(target.status == Status.PENDING || target.status == Status.ACTIVE) {
            "Only a pending enrolment can be activated"
        }
        val editor = preferences.edit()
        activeKid()?.takeIf { it != kid }?.let { oldKid ->
            record(oldKid)?.let { old ->
                editor.putString(recordKey(oldKid), encode(old.copy(status = Status.RETIRED, retiredAtEpochSeconds = now)))
            }
        }
        editor.putString(recordKey(kid), encode(target.copy(status = Status.ACTIVE)))
        editor.putString(KEY_ACTIVE_KID, kid)
        check(editor.commit()) { "Unable to commit active device enrolment" }
    }

    @Synchronized
    fun removePending(kid: String) {
        val record = record(kid) ?: return
        check(record.status != Status.ACTIVE) { "Cannot remove the active enrolment as pending" }
        check(preferences.edit().remove(recordKey(kid)).commit()) { "Unable to remove pending enrolment" }
    }

    fun activeRecord(): Record? = activeKid()?.let(::record)?.takeIf { it.status == Status.ACTIVE }

    fun record(kid: String): Record? {
        validateKid(kid)
        return preferences.getString(recordKey(kid), null)?.let(::decode)
    }

    fun enrollmentFor(kid: String): String? = record(kid)?.publicKeyBase64
    fun keyAliasFor(kid: String): String? = record(kid)?.keyAlias
    fun authTierFor(kid: String): String? = record(kid)?.authTier
    fun activeKid(): String? = preferences.getString(KEY_ACTIVE_KID, null)

    @Synchronized
    fun clear() {
        check(preferences.edit().clear().commit()) { "Unable to clear device enrolment metadata" }
    }

    private fun encode(record: Record): String = listOf(
        record.kid, record.keyAlias, record.publicKeyBase64, record.status.name,
        record.createdAtEpochSeconds.toString(), record.retiredAtEpochSeconds?.toString().orEmpty(),
        record.authTier.orEmpty(),
    ).joinToString("\n")

    private fun decode(value: String): Record {
        val parts = value.split('\n')
        require(parts.size >= 6) { "Corrupt device enrolment record" }
        validateKid(parts[0]); validateAlias(parts[1])
        val status = Status.valueOf(parts[3])
        val retiredAt = parts[5].takeIf(String::isNotBlank)?.toLong()
        val authTier = if (parts.size >= 7) parts[6].takeIf(String::isNotBlank) else null
        return Record(parts[0], parts[1], parts[2], status, parts[4].toLong(), retiredAt, authTier)
    }

    private fun recordKey(kid: String) = "enrolment.$kid"
    private fun validateKid(kid: String) {
        require(kid.matches(Regex("[A-Za-z0-9_]{1,16}"))) { "Invalid gateway key id" }
    }
    private fun validateAlias(alias: String) {
        require(alias.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "Invalid Keystore alias" }
    }

    private companion object {
        const val FILE_NAME = "device_enrolment_secure"
        const val KEY_ACTIVE_KID = "active_kid"

        fun createPreferences(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context, FILE_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
