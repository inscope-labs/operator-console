package com.inscopelabs.buildengine.manualpipeline.domain.port

import com.inscopelabs.buildengine.manualpipeline.domain.model.Deliverable
import com.inscopelabs.buildengine.manualpipeline.domain.model.SubmissionManifest

/**
 * Port for the module-local deliverable manifest store (MP-005 / MP-009).
 *
 * The `ui` layer reaches this through the port, never through a `data` implementation
 * (architecture rule: `ui` calls `domain` and reads `domain.model` only).
 */
interface DeliverableManifestPort {

    suspend fun saveManifest(manifest: SubmissionManifest, deliverables: List<Deliverable>)

    suspend fun loadForTask(taskId: String): List<Deliverable>
}
