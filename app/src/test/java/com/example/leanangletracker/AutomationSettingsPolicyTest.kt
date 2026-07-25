package com.example.leanangletracker

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationSettingsPolicyTest {
    @Test
    fun enablingLockedAutomationRoutesToPremium() {
        assertEquals(
            true,
            shouldOpenPremiumForAutomationToggle(
                enabled = true,
                hasAutomationAccess = false
            )
        )
        assertEquals(
            false,
            shouldOpenPremiumForAutomationToggle(
                enabled = false,
                hasAutomationAccess = false
            )
        )
        assertEquals(
            false,
            shouldOpenPremiumForAutomationToggle(
                enabled = true,
                hasAutomationAccess = true
            )
        )
    }

    @Test
    fun firstAccessEnablesBothAutomationFeatures() {
        val decision = resolveAutomationSettings(
            hadAccess = false,
            hasAccess = true,
            autoPauseEnabled = false,
            autoResumeEnabled = false
        )

        assertEquals(
            AutomationSettingsDecision(
                autoPauseEnabled = true,
                autoResumeEnabled = true
            ),
            decision
        )
    }

    @Test
    fun losingLastAccessDisablesBothAutomationFeatures() {
        val decision = resolveAutomationSettings(
            hadAccess = true,
            hasAccess = false,
            autoPauseEnabled = true,
            autoResumeEnabled = true
        )

        assertEquals(
            AutomationSettingsDecision(
                autoPauseEnabled = false,
                autoResumeEnabled = false
            ),
            decision
        )
    }

    @Test
    fun continuingAccessPreservesIndividualPreferences() {
        val decision = resolveAutomationSettings(
            hadAccess = true,
            hasAccess = true,
            autoPauseEnabled = false,
            autoResumeEnabled = true
        )

        assertEquals(
            AutomationSettingsDecision(
                autoPauseEnabled = false,
                autoResumeEnabled = true
            ),
            decision
        )
    }
}
