package com.cheshire.wallpaperswitcher.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cheshire.wallpaperswitcher.R
import com.cheshire.wallpaperswitcher.BuildConfig
import androidx.compose.ui.res.stringResource

/**
 * Dialog showing app information and folder statistics.
 */
@Composable
fun InformationDialog(
    totalImages: Int,
    availableSeenCount: Int,
    totalSeenCount: Int,
    availableFavoritesCount: Int,
    totalFavoritesCount: Int,
    availableToRemoveCount: Int,
    totalToRemoveCount: Int,
    folderUri: Uri?,
    onResetSeen: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName ?: "Unknown"
    val appName = stringResource(R.string.app_name)
    val gitHash = BuildConfig.GIT_HASH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Information") },
        text = {
            Column {
                Text(appName, style = MaterialTheme.typography.titleLarge)
                Text("Version $versionName ($gitHash)", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Total Images in Folder: $totalImages")
                Text("Seen: $availableSeenCount available ($totalSeenCount total)")
                Text("New (Unseen): ${totalImages - availableSeenCount}")
                Text("Favorites: $availableFavoritesCount available ($totalFavoritesCount total)")
                Text("To Remove: $availableToRemoveCount available ($totalToRemoveCount total)")
                Spacer(modifier = Modifier.height(16.dp))

                Text("Folder Path:", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = folderUri?.let { Uri.decode(it.toString()) } ?: "None",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onResetSeen,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Seen History")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
