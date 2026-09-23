package com.sonix21.suinode.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import com.sonix21.suinode.R
import com.sonix21.suinode.ui.glass.glassSurface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.fragment.app.FragmentActivity
import com.sonix21.suinode.APP
import com.sonix21.suinode.MainActivity
import com.sonix21.suinode.core.AppThemeMode
import com.sonix21.suinode.core.UiLocale
import com.sonix21.suinode.data.Panels
import com.sonix21.suinode.data.PanelSession
import com.sonix21.suinode.data.StatusStore
import com.sonix21.suinode.ui.glass.GlassTopBar
import com.sonix21.suinode.ui.glass.LocalGlass
import com.sonix21.suinode.ui.glass.StatusDot
import com.sonix21.suinode.ui.glass.ToastHost
import com.sonix21.suinode.ui.nav.NavController
import com.sonix21.suinode.ui.nav.Route
import com.sonix21.suinode.ui.screens.screenFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun AppRoot(requestThemeRefresh: () -> Unit) {
    val context = LocalContext.current
    val panels by Panels.list.collectAsState()
    val activeId by Panels.activeId.collectAsState()

    val activity = androidx.activity.compose.LocalActivity.current as MainActivity
    val unlocked = activity.access.unlocked
    val storageReady by Panels.ready.collectAsState()
    val storageError by Panels.storageError.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(unlocked) {
        if (unlocked) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Panels.unlock() }
    }
    LaunchedEffect(unlocked, storageReady) { if (unlocked && storageReady) activity.documents.resumeAfterUnlock() }

    if (!unlocked) {
        LockGate(onUnlocked = { activity.access.authenticated() }, context = context)
        return
    }

    if (!storageReady) {
        com.sonix21.suinode.ui.glass.RequestState(
            title = if (storageError == null) "Opening secure vault" else "Secure vault unavailable",
            message = "Decrypting your saved connections on this device.",
            error = storageError,
            onRetry = { scope.launch { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Panels.unlock() } } },
        )
        ToastHost()
        return
    }

    if (panels.isEmpty()) {
        com.sonix21.suinode.ui.screens.panels.PanelsScreen(nav = null)
        ToastHost()
        return
    }

    val session by Panels.currentSession.collectAsState()
    session?.let { current -> androidx.compose.runtime.key(current) { MainShell(current, requestThemeRefresh) } }
    com.sonix21.suinode.ui.screens.settings.SaveReviewHost()
    ToastHost()
}

@Composable
private fun LockGate(onUnlocked: () -> Unit, context: android.content.Context) {
    val activity = androidx.activity.compose.LocalActivity.current as? FragmentActivity
    val g = LocalGlass.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusDot(g.violet, 14.dp)
            androidx.compose.material3.Text("Locked", color = g.textDim, fontSize = 15.sp)
            com.sonix21.suinode.ui.screens.settings.PinUnlockPanel(onUnlocked) { activity?.let { com.sonix21.suinode.runBiometricGate(it, onUnlocked, { error -> com.sonix21.suinode.ui.glass.ToastBus.show(error) }) } }
            com.sonix21.suinode.ui.glass.GhostButton("Device security settings") {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
            }
        }
    }
    ToastHost()
}

