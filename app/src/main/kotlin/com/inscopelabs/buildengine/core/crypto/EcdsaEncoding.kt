package com.inscopelabs.buildengine.core.crypto

import java.io.ByteArrayOutputStream

/**
 * Transcoding between ASN.1 DER ECDSA signatures (Android Keystore output)
 * and raw 64-byte IEEE P1363 (r || s) signatures (Cloudflare Workers / WebCrypto).
 *
 * Design review ADR 0005: Cloudflare WebCrypto requires raw 64-byte r || s base64url.
 */
object EcdsaEncoding {

    private const val COMPONENT_LEN = 32
    private const val RAW_SIG_LEN = 64

    /**
     * Converts ASN.1 DER signature bytes into raw 64-byte IEEE P1363 (r || s).
     */
    fun derToP1363(der: ByteArray): ByteArray {
        require(der.size >= 8) { "DER signature too short: ${der.size} bytes" }
        require(der[0] == 0x30.toByte()) { "Expected ASN.1 SEQUENCE (0x30), got 0x${"%02x".format(der[0])}" }

        var offset = 1
        val seqLen = der[offset].toInt() and 0xFF
        offset++
        if (seqLen and 0x80 != 0) {
            val numBytes = seqLen and 0x7F
            offset += numBytes
        }

        // Parse r
        require(offset < der.size && der[offset] == 0x02.toByte()) { "Expected ASN.1 INTEGER (0x02) for r" }
        offset++
        val rLen = der[offset].toInt() and 0xFF
        offset++
        require(offset + rLen <= der.size) { "Truncated r component" }
        val rBytes = der.copyOfRange(offset, offset + rLen)
        offset += rLen

        // Parse s
        require(offset < der.size && der[offset] == 0x02.toByte()) { "Expected ASN.1 INTEGER (0x02) for s" }
        offset++
        val sLen = der[offset].toInt() and 0xFF
        offset++
        require(offset + sLen <= der.size) { "Truncated s component" }
        val sBytes = der.copyOfRange(offset, offset + sLen)

        val raw = ByteArray(RAW_SIG_LEN)
        normalizeInteger(rBytes, raw, 0)
        normalizeInteger(sBytes, raw, COMPONENT_LEN)
        return raw
    }

    /**
     * Converts raw 64-byte IEEE P1363 (r || s) into ASN.1 DER signature bytes.
     */
    fun p1363ToDer(raw: ByteArray): ByteArray {
        require(raw.size == RAW_SIG_LEN) { "Raw signature must be exactly 64 bytes, got ${raw.size}" }
        val r = raw.copyOfRange(0, COMPONENT_LEN)
        val s = raw.copyOfRange(COMPONENT_LEN, RAW_SIG_LEN)

        val rDer = integerToDer(r)
        val sDer = integerToDer(s)

        val totalLen = rDer.size + sDer.size
        val out = ByteArrayOutputStream()
        out.write(0x30)
        if (totalLen < 128) {
            out.write(totalLen)
        } else {
            out.write(0x81)
            out.write(totalLen)
        }
        out.write(rDer)
        out.write(sDer)
        return out.toByteArray()
    }

    fun derToP1363Base64Url(der: ByteArray): String = Base64Url.encode(derToP1363(der))

    fun p1363Base64UrlToDer(p1363Base64Url: String): ByteArray = p1363ToDer(Base64Url.decode(p1363Base64Url))

    private fun normalizeInteger(src: ByteArray, dest: ByteArray, destOffset: Int) {
        var start = 0
        while (start < src.size - 1 && src[start] == 0x00.toByte()) {
            start++
        }
        val len = src.size - start
        require(len <= COMPONENT_LEN) { "Integer component exceeds $COMPONENT_LEN bytes: $len" }
        val pad = COMPONENT_LEN - len
        System.arraycopy(src, start, dest, destOffset + pad, len)
    }

    private fun integerToDer(value: ByteArray): ByteArray {
        var start = 0
        while (start < value.size - 1 && value[start] == 0x00.toByte()) {
            start++
        }
        val needsZeroPad = (value[start].toInt() and 0x80) != 0
        val len = value.size - start + (if (needsZeroPad) 1 else 0)

        val out = ByteArray(2 + len)
        out[0] = 0x02
        out[1] = len.toByte()
        var offset = 2
        if (needsZeroPad) {
            out[offset++] = 0x00
        }
        System.arraycopy(value, start, out, offset, value.size - start)
        return out
    }
}
