package com.emailclient.presentation.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

/**
 * Displays a profile image with fallback to default icon
 */
@Composable
fun ProfileImage(
    imageUri: String?,
    contentDescription: String?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (imageUri.isNullOrBlank()) {
            // Show default icon if no image
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(size)
            )
        } else {
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(Uri.parse(imageUri))
                    .crossfade(true)
                    .build()
            )

            when (painter.state) {
                is AsyncImagePainter.State.Success -> {
                    Image(
                        painter = painter,
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(size)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                is AsyncImagePainter.State.Error -> {
                    // Fallback to default icon on error
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = contentDescription,
                        tint = iconTint,
                        modifier = Modifier.size(size)
                    )
                }
                else -> {
                    // Loading or empty state - show icon
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = contentDescription,
                        tint = iconTint,
                        modifier = Modifier.size(size)
                    )
                }
            }
        }
    }
}
