package io.nativestarter.showcase.bluesky.feature.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.nativestarter.showcase.bluesky.core.network.BskyRepository
import io.nativestarter.showcase.bluesky.core.network.PostInteractions
import io.nativestarter.showcase.bluesky.core.network.PostView
import io.nativestarter.showcase.bluesky.core.network.ThreadNode
import io.nativestarter.showcase.bluesky.core.network.withShadow

data class ThreadUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val parents: List<PostView> = emptyList(),
    val anchor: PostView? = null,
    val replies: List<PostView> = emptyList(),
    val error: String? = null,
)

/** Flattens `getPostThread` the way the RN linear thread view does: parents, anchor, then replies depth-first. */
object ThreadFlattener {
    fun parents(node: ThreadNode): List<PostView> {
        val out = ArrayList<PostView>()
        var p = node.parent
        while (p?.post != null) {
            out += p.post
            p = p.parent
        }
        return out.reversed()
    }

    fun replies(node: ThreadNode): List<PostView> {
        val out = ArrayList<PostView>()
        fun walk(n: ThreadNode) {
            n.replies.forEach { child ->
                child.post?.let { out += it }
                walk(child)
            }
        }
        walk(node)
        return out
    }
}

class ThreadViewModel(
    private val uri: String,
    private val repository: BskyRepository,
    private val interactions: PostInteractions,
) : ViewModel() {
    private val raw = MutableStateFlow(ThreadUiState())

    val state: StateFlow<ThreadUiState> = combine(raw, interactions.shadows) { s, shadows ->
        s.copy(
            parents = s.parents.map { it.withShadow(shadows) },
            anchor = s.anchor?.withShadow(shadows),
            replies = s.replies.map { it.withShadow(shadows) },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ThreadUiState())

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    private fun load(refresh: Boolean) {
        raw.update { it.copy(refreshing = refresh, error = null) }
        viewModelScope.launch {
            runCatching { repository.postThread(uri) }
                .onSuccess { node ->
                    raw.value = ThreadUiState(
                        loading = false,
                        parents = ThreadFlattener.parents(node),
                        anchor = node.post,
                        replies = ThreadFlattener.replies(node),
                        error = if (node.post == null) "Post not found" else null,
                    )
                }
                .onFailure { e -> raw.update { it.copy(loading = false, refreshing = false, error = e.message) } }
        }
    }

    fun toggleLike(post: PostView) {
        viewModelScope.launch { interactions.toggleLike(post) }
    }

    fun toggleRepost(post: PostView) {
        viewModelScope.launch { interactions.toggleRepost(post) }
    }
}
