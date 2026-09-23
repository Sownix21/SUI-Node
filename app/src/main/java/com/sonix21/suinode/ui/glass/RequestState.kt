package com.sonix21.suinode.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonix21.suinode.R

/** Shared request state. The parent owns the full available bounds, even before data arrives. */
@Composable
fun RequestState(
    title: String,
    message: String,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 340.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RequestCard(title, error ?: message, failed = error != null, onRetry = onRetry)
            secondaryAction?.let { Spacer(Modifier.height(16.dp)); it() }
        }
    }
}

@Composable
internal fun RequestCard(title: String, message: String, failed: Boolean = false, onRetry: (() -> Unit)? = null) {
    val g = LocalGlass.current
    Column(
        Modifier.widthIn(max = 340.dp).fillMaxWidth()
            .background(g.surface.copy(alpha = 0.96f), MaterialTheme.shapes.extraLarge)
            .glassSurface(corner = 28.dp).padding(28.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(64.dp).background(g.teal.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) {
            if (failed) Icon(Icons.Outlined.CloudOff, null, tint = g.err, modifier = Modifier.size(28.dp))
            else {
                CircularProgressIndicator(modifier = Modifier.size(64.dp), color = g.teal,
                    trackColor = g.teal.copy(alpha = 0.08f), strokeWidth = 2.dp)
                Icon(painterResource(R.drawable.ic_launcher_monochrome), null, tint = g.teal, modifier = Modifier.size(30.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = g.text, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodySmall, color = g.textDim, textAlign = TextAlign.Center)
        if (failed && onRetry != null) PrimaryButton("Try again", onClick = onRetry)
    }
}
