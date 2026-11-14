package com.inscopelabs.buildengine.ui.submission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inscopelabs.buildengine.ui.common.UiState
import com.inscopelabs.buildengine.ui.components.BranchShaRow
import com.inscopelabs.buildengine.ui.components.ErrorBanner

/**
 * The maximum archive size the gateway accepts, in bytes.
 *
 * gateway/src/config/constants.ts — `MAX_UPLOAD_BYTES = 25 * 1024 * 1024`. The gateway
 * measures the *decoded* archive and refuses anything larger with `BAD_ENCODING`
 * (gateway/src/routes/upload-validate.ts). The console pre-checks the same limit so the
 * operator learns about an oversized archive before a capability is minted, not after.
 *
 * The effective limit travels in [SubmissionReviewData.maxUploadBytes] so the stage can
 * source it from configuration instead of a compile-time constant.
 */
const val DEFAULT_MAX_UPLOAD_BYTES: Long = 25L * 1024L * 1024L

/**
 * Why a task is blocked.
 *
 * RELEASE-PLAN.md Stage 4.3 / T4-301 and guide item 37: the five causes stay distinct.
 * "Blocked" on its own is not an answer the operator can act on — a missing input is
 * fixed by supplying it, a specification conflict by resolving it, a decision needed by
 * deciding, a policy refusal by a policy change, and a technical failure by an
 * engineer. Collapsing them into one row hides the one thing the operator needs to see.
 *
 * The console does not guess which category applies. The gateway's own blocked-task
 * reason is mapped to a category by the stage's ViewModel; a reason that maps to none
 * of these is surfaced as [TECHNICAL_FAILURE] with its detail preserved, never silently
 * re-labelled as one of the other four.
 */
enum class BlockerCategory(
    val label: String,
    val guidance: String,
) {
    MISSING_INPUT(
        label = "Missing input",
        guidance = "Something the task needs is absent. Supply it and the task becomes actionable.",
    ),
    SPEC_CONFLICT(
        label = "Specification conflict",
        guidance = "Two requirements disagree. Resolve the conflict before the task can proceed.",
    ),
    DECISION_NEEDED(
        label = "Decision needed",
        guidance = "A judgement is outstanding. Record the decision; the task waits on it.",
    ),
    POLICY_REFUSAL(
        label = "Policy refusal",
        guidance = "A policy forbids the task. The refusal stands until the policy changes.",
    ),
    TECHNICAL_FAILURE(
        label = "Technical failure",
        guidance = "Something failed that the operator cannot resolve from here.",
    ),
}

/**
 * A task the engine is not handing out.
 *
 * `detail` carries the gateway's own reason verbatim. The console's job is to keep the
 * categories apart, not to rewrite what the engine said.
 */
data class BlockedTask(
    val taskId: String,
    val planId: String,
    val category: BlockerCategory,
    val detail: String,
)

/**
 * One file inside a submission archive.
 *
 * `sha256` is the digest of the file as it was read. `digestVerified` says whether that
 * digest was re-checked against the bytes actually about to be sent — a digest that was
 * computed at selection time and not re-verified is not evidence of anything, and the
 * screen says so rather than implying the archive is intact.
 *
 * `isOriginal` marks the file the operator picked. The original is never modified in
 * place: a rework produces a new revision with a new identity, and the original stays
 * exactly as it was picked (RELEASE-PLAN.md Stage 4.3 / T4-301).
 */
data class SubmissionFile(
    val displayPath: String,
    val sha256: String,
    val sizeBytes: Long,
    val isOriginal: Boolean = false,
    val digestVerified: Boolean = false,
)

/**
 * The lifecycle of one submission revision.
 *
 * A revision's identity is its `submissionId` — the contract's own field
 * (docs/schemas/upload.schema.json, required). Rework does not edit a revision; it
 * creates the next one and points it at the one it replaces.
 */
enum class RevisionState {
    DRAFT,
    READY,
    BLOCKED,
    UPLOADED,
    SUPERSEDED,
}

