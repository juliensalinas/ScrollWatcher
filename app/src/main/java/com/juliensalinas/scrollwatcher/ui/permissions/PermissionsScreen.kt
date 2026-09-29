package com.juliensalinas.scrollwatcher.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.juliensalinas.scrollwatcher.tracking.PermissionHelper

@Composable
fun PermissionsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var usageOk by remember { mutableStateOf(PermissionHelper.hasUsageAccess(context)) }
    var overlayOk by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
    var notifOk by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }

    fun refresh() {
        usageOk = PermissionHelper.hasUsageAccess(context)
        overlayOk = PermissionHelper.hasOverlayPermission(context)
        notifOk = PermissionHelper.hasNotificationPermission(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "ScrollWatcher needs these special settings. No Accessibility Service is used.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 16.dp),
        )

        PermissionCard(
            title = "Usage access",
            description = "Lets ScrollWatcher see which app is in the foreground via UsageStatsManager (not Accessibility).",
            granted = usageOk,
            actionLabel = "Open Usage Access settings",
            onAction = {
                context.startActivity(PermissionHelper.usageAccessSettingsIntent())
            },
        )

        Spacer(Modifier.height(12.dp))

        PermissionCard(
            title = "Display over other apps",
            description = "Shows a full-screen lock overlay when your daily scroll budget is used up.",
            granted = overlayOk,
            actionLabel = "Open Overlay settings",
            onAction = {
                context.startActivity(PermissionHelper.overlaySettingsIntent(context))
            },
        )

        Spacer(Modifier.height(12.dp))

        PermissionCard(
            title = "Notifications",
            description = "Required on Android 13+ for the persistent monitoring notification.",
            granted = notifOk,
            actionLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                "Request notification permission"
            } else {
                "Granted on this Android version"
            },
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            actionEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifOk,
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Tip: after granting Usage Access, return here — the checklist refreshes on resume.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = !granted,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (granted) "Granted" else "Missing",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Button(
                onClick = onAction,
                enabled = actionEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(actionLabel)
            }
        }
    }
}
