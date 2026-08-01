package de.hasselmeyer.leanangle.ui.tracking

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.hasselmeyer.leanangle.AppTourUiState
import de.hasselmeyer.leanangle.R

private data class AppTourPage(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int
)

private val appTourPages = listOf(
    AppTourPage(R.string.app_tour_gauge_title, R.string.app_tour_gauge_body),
    AppTourPage(R.string.app_tour_recording_title, R.string.app_tour_recording_body),
    AppTourPage(R.string.app_tour_live_data_title, R.string.app_tour_live_data_body),
    AppTourPage(R.string.app_tour_history_title, R.string.app_tour_history_body),
    AppTourPage(R.string.app_tour_manage_title, R.string.app_tour_manage_body)
)

@Composable
internal fun AppTourDialogs(
    state: AppTourUiState,
    onAcceptOffer: () -> Unit,
    onDeclineOffer: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onFinish: () -> Unit
) {
    if (state.offerPending) {
        AlertDialog(
            onDismissRequest = onDeclineOffer,
            title = { Text(stringResource(R.string.app_tour_prompt_title)) },
            text = { Text(stringResource(R.string.app_tour_prompt_body)) },
            confirmButton = {
                TextButton(onClick = onAcceptOffer) {
                    Text(stringResource(R.string.app_tour_prompt_start))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeclineOffer) {
                    Text(stringResource(R.string.app_tour_prompt_decline))
                }
            }
        )
        return
    }

    if (!state.isActive) return

    val pageIndex = state.currentPage.coerceIn(appTourPages.indices)
    val page = appTourPages[pageIndex]
    val isLastPage = pageIndex == appTourPages.lastIndex
    val bodyScrollState = rememberScrollState()
    var completedInteractivePages by remember(state.isActive) {
        mutableStateOf(emptySet<Int>())
    }
    val canAdvance = pageIndex !in INTERACTIVE_APP_TOUR_PAGES ||
        pageIndex in completedInteractivePages

    LaunchedEffect(pageIndex) {
        bodyScrollState.scrollTo(0)
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Dialog(
        onDismissRequest = onFinish,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.86f else 0.92f)
                .fillMaxHeight(if (isLandscape) 0.88f else 0.9f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTourPagePreview(
                        pageIndex = pageIndex,
                        isCompleted = pageIndex in completedInteractivePages,
                        onInteractionCompleted = {
                            completedInteractivePages += pageIndex
                        },
                        modifier = Modifier.weight(1.2f).fillMaxHeight()
                    )
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppTourHeader(pageIndex = pageIndex, page = page)
                        Text(
                            text = stringResource(page.bodyRes),
                            modifier = Modifier.weight(1f).verticalScroll(bodyScrollState),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        AppTourNavigation(
                            pageIndex = pageIndex,
                            isLastPage = isLastPage,
                            canAdvance = canAdvance,
                            onPreviousPage = onPreviousPage,
                            onNextPage = onNextPage,
                            onFinish = onFinish
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppTourHeader(pageIndex = pageIndex, page = page)
                    AppTourPagePreview(
                        pageIndex = pageIndex,
                        isCompleted = pageIndex in completedInteractivePages,
                        onInteractionCompleted = {
                            completedInteractivePages += pageIndex
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                    Text(
                        text = stringResource(page.bodyRes),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 132.dp)
                            .verticalScroll(bodyScrollState),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    AppTourNavigation(
                        pageIndex = pageIndex,
                        isLastPage = isLastPage,
                        canAdvance = canAdvance,
                        onPreviousPage = onPreviousPage,
                        onNextPage = onNextPage,
                        onFinish = onFinish
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTourHeader(pageIndex: Int, page: AppTourPage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(
                R.string.app_tour_page_indicator,
                pageIndex + 1,
                appTourPages.size
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(page.titleRes),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AppTourNavigation(
    pageIndex: Int,
    isLastPage: Boolean,
    canAdvance: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pageIndex > 0) {
            TextButton(onClick = onPreviousPage) {
                Text(stringResource(R.string.app_tour_back))
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        TextButton(onClick = onFinish) {
            Text(stringResource(R.string.app_tour_skip))
        }

        Button(
            onClick = if (isLastPage) onFinish else onNextPage,
            enabled = canAdvance
        ) {
            Text(stringResource(if (isLastPage) R.string.app_tour_finish else R.string.app_tour_next))
        }
    }
}

private val INTERACTIVE_APP_TOUR_PAGES = setOf(0, 1, 3)
