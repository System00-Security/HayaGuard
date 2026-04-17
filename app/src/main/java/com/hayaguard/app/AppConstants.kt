package com.hayaguard.app

object AppConstants {

    object ImageProcessing {
        const val MIN_IMAGE_SIZE = 5000
        const val MIN_IMAGE_DIMENSION = 100
        const val DEFAULT_PIXEL_SIZE = 32
        const val BLUR_RADIUS = 25
    }

    object Timeouts {
        const val PROCESSING_TIMEOUT_MS = 15000L
        const val AI_RACE_TIMEOUT_MS = 300L
        const val REFRESH_RETRY_DELAY_MS = 100L
        const val CONNECTION_TIMEOUT_MS = 15000
        const val READ_TIMEOUT_MS = 15000
    }

    object NSFWThresholds {
        const val LOW_CONFIDENCE = 0.75f
        const val DRAWING = 0.55f
        const val HENTAI = 0.55f
        const val PORN = 0.85f
        const val SEXY = 0.80f
        const val COMBINED_ANIME = 0.45f
        const val EXPLICIT = 0.60f
        const val BREAST = 0.75f
    }

    object HayaMode {
        const val MIN_FACE_SIZE = 0.15f
        const val MIN_FACE_AREA_RATIO = 0.01f
        const val GENDER_CONFIDENCE = 0.65f
        const val FACE_PADDING_RATIO = 0.15f
    }

    object GPU {
        const val STABILITY_THRESHOLD = 0.7f
        const val STABILITY_TEST_RUNS = 3
        const val RETEST_INTERVAL_DAYS = 7
    }

    object Cache {
        const val PLACEHOLDER_CACHE_SIZE = 50
        const val MIN_GC_INTERVAL_MS = 5000L
    }
}
