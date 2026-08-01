package de.hasselmeyer.leanangle.ui.navigation

import de.hasselmeyer.leanangle.ui.intro.IntroStage
import de.hasselmeyer.leanangle.ui.legal.LegalDocument

enum class ScreenDirection {
    HORIZONTAL, VERTICAL
}

enum class Screens(val screenEntryDirection: ScreenDirection) {
    INTRO(ScreenDirection.VERTICAL),
    ACCESS(ScreenDirection.VERTICAL),
    TRACKING(ScreenDirection.HORIZONTAL),
    HISTORY(ScreenDirection.HORIZONTAL),
    DETAIL(ScreenDirection.HORIZONTAL),
    SETTINGS(ScreenDirection.VERTICAL),
    PREMIUM(ScreenDirection.VERTICAL),
    CALIBRATION(ScreenDirection.VERTICAL),
    LEGAL(ScreenDirection.VERTICAL)
}

sealed interface AppRoute {
    val screen: Screens
    fun index(): Int

    data class Intro(val stage: IntroStage) : AppRoute {
        override val screen = Screens.INTRO
        override fun index() = 0
    }

    data object Access : AppRoute {
        override val screen = Screens.ACCESS
        override fun index() = 1
    }

    data object Tracking : AppRoute {
        override val screen = Screens.TRACKING
        override fun index() = 2
    }

    data object Calibration : AppRoute {
        override val screen = Screens.CALIBRATION
        override fun index() = 3
    }

    data object TrackReview : AppRoute {
        override val screen = Screens.HISTORY
        override fun index() = 4
    }

    data class RideDetail(val rideId: Long) : AppRoute {
        override val screen = Screens.DETAIL
        override fun index() = 5
    }

    data object Settings : AppRoute {
        override val screen = Screens.SETTINGS
        override fun index() = 6
    }

    data object Premium : AppRoute {
        override val screen = Screens.PREMIUM
        override fun index() = 7
    }

    data class Legal(val document: LegalDocument) : AppRoute {
        override val screen = Screens.LEGAL
        override fun index() = 8
    }
}
