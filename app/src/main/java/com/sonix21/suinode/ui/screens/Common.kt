package com.sonix21.suinode.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.data.Panels
import com.sonix21.suinode.data.PanelSession
import com.sonix21.suinode.ui.glass.BusyOverlay
import com.sonix21.suinode.ui.glass.EmptyState
import com.sonix21.suinode.ui.glass.GlassTopBar
import com.sonix21.suinode.ui.glass.IconGhostButton
import com.sonix21.suinode.ui.glass.JsonEditSignal
import com.sonix21.suinode.ui.glass.PrimaryButton
import com.sonix21.suinode.ui.glass.ToastBus
import com.sonix21.suinode.ui.nav.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Session accessors for screens. */
@Composable
fun useSession(): PanelSession {
    JsonEditSignal.revision // Re-evaluate parent conditions after any JSON control changes.
    val session by Panels.currentSession.collectAsState()
    return requireNotNull(session) { "Select a panel before opening management screens" }
}

class Runner(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun go(label: String = "working…", onError: Boolean = true, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message?.take(240) ?: "The request could not be completed."
                if (onError) ToastBus.show(error!!)
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun rememberRunner(): Runner {
    val scope = rememberCoroutineScope()
    return remember { Runner(scope) }
}

/** Standard page scaffold: compact header actions and full-height content. */
@Composable
fun PageScaffold(
    title: String,
    nav: NavController? = null,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    busy: Boolean = false,
    primaryAction: (@Composable () -> Unit)? = null,
    draftValue: (() -> Any?)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    // Observe lossless JSONObject editor mutations and refresh conditional UI.
    val jsonRevision = JsonEditSignal.revision
    val currentDraft = androidx.compose.runtime.rememberUpdatedState(draftValue)
    val checkpoint = remember(nav, title, draftValue != null) {
        if (draftValue != null) com.sonix21.suinode.core.DraftCheckpoint { currentDraft.value?.invoke() } else null
    }
    androidx.compose.runtime.DisposableEffect(nav, checkpoint) {
        if (checkpoint != null) {
            nav?.draft = checkpoint
            com.sonix21.suinode.core.DraftRegistry.register(checkpoint)
        }
        onDispose {
            if (nav?.draft === checkpoint) nav?.draft = null
            if (checkpoint != null) com.sonix21.suinode.core.DraftRegistry.remove(checkpoint)
        }
    }
    var confirmLocalBack by remember { mutableStateOf(false) }
    val protectedLocalBack: (() -> Unit)? = onBack?.let { back -> {
        if (checkpoint?.changed() == true) confirmLocalBack = true else back()
    } }
    androidx.activity.compose.BackHandler(enabled = protectedLocalBack != null && checkpoint != null) { protectedLocalBack?.invoke() }
    if (confirmLocalBack) com.sonix21.suinode.ui.glass.ConfirmDialog(
        "Discard unsaved changes?", "These edits have not been saved. Leave this page and discard the draft?",
        confirmText = "Discard", danger = true,
        onConfirm = { checkpoint?.accept(); onBack?.invoke() }, onDismiss = { confirmLocalBack = false })
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
            GlassTopBar(title, subtitle, protectedLocalBack ?: nav?.takeIf { it.stack.size > 1 }?.let { controller -> { controller.pop() } }) {
                actions?.invoke(this)
                primaryAction?.invoke()
            }
            if (checkpoint?.changed() == true) androidx.compose.material3.Text(
                "Unsaved changes", color = com.sonix21.suinode.ui.glass.LocalGlass.current.teal,
                modifier = Modifier.padding(bottom = 8.dp))
            Column(Modifier.weight(1f).fillMaxWidth().then(if (jsonRevision >= 0) Modifier else Modifier), content = content)
            Spacer(Modifier.height(10.dp))
        }
        BusyOverlay(busy)
    }
}

/** Scrollable list container with empty-state handling. */
@Composable
fun <T> ItemsScroll(
    items: List<T>,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String? = null,
    emptyAction: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    itemSpacing: Int = 12,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty() && header == null) {
        EmptyState(emptyIcon, emptyTitle, emptySubtitle, emptyAction)
        return
    }
    androidx.compose.foundation.lazy.LazyColumn(
        verticalArrangement = Arrangement.spacedBy(itemSpacing.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        header?.let { item(key = "header") { Box(Modifier.fillMaxWidth()) { it() } } }
        items(count = items.size, key = { i ->
            when (val v = items[i]) {
                is org.json.JSONObject ->
                    if (v.has("id") && v.optLong("id") >= 0) "obj-${v.optLong("id")}" else "tag-${v.optString("tag").ifBlank { i.toString() }}"
                else -> "i$i"
            }
        }) { i ->
            Box(Modifier.animateItem(
                fadeInSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                placementSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            )) { itemContent(items[i]) }
        }
        item(key = "tail") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun RefreshActionButton(onRefresh: suspend () -> Unit) {
    val runner = rememberRunner()
    IconGhostButton(Icons.Filled.Refresh, { runner.go { onRefresh() } })
}

@Composable
fun SaveBar(text: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
        PrimaryButton(text, enabled = enabled, loading = loading, onClick = onClick)
    }
}

/** Keeps navigation and retry available while an editor waits for its full APIv2 record. */
@Composable
fun RecordLoadingPage(title: String, nav: NavController, runner: Runner, onRetry: () -> Unit) {
    PageScaffold(title = title, nav = nav) {
        com.sonix21.suinode.ui.glass.RequestState(
            title = if (runner.error == null) "Opening $title" else "Couldn’t open $title",
            message = "Fetching the latest details from your panel.",
            error = runner.error,
            onRetry = onRetry,
        )
    }
}


/** Read-only loads follow the selected filters; cancelling a stale GET never cancels a save. */
class ReadRequest<T> {
    var value by mutableStateOf<T?>(null)
        internal set
    var busy by mutableStateOf(true)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    internal var attempt by mutableStateOf(0)
    fun refresh() { attempt++ }
}

@Composable
fun <T> rememberReadRequest(vararg keys: Any?, read: suspend () -> T): ReadRequest<T> {
    val request = remember(*keys) { ReadRequest<T>() }
    androidx.compose.runtime.LaunchedEffect(*keys, request.attempt) {
        request.busy = true
        request.error = null
        try {
            request.value = read()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            request.error = e.message?.take(240) ?: "The request could not be completed."
        } finally {
            request.busy = false
        }
    }
    return request
}

@Composable
fun ReadError(request: ReadRequest<*>) {
    request.error?.let { message ->
        com.sonix21.suinode.ui.glass.GlassCard(Modifier.fillMaxWidth()) {
            androidx.compose.material3.Text(message, color = com.sonix21.suinode.ui.glass.LocalGlass.current.err)
            com.sonix21.suinode.ui.glass.GhostButton("Try again", enabled = !request.busy) { request.refresh() }
        }
    }
}