/**
 * One revision of a submission.
 *
 * `submissionId` is the revision identity and the value the gateway will store the
 * archive under. `supersedesSubmissionId` is the revision this one replaces, which is
 * how the original is preserved and the chain stays auditable.
 */
data class SubmissionRevision(
    val submissionId: String,
    val sequence: Int,
    val createdAtEpochSeconds: Long,
    val state: RevisionState,
    val archiveDisplayPath: String,
    val archiveSizeBytes: Long,
    val archiveSha256: String,
    val files: List<SubmissionFile> = emptyList(),
    val note: String = "",
    val supersedesSubmissionId: String? = null,
) {
    /** Whether this revision's bytes fit under [maxUploadBytes]. */
    fun fitsWithin(maxUploadBytes: Long): Boolean = archiveSizeBytes in 0..maxUploadBytes

    /** Whether the recorded archive digest is a well-formed SHA-256 hex string. */
    val hasWellFormedDigest: Boolean
        get() = archiveSha256.length == 64 && archiveSha256.all { it.isDigit() || it in 'a'..'f' }
}

/**
 * A constraint the submission must satisfy, and whether it currently does.
 */
data class SubmissionRestriction(
    val label: String,
    val detail: String,
    val isSatisfied: Boolean,
)

/**
 * Everything the submission review screen shows.
 *
 * `expectedHeadSha` is the head the operator reviewed and the submission is bound to.
 * `liveHeadSha` is the branch head as it is now. When the two differ the submission is
 * bound to a commit that no longer exists, and the screen says so instead of offering
 * an action that the gateway would refuse.
 */
data class SubmissionReviewData(
    val planId: String,
    val taskId: String,
    val branch: String,
    val expectedHeadSha: String,
    val liveHeadSha: String = "",
    val currentSubmissionId: String = "",
    val revisions: List<SubmissionRevision> = emptyList(),
    val blockedTasks: List<BlockedTask> = emptyList(),
    val restrictions: List<SubmissionRestriction> = emptyList(),
    val maxUploadBytes: Long = DEFAULT_MAX_UPLOAD_BYTES,
) {
    /** The revision the operator is currently looking at. */
    val currentRevision: SubmissionRevision?
        get() = revisions.firstOrNull { it.submissionId == currentSubmissionId }
            ?: revisions.maxByOrNull { it.sequence }

    /**
     * Whether the branch head has moved since the submission was bound.
     *
     * A blank [liveHeadSha] is "not known", not "unchanged": the console does not claim
     * the head is stable when it has not been able to check.
     */
    val hasHeadDrifted: Boolean
        get() = liveHeadSha.isNotBlank() && expectedHeadSha.isNotBlank() && liveHeadSha != expectedHeadSha

    /** Whether the branch head could not be checked at all. */
    val isHeadUnverified: Boolean
        get() = liveHeadSha.isBlank()

    /** Every revision the operator has produced, newest first. */
    val revisionHistory: List<SubmissionRevision>
        get() = revisions.sortedByDescending { it.sequence }
}

