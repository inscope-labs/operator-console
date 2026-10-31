package com.inscopelabs.buildengine.data.repository

import com.inscopelabs.buildengine.core.network.ErrorMapper
import com.inscopelabs.buildengine.core.network.GatewayFailureState
import com.inscopelabs.buildengine.core.network.GatewayHttpException
import com.inscopelabs.buildengine.core.network.GatewayProtocolException
import com.inscopelabs.buildengine.core.result.AppResult

/**
 * Calls the gateway and turns its refusals into the failure the domain sees.
 *
 * The transport's exceptions do not escape the data layer. A `GatewayHttpException`
 * is the gateway's answer and a `GatewayProtocolException` is an answer the console
 * could not read, and both are outcomes rather than accidents — so they become an
 * [AppResult.Failure] here, where the error catalogue is known, rather than
 * exceptions the domain has to catch and interpret.
 *
 * The mapping is the [ErrorMapper]'s, so a refusal that arrives here arrives with
 * the same state code it would have had anywhere else in the app: a drifted head
 * is `head_drifted` here and it is `head_drifted` on the screen.
 *
 * The one thing this does not do is swallow the reason. The failure carries the
 * gateway's own code in its message, so the operator is told *why* the gateway
 * refused and not only that it did.
 */
internal suspend fun <T> callGateway(
    errorMapper: ErrorMapper,
    block: suspend () -> T,
): AppResult<T> = try {
    AppResult.Success(block())
} catch (error: NotFoundException) {
    notFound(error.message ?: "the gateway has no such resource")
} catch (error: GatewayHttpException) {
    errorMapper.map(error.statusCode, error.body)
} catch (error: GatewayProtocolException) {
    errorMapper.mapProtocolFailure(error.message ?: "the gateway's answer could not be read")
}

/**
 * A resource the gateway does not have.
 *
 * It is neither a refusal nor a protocol failure: the gateway answered perfectly
 * well, and what it answered was "there is no such thing". It gets its own state,
 * `not_found`, so the screen can say "no such plan" instead of "something went
 * wrong".
 */
internal class NotFoundException(message: String) : Exception(message)

/**
 * The failure for a resource the gateway does not have.
 */
internal fun notFound(message: String): AppResult.Failure =
    AppResult.Failure(code = GatewayFailureState.NOT_FOUND.code, message = message)
