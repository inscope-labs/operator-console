package com.inscopelabs.buildengine.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inscopelabs.buildengine.domain.model.ConnectionStatus
import com.inscopelabs.buildengine.domain.repository.ConnectionStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Shell-level connection status for the persistent banner.
 */
@HiltViewModel
class ConnectionStatusViewModel @Inject constructor(
    connectionStatusRepository: ConnectionStatusRepository,
) : ViewModel() {

    val status: StateFlow<ConnectionStatus> = connectionStatusRepository.status
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConnectionStatus.NotConfigured,
        )
}
