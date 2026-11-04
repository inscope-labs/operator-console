package com.inscopelabs.buildengine.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException
import java.net.UnknownHostException

/**
 * The HTTP transport to the gateway.
 *
 * Design review §8: every request must produce an audit record, but credentials,
 * signatures and nonces must never appear in a log line. See util/Redactor.kt.
 *
 * When a [GatewayActivityLog] is supplied, each exchange records method, path, and
 * outcome only — never tokens, capabilities, or bodies.
 */
class GatewayHttpClient(
    private val client: HttpClient,
    private val baseUrlProvider: GatewayBaseUrlProvider = GatewayBaseUrlProvider {
        GatewayEndpoints.BASE_URL
    },
    private val credentials: OperatorCredentialProvider = OperatorCredentialProvider { null },
    private val activityLog: GatewayActivityLog? = null,
) {

    suspend fun get(path: String): String = request("GET", path, body = null)

    suspend fun post(path: String, body: String): String = request("POST", path, body)

    /**
     * Performs a request against the gateway and returns the response body.
     *
     * @throws GatewayHttpException if the gateway refused with a non-2xx status.
     * @throws GatewayNotConfiguredException if the base URL is unset or a placeholder.
     */
    suspend fun request(method: String, path: String, body: String?): String {
        val baseUrl = baseUrlProvider.baseUrl()
        if (!GatewayEndpoints.isConfigured(baseUrl)) {
            record(method, path, GatewayActivityEvent.Outcome.NOT_CONFIGURED)
            throw GatewayNotConfiguredException(
                "the gateway base URL is not configured (host must not be a .invalid placeholder)",
            )
        }
        return try {
            val response = client.request(GatewayEndpoints.url(path, baseUrl)) {
                this.method = HttpMethod.parse(method)
                credentials.credential()?.let { credential ->
                    header(HttpHeaders.Authorization, "Bearer $credential")
                }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }

            val payload = response.bodyAsText()
            if (!response.status.isSuccess()) {
                record(
                    method,
                    path,
                    GatewayActivityEvent.Outcome.REFUSED,
                    statusCode = response.status.value,
                )
                throw GatewayHttpException(
                    statusCode = response.status.value,
                    body = payload,
                    message = "the gateway refused $method $path with ${response.status.value}",
                )
            }
            record(
                method,
                path,
                GatewayActivityEvent.Outcome.SUCCESS,
                statusCode = response.status.value,
            )
            payload
        } catch (error: GatewayNotConfiguredException) {
            throw error
        } catch (error: GatewayHttpException) {
            throw error
        } catch (error: UnknownHostException) {
            record(
                method,
                path,
                GatewayActivityEvent.Outcome.UNREACHABLE,
                detail = "host could not be resolved",
            )
            throw error
        } catch (error: IOException) {
            record(
                method,
                path,
                GatewayActivityEvent.Outcome.UNREACHABLE,
                detail = "transport failure",
            )
            throw error
        } catch (error: Exception) {
            record(
                method,
                path,
                GatewayActivityEvent.Outcome.ERROR,
                detail = error.javaClass.simpleName,
            )
            throw error
        }
    }

    private fun record(
        method: String,
        path: String,
        outcome: GatewayActivityEvent.Outcome,
        statusCode: Int? = null,
        detail: String? = null,
    ) {
        activityLog?.record(
            GatewayActivityEvent(
                method = method,
                path = path,
                outcome = outcome,
                statusCode = statusCode,
                detail = detail,
                epochMillis = System.currentTimeMillis(),
            ),
        )
    }
}

/**
 * Supplies the gateway base URL for each request.
 *
 * Resolved per call so Settings changes apply without process restart.
 */
fun interface GatewayBaseUrlProvider {
    fun baseUrl(): String
}

/**
 * Supplies the gateway-scoped operator credential.
 *
 * The credential lives in `data.local.OperatorCredentialStore`, which is Stage 3.1;
 * the transport only needs somewhere to ask for it, and must work without one —
 * `/health` is the one endpoint the contract allows to be called unauthenticated.
 */
fun interface OperatorCredentialProvider {
    /** The credential to present, or `null` when there is none. */
    fun credential(): String?
}

/**
 * Base for the failures the gateway surface can raise.
 */
open class GatewayException(message: String) : Exception(message)

/**
 * The configured base URL is a scaffold placeholder or empty.
 * No DNS lookup or socket is attempted.
 */
class GatewayNotConfiguredException(
    message: String = "the gateway base URL is not configured",
) : GatewayException(message)

/**
 * The gateway refused: a non-2xx response.
 *
 * The status and the body are kept, because the status is the coarse class and the
 * body is where the gateway states the reason.
 */
class GatewayHttpException(
    val statusCode: Int,
    val body: String?,
    message: String,
) : GatewayException(message)

/**
 * The gateway answered, but not with something the console understands.
 *
 * A malformed body is not a refusal: the gateway accepted the request and then
 * answered in a way the console cannot read. It is its own failure, because
 * "the gateway said no" and "the gateway said something else" call for different
 * operator reactions.
 */
class GatewayProtocolException(message: String) : GatewayException(message)
