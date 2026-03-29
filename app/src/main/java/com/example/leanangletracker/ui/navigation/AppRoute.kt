package com.example.leanangletracker.ui.navigation

import com.example.leanangletracker.ui.intro.IntroStage

enum class ScreenDirection {
    HORIZONTAL, VERTICAL
}

enum class Screens(val screenEntryDirection: ScreenDirection) {
    INTRO(ScreenDirection.VERTICAL),
    TRACKING(ScreenDirection.VERTICAL),
    HISTORY(ScreenDirection.VERTICAL),
    DETAIL(ScreenDirection.HORIZONTAL),
    SETTINGS(ScreenDirection.VERTICAL),
    CALIBRATION(ScreenDirection.VERTICAL)
}

sealed interface AppRoute {
    val screen: Screens
    fun index(): Int

    data class Intro(val stage: IntroStage) : AppRoute {
        override val screen = Screens.INTRO
        override fun index() = 0
    }

    data object Tracking : AppRoute {
        override val screen = Screens.TRACKING
        override fun index() = 1
    }

    data object Calibration : AppRoute {
        override val screen = Screens.CALIBRATION
        override fun index() = 2
    }

    data object TrackReview : AppRoute {
        override val screen = Screens.HISTORY
        override fun index() = 3
    }

    data class RideDetail(val rideId: Long) : AppRoute {
        override val screen = Screens.DETAIL
        override fun index() = 4
    }

    data object Settings : AppRoute {
        override val screen = Screens.SETTINGS
        override fun index() = 5
    }
}
