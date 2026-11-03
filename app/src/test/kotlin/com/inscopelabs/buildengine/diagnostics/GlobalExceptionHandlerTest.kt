package com.inscopelabs.buildengine.diagnostics

import android.content.Intent
import com.inscopelabs.buildengine.testutil.FakeContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GlobalExceptionHandlerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: FakeContext
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        context = FakeContext(baseDir = filesDir)
        CrashReporterManager.initialize(context)
    }

    @Test
    fun `generateReferenceCode formats with XTOOLS prefix`() {
        val handler = GlobalExceptionHandler(context)
        val code = handler.generateReferenceCode()
        assertTrue("Reference code should start with XTOOLS-: $code", code.startsWith("XTOOLS-"))
    }

    @Test
    fun `buildCrashReport contains required diagnostics sections`() {
        val handler = GlobalExceptionHandler(context)
        val ex = RuntimeException("Boom test error")
        val thread = Thread.currentThread()
        val refCode = handler.generateReferenceCode()

        val report = handler.buildCrashReport(thread, ex, refCode)

        assertTrue(report.contains("GLOBAL CRASH REPORT"))
        assertTrue(report.contains(refCode))
        assertTrue(report.contains(thread.name))
        assertTrue(report.contains("com.inscopelabs.buildengine"))
        assertTrue(report.contains("RuntimeException"))
        assertTrue(report.contains("Boom test error"))
        assertTrue(report.contains("STACK TRACE"))
    }

    @Test
    fun `writeCrashLog writes report to file and rotates when size exceeds limit`() {
        val handler = GlobalExceptionHandler(context)
        val testReport = "Crash report sample content\n"

        handler.writeCrashLog(testReport)

        val logFile = File(filesDir, GlobalExceptionHandler.CRASH_LOG_FILE)
        assertTrue(logFile.exists())
        assertEquals(testReport, logFile.readText())

        // Test rotation by writing a report when file size exceeds MAX_LOG_SIZE
        logFile.writeBytes(ByteArray(GlobalExceptionHandler.MAX_LOG_SIZE.toInt() + 10))

        val secondReport = "Second crash after rotation\n"
        handler.writeCrashLog(secondReport)

        val rotatedFile = File(filesDir, "${GlobalExceptionHandler.CRASH_LOG_FILE}.1")
        assertTrue("Rotated log file should exist", rotatedFile.exists())
        assertTrue("Active log file should contain new report", logFile.readText().contains(secondReport))
    }

    @Test
    fun `launchErrorActivity launches CrashActivity in debug build`() {
        val handler = GlobalExceptionHandler(context)

        val ex = IllegalStateException("Debug exception")
        val report = "Full report text"
        val refCode = "XTOOLS-12345"

        handler.launchErrorActivity(ex, report, refCode)

        assertTrue(handler.crashActivityLaunched)
        assertEquals(1, context.startedActivities.size)

        val intent = context.startedActivities.first()
        assertNotNull(intent)
    }

    @Test
    fun `uncaughtException runs full handler pipeline safely without crashing JVM`() {
        var terminated = false
        val handler = GlobalExceptionHandler(context).apply {
            processTerminator = { terminated = true }
        }

        val ex = NullPointerException("NPE test")
        handler.uncaughtException(Thread.currentThread(), ex)

        assertTrue(handler.crashActivityLaunched)
        assertTrue("Process terminator should have been invoked", terminated)

        val logFile = File(filesDir, GlobalExceptionHandler.CRASH_LOG_FILE)
        assertTrue(logFile.exists())
        assertTrue(logFile.readText().contains("NullPointerException"))
    }
}
