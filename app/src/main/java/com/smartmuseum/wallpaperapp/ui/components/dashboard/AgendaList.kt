package com.smartmuseum.wallpaperapp.ui.components.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartmuseum.wallpaperapp.R
import com.smartmuseum.wallpaperapp.domain.model.CalendarEvent

@Composable
fun AgendaList(events: List<CalendarEvent>?) {
    if (events.isNullOrEmpty()) return

    Column {
        Text(
            text = stringResource(R.string.todays_schedule),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            items(events.take(3)) { event ->
                CalendarEventItem(event)
            }
        }
    }
}

@Preview
@Composable
private fun AgendaListPreview() {
    AgendaList(
        events = listOf(
            CalendarEvent(
                "Meeting with team",
                System.currentTimeMillis(),
                System.currentTimeMillis() + 3600000,
                false
            ),
            CalendarEvent(
                "Lunch break",
                System.currentTimeMillis() + 7200000,
                System.currentTimeMillis() + 10800000,
                false
            )
        )
    )
}
