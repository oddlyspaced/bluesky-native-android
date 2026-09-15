package io.nativestarter.showcase.bluesky.core.common

import io.nativestarter.showcase.bluesky.core.network.XrpcException
import java.io.IOException

/** User-facing error text. Ports of RN `cleanError` (src/lib/strings/errors.ts) and `useCleanError`. */
object ErrorText {
    const val NETWORK = "Unable to connect. Please check your internet connection and try again."
    const val SERVER = "The server appears to be experiencing issues. Please try again in a few moments."
    const val APP_PASSWORD = "This feature is not available while using an App Password. Please sign in with your main password."
    const val RATE_LIMIT = "You've reached the maximum number of requests allowed. Please try again later."

    private val NETWORK_MARKERS = listOf(
        "Abort",
        "Network request failed",
        "Failed to fetch",
        "fetch failed",
        "Load failed",
        "Upstream service unreachable",
        "NetworkError when attempting to fetch resource",
    )
    private val SERVER_MARKERS = listOf("Upstream Failure", "UpstreamFailure", "NotEnoughResources", "pipethrough network error")
    private val PASS_THROUGH = listOf("Account has been suspended", "Account is deactivated", "Profile not found", "Unable to resolve handle")

    /** RN `cleanError`. An I/O failure that is not an XRPC response is a network error on Android. */
    fun clean(error: Throwable?): String {
        if (error == null) return ""
        if (error is IOException && error !is XrpcException) return NETWORK
        val raw = when (error) {
            is XrpcException -> listOfNotNull(error.error, error.serverMessage).joinToString(" ")
            else -> error.message.orEmpty()
        }
        if (raw.isBlank()) return ""
        if (NETWORK_MARKERS.any { raw.contains(it) }) return NETWORK
        if (SERVER_MARKERS.any { raw.contains(it) }) return SERVER
        if (raw.contains("Bad token scope") || raw.contains("Bad token method")) return APP_PASSWORD
        PASS_THROUGH.firstOrNull { raw.contains(it) }?.let { return it }
        return when (error) {
            is XrpcException -> error.serverMessage?.takeIf { it.isNotBlank() } ?: error.error.orEmpty()
            else -> raw.removePrefix("Error: ")
        }
    }

    /** RN `useCleanError` (thread and other screens): network, server and rate-limit errors get fixed copy. */
    fun cleanForScreen(error: Throwable?): String {
        if (error is XrpcException && (error.status == 429 || error.error == "RateLimitExceeded")) return RATE_LIMIT
        return clean(error)
    }
}
