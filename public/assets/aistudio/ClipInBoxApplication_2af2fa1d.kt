package com.inscopelabs.abx.clipinbox

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.os.Bundle
import com.inscopelabs.abx.clipinbox.boot.BootGuard
import com.inscopelabs.abx.clipinbox.boot.BootRoute
import com.inscopelabs.abx.clipinbox.boot.RecoveryActivity
import com.inscopelabs.abx.clipinbox.category.CategoryRepository
import com.inscopelabs.abx.clipinbox.category.CategoryRepositoryImpl
import com.inscopelabs.abx.clipinbox.data.local.ClipboardDatabase
import com.inscopelabs.abx.clipinbox.diagnostics.CrashReporterManager
import com.inscopelabs.abx.clipinbox.diagnostics.DiagnosticsInitializer
import com.inscopelabs.abx.clipinbox.diagnostics.GlobalExceptionHandler
import com.inscopelabs.abx.clipinbox.diagnostics.Logger
import com.inscopelabs.abx.clipinbox.domain.ClipRepository
import com.inscopelabs.abx.clipinbox.domain.ClipRepositoryImpl
import com.inscopelabs.abx.clipinbox.domain.detect.ClipClassifier
import com.inscopelabs.abx.clipinbox.domain.queue.ClipQueueManager
import com.inscopelabs.abx.clipinbox.domain.queue.QueueRepositoryImpl
import com.inscopelabs.abx.clipinbox.export.connector.AbxMailboxConnector
import com.inscopelabs.abx.clipinbox.export.connector.EncryptedSessionStore
import com.inscopelabs.abx.clipinbox.export.connector.FileManagerConnector
import com.inscopelabs.abx.clipinbox.export.connector.SessionGate
import com.inscopelabs.abx.clipinbox.export.saf.SafPathRepository
import com.inscopelabs.abx.clipinbox.security.AutoClearScheduler
import com.inscopelabs.abx.clipinbox.security.SensitiveClipPolicy
import com.inscopelabs.abx.clipinbox.service.ClipboardWatcher
import com.inscopelabs.abx.clipinbox.service.OtpAutoCapture
import com.inscopelabs.abx.clipinbox.tag.TagRepository
import com.inscopelabs.abx.clipinbox.tag.TagRepositoryImpl
import com.inscopelabs.abx.clipinbox.utils.NotificationHelper
import com.inscopelabs.abx.clipinbox.utils.NotificationPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ClipInBoxApplication : Application() {

    lateinit var repository: ClipRepository
        private set

    lateinit var queueRepository: ClipQueueManager.QueueRepository
        private set

    lateinit var sessionGate: SessionGate
        private set

    lateinit var connector: FileManagerConnector
        private set

    lateinit var safPathRepository: SafPathRepository
        private set

    lateinit var categoryRepository: CategoryRepository
        private set

    lateinit var tagRepository: TagRepository
        private set

    lateinit var clipboardWatcher: ClipboardWatcher
        private set

    lateinit var autoClearScheduler: AutoClearScheduler
        private set

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity !is RecoveryActivity) {
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
            CrashReporterManager.initialize(this)
            Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(this))
            DiagnosticsInitializer.initialize(this)

            val database = ClipboardDatabase.getDatabase(this)

            sessionGate = SessionGate(EncryptedSessionStore(this))
            Logger.i("ClipInBoxApplication", "Initialized SessionGate")

            connector = AbxMailboxConnector(sessionGate)
            Logger.i("ClipInBoxApplication", "Initialized AbxMailboxConnector")

            safPathRepository = SafPathRepository(
                database.safPathDao(),
                database.namingMacroDao(),
            )
            Logger.i("ClipInBoxApplication", "Initialized SafPathRepository")

            categoryRepository = CategoryRepositoryImpl(database.categoryDao(), database.clipDao())
            Logger.i("ClipInBoxApplication", "Initialized CategoryRepository")
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    categoryRepository.ensureSeedCategoryExists()
                    Logger.i("ClipInBoxApplication", "Ensured seed category exists")
                } catch (t: Throwable) {
                    Logger.e("ClipInBoxApplication", "Failed to ensure seed category", t)
                }
            }

            tagRepository = TagRepositoryImpl(database.tagDao(), database.clipDao())
            Logger.i("ClipInBoxApplication", "Initialized TagRepository")
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    tagRepository.ensureSystemTagsSeeded()
                    Logger.i("ClipInBoxApplication", "Ensured system tags seeded")
                } catch (t: Throwable) {
                    Logger.e("ClipInBoxApplication", "Failed to ensure system tags seeded", t)
                }
            }

            repository = ClipRepositoryImpl(database.clipDao(), categoryRepository)
            queueRepository = QueueRepositoryImpl(database.queueDao())
            Logger.i("ClipInBoxApplication", "Initialized ClipRepository and QueueRepository")

            val policy = SensitiveClipPolicy()
            val classifier = ClipClassifier()
            val notificationHelper = NotificationHelper(this, NotificationPreferences(this))
            val otpCapture = OtpAutoCapture(this, notificationHelper)
            autoClearScheduler = AutoClearScheduler(this)
            clipboardWatcher = ClipboardWatcher(
                this, classifier, policy, otpCapture, autoClearScheduler
            )
            clipboardWatcher.install()
            Logger.i("ClipInBoxApplication", "ClipboardWatcher installed")

            if (NotificationPreferences.isPersistentNotificationEnabled(this)) {
                NotificationHelper.postTriggerNotification(this, true)
            }
            BootGuard.stageSuccess("app_init")
        } catch (t: Throwable) {
            BootGuard.recordFailure(this, "app_init", t)
        }
    }

    fun setNotificationTriggerEnabled(enabled: Boolean) {
        NotificationPreferences.setPersistentNotificationEnabled(this, enabled)
        if (enabled) {
            NotificationHelper.postTriggerNotification(this, true)
        } else {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NotificationHelper.NOTIFICATION_ID)
        }
    }
}
