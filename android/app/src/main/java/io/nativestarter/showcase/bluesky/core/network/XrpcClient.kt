package io.nativestarter.showcase.bluesky.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

val XrpcJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** An XRPC call that returned a non-2xx status. [error] is the lexicon error name, for example `ExpiredToken`. */
class XrpcException(val status: Int, val error: String?, message: String?) :
    IOException("XRPC $status ${error ?: ""}: ${message ?: ""}")

/**
 * Minimal XRPC transport over OkHttp.
 *
 * Mirrors the RN split in `src/state/session/clients.ts`: `app.bsky.*` reads go through the PDS with
 * `atproto-proxy: <appview did>#bsky_appview`; `com.atproto.*` calls go to the PDS with no proxy header.
 */
class XrpcClient(
    private val serviceUrl: String,
    private val appviewProxy: String,
    private val http: OkHttpClient = OkHttpClient(),
) {
    enum class Target { Pds, Appview }

    suspend fun query(
        nsid: String,
        params: Map<String, String?> = emptyMap(),
        accessJwt: String?,
        target: Target = Target.Appview,
    ): String = withContext(Dispatchers.IO) {
        val url = "$serviceUrl/xrpc/$nsid".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> if (v != null) addQueryParameter(k, v) }
        }.build()
        execute(Request.Builder().url(url).get(), accessJwt, target)
    }

    /** Query whose parameter repeats, for example `getFeedGenerators?feeds=a&feeds=b`. */
    suspend fun queryRepeated(
        nsid: String,
        name: String,
        values: List<String>,
        accessJwt: String?,
        target: Target = Target.Appview,
    ): String = withContext(Dispatchers.IO) {
        val url = "$serviceUrl/xrpc/$nsid".toHttpUrl().newBuilder().apply {
            values.forEach { addQueryParameter(name, it) }
        }.build()
        execute(Request.Builder().url(url).get(), accessJwt, target)
    }

    suspend fun procedure(
        nsid: String,
        body: JsonObject,
        accessJwt: String?,
        target: Target = Target.Pds,
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$serviceUrl/xrpc/$nsid")
            .post(body.toString().toRequestBody(JSON))
        execute(request, accessJwt, target)
    }

    private fun execute(builder: Request.Builder, accessJwt: String?, target: Target): String {
        if (accessJwt != null) builder.header("Authorization", "Bearer $accessJwt")
        if (target == Target.Appview) builder.header("atproto-proxy", appviewProxy)
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val obj = runCatching { XrpcJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
                throw XrpcException(
                    response.code,
                    obj?.get("error")?.toString()?.trim('"'),
                    obj?.get("message")?.toString()?.trim('"'),
                )
            }
            return text
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        fun createSessionBody(identifier: String, password: String): JsonObject = buildJsonObject {
            put("identifier", identifier)
            put("password", password)
        }
    }
}