/**
 * Submission inspection and blocked-task review.
 *
 * **Proposed baseline.** This is a pre-execution proposal produced against
 * `docs/PRE-EXECUTION-ONBOARDING.md`, not a completed stage. It has not been compiled,
 * has no test coverage, and is not wired into navigation, DI or any caller. The AI
 * Studio agent executes Stage 4.3, corrects this file against the real repositories and
 * contracts, adds the tests, and closes the stage only when CI is green on the pushed
 * SHA (G-04).
 *
 * TODO(Stage 4.3 / T4-301): supply the ViewModel and the data source, confirm the
 * restriction list against the gateway's own pre-checks, map the gateway's blocked-task
 * reasons onto [BlockerCategory] without guessing, and add the state tests the gate
 * requires.
 *
 * ## What this screen is for
 *
 * RELEASE-PLAN.md Stage 4.3 / T4-301: submission inspection (paths, file hashes, expected
 * branch SHA, restrictions); blocked-task views that keep missing input, specification
 * conflict, decision needed, policy refusal and technical failure distinct; rework that
 * creates a new revision identity and preserves the original.
 *
 * ## Why the screen holds state instead of taking a ViewModel
 *
 * The screen takes a [UiState] and plain callbacks rather than a ViewModel. That keeps
 * this file self-contained — it compiles against what is on `main` today and adds no
 * type the stage has to invent before the body can be read — and it leaves exactly one
 * seam for the stage: a `@HiltViewModel` exposing `StateFlow<UiState<SubmissionReviewData>>`,
 * plus the mapping from the repositories to [SubmissionReviewData].
 *
 * **The signature below is a proposal.** The stage owns the final shape.
 *
 * ## Boundaries this screen keeps
 *
 * - It inspects and it reports. It performs no upload, mints no capability and signs
 *   nothing. The upload path is Stage 3.4's (`UploadSubmissionUseCase`), and it must go
 *   through the shared journal-backed executor, not through a screen.
 * - It never reports a submission as ready to send while the head has drifted, the
 *   archive digest is malformed, or the archive exceeds the gateway's limit.
 * - It shows the original revision after a rework. It does not overwrite it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionReviewScreen(
    state: UiState<SubmissionReviewData>,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    onOpenTask: (String) -> Unit = {},
    onStartRework: (submissionId: String) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("submission_review_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Submission review") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        when (state) {
            is UiState.Idle -> IdleContent(
                modifier = Modifier.padding(innerPadding),
                onRefresh = onRefresh,
            )

            is UiState.Loading -> LoadingContent(
                modifier = Modifier.padding(innerPadding),
            )

            is UiState.Error -> Column(
                modifier = Modifier.padding(innerPadding),
            ) {
                ErrorBanner(
                    message = state.message,
                    onRetry = onRefresh,
                    modifier = Modifier.testTag("submission_review_error"),
                )
            }

            is UiState.Success -> SubmissionReviewContent(
                data = state.data,
                modifier = Modifier.padding(innerPadding),
                onRefresh = onRefresh,
                onOpenTask = onOpenTask,
                onStartRework = onStartRework,
            )
        }
    }
}

@Composable
private fun IdleContent(
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Nothing loaded yet.",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("submission_review_idle"),
        )
        Text(
            text = "The review reads the submission the operator selected. It is loaded on " +
                "demand, not on entry.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.testTag("submission_review_load_button"),
        ) {
            Text("Load review")
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp).testTag("submission_review_loading"),
            strokeWidth = 2.dp,
        )
        Text(
            text = "Loading the submission…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubmissionReviewContent(
    data: SubmissionReviewData,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onOpenTask: (String) -> Unit,
    onStartRework: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("submission_review_content"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BoundScopeCard(
                data = data,
                onRefresh = onRefresh,
                onOpenTask = onOpenTask,
            )
        }

        item {
            CurrentRevisionCard(
                data = data,
                onStartRework = onStartRework,
            )
        }

        item {
            RestrictionsCard(restrictions = data.restrictions)
        }

        item {
            BlockedTasksCard(blockedTasks = data.blockedTasks)
        }

        item {
            RevisionHistoryCard(
                revisions = data.revisionHistory,
                currentSubmissionId = data.currentSubmissionId,
                onStartRework = onStartRework,
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * The scope the submission is bound to.
 *
 * The branch and the full 40-character head are shown before anything else, because a
 * submission bound to the wrong commit is the failure this screen exists to prevent.
 */
