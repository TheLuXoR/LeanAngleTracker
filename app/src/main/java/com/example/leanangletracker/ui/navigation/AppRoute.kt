package com.example.leanangletracker.ui.navigation

import com.example.leanangletracker.ui.intro.IntroStage

sealed interface AppRoute {
    data class Intro(val stage: IntroStage) : AppRoute
    data object Tracking : AppRoute
    data object Settings : AppRoute
    data object TrackReview : AppRoute
}