@Composable
private fun MainShell(session: PanelSession, requestThemeRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    val stack = remember { androidx.compose.runtime.snapshots.SnapshotStateList<Route>().apply { add(Route.Home) } }
    val nav = remember(stack) { NavController(stack) }
    val screenState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val rootTabs = setOf(Route.Home, Route.Clients, Route.Inbounds, Route.Tools)
    val data by session.data.collectAsState()
    val syncError by session.syncError.collectAsState()
    val syncing by session.syncing.collectAsState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(session, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                session.load()
                StatusStore.poll(session)
                delay(APP.prefs.refreshIntervalSec.coerceIn(2, 60) * 1000L)
            }
        }
    }

    BackHandler(enabled = stack.size > 1) { nav.pop() }
    if (nav.pendingNavigation.value != null) com.sonix21.suinode.ui.glass.ConfirmDialog(
        "Discard unsaved changes?", "Your changes have not been saved. Leave this page and discard the draft?",
        confirmText = "Discard", danger = true, onConfirm = { nav.discardAndContinue() },
        onDismiss = { nav.pendingNavigation.value = null })

    val isTopLevel = stack.size == 1

    Column(Modifier.fillMaxSize()) {
        val g = LocalGlass.current
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_launcher_monochrome), null, tint = g.teal, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { nav.push(Route.Panels) }
                .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f, fill = false)) {
                    Text("S-UI NODE", color = g.textFaint, fontSize = 8.sp, letterSpacing = 1.5.sp)
                    Text(session.panel.name, color = g.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ExpandMore, contentDescription = "Switch panel", tint = g.textFaint, modifier = Modifier.size(20.dp))
            }
            StatusDot(if (syncError != null) g.err else g.teal, 6.dp)
            Spacer(Modifier.width(6.dp))
            Text(if (syncError != null) "Offline" else if (!data.loaded) "Connecting" else "Connected",
                color = g.textFaint, fontSize = 10.sp)
            com.sonix21.suinode.ui.glass.IconGhostButton(Icons.Rounded.Settings, { nav.push(Route.AppSettings) }, contentDesc = "App settings")
        }
        if (syncError != null && data.loaded) com.sonix21.suinode.ui.glass.GlassCard(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
            Text(syncError.orEmpty(), color = LocalGlass.current.err, fontSize = 12.sp)
            com.sonix21.suinode.ui.glass.GhostButton("Retry", enabled = !syncing) { scope.launch { session.load(force = true) } }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!data.loaded && nav.current !in setOf(Route.Panels, Route.AppSettings, Route.ConnectionDiagnostics, Route.Monitoring, Route.AlertHistory, Route.Renewals) && nav.current !is Route.PanelEditor && nav.current !is Route.PendingSave && nav.current !is Route.VpsQuota && nav.current !is Route.VpsRenewal && nav.current !is Route.OfflineOverview) {
                com.sonix21.suinode.ui.glass.RequestState(
                    title = if (syncError == null) "Connecting to your panel" else "Panel unavailable",
                    message = "Syncing your clients, inbounds and configuration through APIv2.",
                    error = syncError,
                    onRetry = { if (!syncing) scope.launch { session.load(force = true) } },
                    secondaryAction = { com.sonix21.suinode.ui.glass.GhostButton("Manage panels") { nav.push(Route.Panels) } },
                )
            } else AnimatedContent(
                targetState = stack.toList(),
                contentKey = { it.last().toString() },
                transitionSpec = {
                    val forward = targetState.size >= initialState.size
                    (slideInHorizontally(tween(250)) { if (forward) it / 12 else -it / 12 } + fadeIn(tween(220)))
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "nav",
            ) { st ->
                val route = st.last()
                val routeKey = route.toString()
                androidx.compose.runtime.DisposableEffect(routeKey) {
                    onDispose {
                        if (route !in stack && route !in rootTabs) screenState.removeState(routeKey)
                    }
                }
                screenState.SaveableStateProvider(routeKey) {
                    Box(Modifier.fillMaxSize()) {
                        screenFor(route, nav, requestThemeRefresh)
                    }
                }
            }
        }
        if (isTopLevel) BottomNav(nav)
    }
}

private data class TabItem(val route: Route, val icon: ImageVector, val label: String)

@Composable
private fun BottomNav(nav: NavController) {
    val g = LocalGlass.current
    val tabs = listOf(
        TabItem(Route.Home, Icons.Outlined.Home, "Overview"),
        TabItem(Route.Clients, Icons.Outlined.Group, "Clients"),
        TabItem(Route.Inbounds, Icons.Outlined.SettingsInputAntenna, "Inbounds"),
        TabItem(Route.Tools, Icons.Outlined.GridView, "Tools"),
    )
    NavigationBar(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp).glassSurface(corner = 28.dp),
        containerColor = Color.Transparent, tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        tabs.forEach { t ->
            NavigationBarItem(
                selected = nav.stack.firstOrNull() == t.route, onClick = { nav.tab(t.route) },
                icon = { Icon(t.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                label = { Text(UiLocale.text(t.label), fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = g.teal, selectedTextColor = g.text,
                    indicatorColor = g.teal.copy(alpha = 0.14f),
                    unselectedIconColor = g.textFaint, unselectedTextColor = g.textFaint,
                ),
            )
        }
    }
}
