package com.inscopelabs.buildengine.core.network

import com.inscopelabs.buildengine.core.result.AppResult

/**
 * Maps the gateway's error catalogue onto UI states.
 *
 * SCAFFOLD PLACEHOLDER — placeholder only, implementation pending.
 *
 * Design review §12, Appendix B.3: the V5-V8 rejections (scope mismatch, replayed
 * capability, drifted head, active halt) must surface clearly and separately.
 */
class ErrorMapper {

    fun map(statusCode: Int, body: String?): AppResult.Failure =
        TODO("SCAFFOLD: map a gateway error onto a failure state.")

    fun mapStatusCode(statusCode: Int): String =
        TODO("SCAFFOLD: map a status code onto a stable error code.")
}
