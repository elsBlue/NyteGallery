package com.nytefall.nytegallery.data

import com.nytefall.nytegallery.R

object GalleryRepository {
    val photos: List<GalleryPhoto> = listOf(
        GalleryPhoto("valley", "Bright Lake Valley", R.drawable.valley, takenAt = stamp(2026, 11, 11, 11, 11)),
        GalleryPhoto("japan", "Girl in a Japanese City", R.drawable.japan_city, takenAt = stamp(2026, 4, 3, 9, 24)),
        GalleryPhoto("cyber", "Neon Cyberpunk Market", R.drawable.cyberpunk, takenAt = stamp(2025, 8, 19, 8, 5)),
        GalleryPhoto("sea", "Yellow Umbrella Beach", R.drawable.seaside, takenAt = stamp(2026, 6, 2, 4, 40)),
        GalleryPhoto("cat", "Cat in a Flower Garden", R.drawable.garden_cat, takenAt = stamp(2024, 12, 28, 2, 15)),
        GalleryPhoto("lantern", "Daytime Festival Lanterns", R.drawable.lanterns, takenAt = stamp(2025, 1, 14, 10, 2)),
    )
}
