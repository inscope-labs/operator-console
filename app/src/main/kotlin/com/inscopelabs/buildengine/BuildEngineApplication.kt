package com.inscopelabs.buildengine

import android.app.Application
import com.inscopelabs.buildengine.domain.repository.SessionRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Design review §4: the app holds no repository credential of any kind. The posture
 * check that must fail closed here is described in docs/architecture.md.
 *
 * ### The session opens here
 *
 * The session is opened when the process starts, not when the Activity is created.
 * A configuration change — a rotation, a theme switch — recreates the Activity, and
 * a session that reopened on every recreation would rotate its own identity under
 * the operator's feet, and would undo an operator who had deliberately ended it.
 *
 * The session is in memory and dies with the process (design review §9). Nothing
 * durable is written here, and there is nothing to restore on the next launch.
 */
@HiltAndroidApp
class BuildEngineApplication : Application() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    /** A scope that lives as long as the process does. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            sessionRepository.start()
        }
    }
}
