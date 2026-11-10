package com.inscopelabs.buildengine

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.inscopelabs.buildengine.boot.BootGuard
import com.inscopelabs.buildengine.boot.BootRoute
import com.inscopelabs.buildengine.boot.RecoveryActivity
import com.inscopelabs.buildengine.diagnostics.CrashActivity
import com.inscopelabs.buildengine.diagnostics.GlobalExceptionHandler
import com.inscopelabs.buildengine.diagnostics.UserFacingErrorActivity
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

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity !is RecoveryActivity &&
                    activity !is CrashActivity &&
                    activity !is UserFacingErrorActivity
                ) {
                    BootRoute.redirectIfNeeded(activity)
                }
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        BootGuard.stageStart("app_init")
        try {
            Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(this))

            applicationScope.launch {
                sessionRepository.start()
            }
            BootGuard.stageSuccess("app_init")
        } catch (t: Throwable) {
            BootGuard.recordFailure(this, "app_init", t)
        }
    }
}
