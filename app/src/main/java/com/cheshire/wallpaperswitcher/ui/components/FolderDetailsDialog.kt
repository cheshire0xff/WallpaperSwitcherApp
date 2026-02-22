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
import androidx.compose.ui.unit.dp

/**
 * Dialog showing folder statistics and path details.
 */
@Composable
fun FolderDetailsDialog(
    totalImages: Int,
    seenCount: Int,
    favoritesCount: Int,
    toRemoveCount: Int,
    folderUri: Uri?,
    onResetSeen: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Folder Details") },
        text = {
            Column {
                Text("Total Images: $totalImages")
                Text("Seen: $seenCount")
                Text("New: ${totalImages - seenCount}")
                Text("Favorites: $favoritesCount")
                Text("To Remove: $toRemoveCount")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Path:", style = MaterialTheme.typography.labelLarge)
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
