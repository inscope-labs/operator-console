package com.inscopelabs.buildengine.domain.repository

import com.inscopelabs.buildengine.domain.model.ConnectionStatus
import kotlinx.coroutines.flow.Flow

/**
 * Single source of connection status for the shell banner.
 *
 * Combines gateway base URL configuration with the last successful health snapshot.
 */
interface ConnectionStatusRepository {
    val status: Flow<ConnectionStatus>
}
