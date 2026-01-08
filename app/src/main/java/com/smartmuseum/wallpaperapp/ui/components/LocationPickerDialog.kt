package com.smartmuseum.wallpaperapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartmuseum.wallpaperapp.domain.usecase.LocationResult

@Composable
fun LocationPickerDialog(
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    searchResults: List<LocationResult>,
    onLocationSelected: (LocationResult) -> Unit,
    isLoading: Boolean
) {
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Location") },
        text = {
            Column {
                Text("We couldn't get your automatic location. Please type your city name:")
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it.length > 2) onSearch(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. London, UK") },
                    singleLine = true
                )

                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(searchResults) { result ->
                        ListItem(
                            headlineContent = { Text(result.name) },
                            modifier = Modifier.clickable { onLocationSelected(result) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
