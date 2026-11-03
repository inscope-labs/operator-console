package com.inscopelabs.buildengine.core.security

import androidx.biometric.BiometricManager
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthTierResolverTest {

    @Test
    fun `API 30 or higher with secure device resolves hardware_confirm even without biometrics`() {
        val tier = AuthTierResolver.resolve(
            sdkInt = 30,
            isDeviceSecure = true,
            biometricStatus = BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
        )
        assertEquals(AuthTier.HARDWARE_CONFIRM, tier)
    }

    @Test
    fun `API 30 or higher with secure device and biometric success resolves hardware_confirm`() {
        val tier = AuthTierResolver.resolve(
            sdkInt = 34,
            isDeviceSecure = true,
            biometricStatus = BiometricManager.BIOMETRIC_SUCCESS,
        )
        assertEquals(AuthTier.HARDWARE_CONFIRM, tier)
    }

    @Test
    fun `API 30 or higher without secure lock screen resolves in_app_confirm`() {
        val tier = AuthTierResolver.resolve(
            sdkInt = 30,
            isDeviceSecure = false,
            biometricStatus = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
        )
        assertEquals(AuthTier.IN_APP_CONFIRM, tier)
    }

    @Test
    fun `API 28 with biometric success resolves hardware_confirm`() {
        val tier = AuthTierResolver.resolve(
            sdkInt = 28,
            isDeviceSecure = true,
            biometricStatus = BiometricManager.BIOMETRIC_SUCCESS,
        )
        assertEquals(AuthTier.HARDWARE_CONFIRM, tier)
    }

    @Test
    fun `API 29 with biometric success resolves hardware_confirm`() {
        val tier = AuthTierResolver.resolve(
            sdkInt = 29,
            isDeviceSecure = true,
            biometricStatus = BiometricManager.BIOMETRIC_SUCCESS,
        )
        assertEquals(AuthTier.HARDWARE_CONFIRM, tier)
    }

    @Test
    fun `API 28 or 29 without biometric success resolves in_app_confirm`() {
        val tierNoHw = AuthTierResolver.resolve(
            sdkInt = 28,
            isDeviceSecure = true,
            biometricStatus = BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
        )
        assertEquals(AuthTier.IN_APP_CONFIRM, tierNoHw)

        val tierNoneEnrolled = AuthTierResolver.resolve(
            sdkInt = 29,
            isDeviceSecure = true,
            biometricStatus = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
        )
        assertEquals(AuthTier.IN_APP_CONFIRM, tierNoneEnrolled)
    }

    @Test
    fun `API below 28 resolves in_app_confirm`() {
        val tier = AuthTierResolver.resolve(
            sdkInt = 27,
            isDeviceSecure = true,
            biometricStatus = BiometricManager.BIOMETRIC_SUCCESS,
        )
        assertEquals(AuthTier.IN_APP_CONFIRM, tier)
    }
}
