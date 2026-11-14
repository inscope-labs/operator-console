package com.inscopelabs.buildengine.ui.publish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inscopelabs.buildengine.R
import com.inscopelabs.buildengine.ui.components.OperationResultBanner

/**
 * Progress and result of a publish. It shares the confirmation screen's [PublishViewModel]
 * (the caller passes the confirmation destination's instance), so it shows the real state of
 * the one submission rather than a separate guess.
 */
@Composable
fun PublishProgressScreen(
    modifier: Modifier = Modifier,
    viewModel: PublishViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.publish_progress_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        when (val current = state) {
            is PublishUiState.Submitting -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.publish_progress_sending))
            }
            is PublishUiState.Finished -> OperationResultBanner(
                message = publishBannerMessage(current.banner),
                success = current.banner.isSuccess,
                auditRef = current.banner.auditRef,
            )
            else -> Text(stringResource(R.string.publish_progress_none))
        }
    }
}

/** Maps the presenter's distinct kinds to distinct operator-facing messages. */
@Composable
fun publishBannerMessage(banner: PublishBanner): String = when (banner.kind) {
    PublishBannerKind.SUCCEEDED -> stringResource(R.string.publish_result_succeeded)
    PublishBannerKind.SCOPE_REFUSED -> stringResource(R.string.publish_result_scope_refused)
    PublishBannerKind.REPLAYED -> stringResource(R.string.publish_result_replayed)
    PublishBannerKind.HEAD_DRIFTED -> stringResource(R.string.publish_result_head_drifted)
    PublishBannerKind.HALT_ACTIVE -> stringResource(R.string.publish_result_halt_active)
    PublishBannerKind.REFUSED -> stringResource(R.string.publish_result_refused, banner.code.orEmpty())
    PublishBannerKind.CONFLICT -> stringResource(R.string.publish_result_conflict, banner.code.orEmpty())
    PublishBannerKind.TIMEOUT -> stringResource(R.string.publish_result_timeout)
    PublishBannerKind.UNCERTAIN -> stringResource(R.string.publish_result_uncertain)
    PublishBannerKind.RECOVERY_REQUIRED -> stringResource(R.string.publish_result_recovery_required)
    PublishBannerKind.DECLINED -> stringResource(R.string.publish_result_declined)
    PublishBannerKind.AUTH_FAILED -> stringResource(R.string.publish_result_auth_failed)
    PublishBannerKind.LOCKED_OUT -> stringResource(R.string.publish_result_locked_out)
    PublishBannerKind.KEY_INVALIDATED -> stringResource(R.string.publish_result_key_invalidated)
    PublishBannerKind.NOT_ENROLLED -> stringResource(R.string.publish_result_not_enrolled)
    PublishBannerKind.TTL_EXPIRED -> stringResource(R.string.publish_result_ttl_expired)
    PublishBannerKind.TIER_NOT_ALLOWED -> stringResource(R.string.publish_result_tier_not_allowed)
    PublishBannerKind.ENVIRONMENT_MISMATCH ->
        stringResource(R.string.publish_result_environment_mismatch, banner.detail.orEmpty())
    PublishBannerKind.HEAD_UNAVAILABLE -> stringResource(R.string.publish_result_head_unavailable)
    PublishBannerKind.INVALID_TARGET ->
        stringResource(R.string.publish_result_invalid_target, banner.detail.orEmpty())
}
