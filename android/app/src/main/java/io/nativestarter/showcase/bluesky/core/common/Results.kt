package io.nativestarter.showcase.bluesky.core.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * `runCatching` for suspend code. Cancellation is not a failure: it is rethrown so the coroutine stops
 * and no error state is shown for a screen the user already left.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
