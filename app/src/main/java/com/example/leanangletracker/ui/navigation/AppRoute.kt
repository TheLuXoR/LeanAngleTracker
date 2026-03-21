package com.example.leanangletracker.ui.navigation

import com.example.leanangletracker.ui.intro.IntroStage

sealed interface AppRoute {
    fun index(): Int

    data class Intro(val stage: IntroStage) : AppRoute {
        override fun index(): Int {
            return 0
        }
    }

    data object Tracking : AppRoute {
        override fun index(): Int {
            return 1
        }
    }

    data object Settings : AppRoute {
        override fun index(): Int {
            return 3
        }
    }

    data object TrackReview : AppRoute {
        override fun index(): Int {
            return 2
        }
    }
}
