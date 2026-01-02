package com.smartmuseum.wallpaperapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.smartmuseum.wallpaperapp.domain.model.AtmosImage
import com.smartmuseum.wallpaperapp.ui.MainUiState
import com.smartmuseum.wallpaperapp.ui.ProviderImages

@Composable
fun ProviderImageSelector(
    uiState: MainUiState,
    onImageSelected: (AtmosImage) -> Unit,
    modifier: Modifier = Modifier
) {
    // Gradient Scrim for Legibility
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.5f),
                        Color.Black.copy(alpha = 0.7f)
                    )
                )
            )
    ) {
        // Provider Images List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(uiState.providerImages) { providerImages ->
                ProviderRow(
                    providerImages = providerImages,
                    onImageSelected = onImageSelected
                )
            }
        }
    }
}

@Composable
private fun ProviderRow(
    providerImages: ProviderImages,
    onImageSelected: (AtmosImage) -> Unit
) {
    Column {
        // Provider Name
        Text(
            text = providerImages.providerName,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Images Row
        if (providerImages.isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (providerImages.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Error: ${providerImages.error}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(providerImages.images) { image ->
                    ImageThumbnail(
                        image = image,
                        onClick = { onImageSelected(image) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageThumbnail(
    image: AtmosImage,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 160.dp, height = 200.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(Color.Gray.copy(alpha = 0.3f))
    ) {
        AsyncImage(
            model = image.url,
            contentDescription = image.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Overlay for better visibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f)
                        ),
                        startY = 150f
                    )
                )
        )
    }
}