@Composable
private fun BoundScopeCard(
    data: SubmissionReviewData,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
    onOpenTask: (String) -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("submission_scope_card"),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Bound scope",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("submission_review_refresh_button"),
                ) {
                    Text("Refresh")
                }
            }

            Text(
                text = "Plan: ${data.planId.ifBlank { "—" }} · Task: ${data.taskId.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = "Branch: ${data.branch.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodyMedium,
            )

            BranchShaRow(sha = data.expectedHeadSha, label = "Expected head SHA")

            if (data.hasHeadDrifted) {
                ScopeWarning(
                    modifier = Modifier.testTag("submission_head_drifted"),
                    title = "Head has moved",
                    body = "The submission is bound to ${data.expectedHeadSha}, but the branch " +
                        "head is now ${data.liveHeadSha}. The gateway will refuse an upload " +
                        "against a moved head. Rebind the submission before sending it.",
                )
            } else if (data.isHeadUnverified) {
                ScopeWarning(
                    modifier = Modifier.testTag("submission_head_unverified"),
                    title = "Head not verified",
                    body = "The live branch head could not be read, so the console cannot say the " +
                        "submission is still bound to the reviewed commit.",
                )
            }

            if (data.taskId.isNotBlank()) {
                OutlinedButton(
                    onClick = { onOpenTask(data.taskId) },
                    modifier = Modifier.testTag("submission_open_task_button"),
                ) {
                    Text("Open task ${data.taskId}")
                }
            }
        }
    }
}

