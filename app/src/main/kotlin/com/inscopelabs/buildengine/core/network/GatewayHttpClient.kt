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

/**
 * The HTTP transport to the gateway.
 *
 * Design review §8: every request must produce an audit record, but credentials,
 * signatures and nonces must never appear in a log line. See util/Redactor.kt.
 *
 * This class speaks HTTP and nothing else. It does not log, it does not parse the
 * body's JSON, and it does not decide what a refusal means — the body of a refusal
 * is handed on untouched so that the layer above can map it.
 *
 * ### Authentication
 *
 * The channel is HTTPS with host verification, and the console's identity on the
 * gateway is the `Authorization: Bearer <operator_credential>` header
 * (docs/gateway-contract.md §1.2). That credential is gateway-scoped and holds no
 * repository authority; a privileged operation additionally carries its capability
 * in the request body.
 *
 * mTLS is deferred (ADR 0006), so the certificate pinner and the client
 * certificate are not installed here. They belong on the Ktor engine in
 * `di/NetworkModule.kt`, so that one place owns the channel's posture and every
 * request inherits it. A per-request header set in the client would be a second
 * place to forget.
 *
 * ### Failures
 *
 * A non-2xx response is a refusal, and a refusal is not an exception in the
 * transport sense — it is the gateway's answer. It becomes a [GatewayHttpException],
 * carrying the status and the body, because the status alone does not say *why*
 * and the body is where the gateway says why.
 */
class GatewayHttpClient(
    private val client: HttpClient,
    private val baseUrl: String = GatewayEndpoints.BASE_URL,
    private val credentials: OperatorCredentialProvider = OperatorCredentialProvider { null },
) {

    suspend fun get(path: String): String = request("GET", path, body = null)

    suspend fun post(path: String, body: String): String = request("POST", path, body)

    /**
     * Performs a request against the gateway and returns the response body.
     *
     * @throws GatewayHttpException if the gateway refused with a non-2xx status.
     */
    suspend fun request(method: String, path: String, body: String?): String {
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
            throw GatewayHttpException(
                statusCode = response.status.value,
                body = payload,
                message = "the gateway refused $method $path with ${response.status.value}",
            )
        }
        return payload
    }
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
