package com.smartmuseum.wallpaperapp.domain.repository

import com.smartmuseum.wallpaperapp.domain.model.CalendarEvent

interface CalendarRepository {
    suspend fun getTodaysEvents(): List<CalendarEvent>
}
