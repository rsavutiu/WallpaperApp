package com.smartmuseum.wallpaperapp.domain.repository

import com.smartmuseum.wallpaperapp.domain.model.AtmosImage

interface SampleDataProvider {
    fun getSampleAtmosImage(): AtmosImage
}