@Composable
private fun ScopeWarning(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(text = body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * The revision the operator is looking at, and the reasons it cannot be sent yet.
 */
@Composable
private fun CurrentRevisionCard(
    data: SubmissionReviewData,
    modifier: Modifier = Modifier,
    onStartRework: (String) -> Unit,
) {
    val revision = data.currentRevision

    ElevatedCard(
        modifier = modifier.fillMaxWidth().testTag("submission_current_revision_card"),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Current revision",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (revision == null) {
                Text(
                    text = "No submission has been selected for this task.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("submission_no_revision"),
                )
                return@Column
            }

            Text(
                text = "Revision ${revision.sequence} · ${revision.state.name.lowercase()}",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = "Submission id: ${revision.submissionId}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("submission_current_id"),
            )

            revision.supersedesSubmissionId?.let { previous ->
                Text(
                    text = "Supersedes $previous",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = revision.archiveDisplayPath.ifBlank { "archive not selected" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                text = "Archive size: ${revision.archiveSizeBytes} bytes " +
                    "(limit ${data.maxUploadBytes} bytes)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("submission_archive_size"),
            )

            Text(
                text = "SHA-256: ${revision.archiveSha256.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag("submission_archive_digest"),
            )

            if (!revision.fitsWithin(data.maxUploadBytes)) {
                ScopeWarning(
                    modifier = Modifier.testTag("submission_oversized"),
                    title = "Archive is too large",
                    body = "The archive is ${revision.archiveSizeBytes} bytes and the gateway " +
                        "accepts at most ${data.maxUploadBytes}. It will be refused before it " +
                        "is sent.",
                )
            }

            if (!revision.hasWellFormedDigest) {
                ScopeWarning(
                    modifier = Modifier.testTag("submission_bad_digest"),
                    title = "Digest is not usable",
                    body = "The recorded digest is not a 64-character lowercase SHA-256 hex " +
                        "string. The archive must be re-read and re-hashed before it is sent.",
                )
            }

            if (revision.files.any { !it.digestVerified }) {
                ScopeWarning(
                    modifier = Modifier.testTag("submission_unverified_file_digest"),
                    title = "A file digest has not been re-verified",
                    body = "At least one file's digest was computed when the file was picked " +
                        "and has not been re-checked against the bytes that would be sent.",
                )
            }

            if (revision.note.isNotBlank()) {
                Text(
                    text = revision.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(
                onClick = { onStartRework(revision.submissionId) },
                modifier = Modifier.testTag("submission_start_rework_button"),
            ) {
                Text("Start rework (new revision)")
            }

            Text(
                text = "Rework creates a new submission id and leaves this revision intact.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The constraints the submission must satisfy.
 */
@Composable
private fun RestrictionsCard(
    restrictions: List<SubmissionRestriction>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("submission_restrictions_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Restrictions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (restrictions.isEmpty()) {
                Text(
                    text = "No restrictions reported.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            restrictions.forEach { restriction ->
                Row(
                    modifier = Modifier.fillMaxWidth().testTag(
                        "submission_restriction_${restriction.label.lowercase().replace(' ', '_')}",
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = if (restriction.isSatisfied) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.Error
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (restriction.isSatisfied) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = restriction.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = restriction.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The blocked tasks, with their causes kept apart.
 *
 * Each category gets its own label and its own explanation, because the operator's next
 * step is different for each of them. The categories are shown in a fixed order so two
 * screens never order the same set of blockers differently.
 */
@Composable
private fun BlockedTasksCard(
    blockedTasks: List<BlockedTask>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("submission_blocked_tasks_card"),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Blocked tasks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (blockedTasks.isEmpty()) {
                Text(
                    text = "No blocked tasks reported.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("submission_no_blocked_tasks"),
                )
                return@Column
            }

            BlockerCategory.entries.forEach { category ->
                val inCategory = blockedTasks.filter { it.category == category }
                if (inCategory.isEmpty()) return@forEach

                Column(
                    modifier = Modifier.fillMaxWidth().testTag(
                        "submission_blocker_group_${category.name.lowercase()}",
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${category.label} (${inCategory.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = blockerColor(category),
                    )
                    Text(
                        text = category.guidance,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    inCategory.forEach { blocked ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().testTag(
                                "submission_blocked_task_${blocked.taskId}",
                            ),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = blockerColor(category),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = blocked.taskId.ifBlank { "(no task id)" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (blocked.planId.isNotBlank()) {
                                        Text(
                                            text = "Plan: ${blocked.planId}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (blocked.detail.isNotBlank()) {
                                        Text(
                                            text = blocked.detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The revision chain, newest first.
 *
 * A superseded revision stays in the list. The original is what the operator submitted
 * first, and removing it from the view would make a rework look like the first attempt.
 */
@Composable
private fun RevisionHistoryCard(
    revisions: List<SubmissionRevision>,
    currentSubmissionId: String,
    modifier: Modifier = Modifier,
    onStartRework: (String) -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().testTag("submission_revision_history_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Revision history",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (revisions.isEmpty()) {
                Text(
                    text = "No revisions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            revisions.forEach { revision ->
                val isCurrent = revision.submissionId == currentSubmissionId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(12.dp)
                        .testTag("submission_revision_${revision.submissionId}"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = if (isCurrent) Icons.Default.Info else Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Revision ${revision.sequence} · ${revision.state.name.lowercase()}" +
                                if (isCurrent) " · current" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = revision.submissionId,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        revision.supersedesSubmissionId?.let { previous ->
                            Text(
                                text = "Supersedes $previous",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!isCurrent && revision.state != RevisionState.UPLOADED) {
                        OutlinedButton(
                            onClick = { onStartRework(revision.submissionId) },
                            modifier = Modifier.testTag(
                                "submission_rework_${revision.submissionId}_button",
                            ),
                        ) {
                            Text("Rework")
                        }
                    }
                }
            }
        }
    }
}

/**
 * The colour a blocker category is shown in.
 *
 * Distinct per category so two different causes never look alike at a glance. The
 * labels carry the meaning; the colour only helps the operator scan the list.
 */
@Composable
private fun blockerColor(category: BlockerCategory): Color = when (category) {
    BlockerCategory.MISSING_INPUT -> MaterialTheme.colorScheme.tertiary
    BlockerCategory.SPEC_CONFLICT -> MaterialTheme.colorScheme.secondary
    BlockerCategory.DECISION_NEEDED -> MaterialTheme.colorScheme.primary
    BlockerCategory.POLICY_REFUSAL -> MaterialTheme.colorScheme.error
    BlockerCategory.TECHNICAL_FAILURE -> MaterialTheme.colorScheme.onSurfaceVariant
}
