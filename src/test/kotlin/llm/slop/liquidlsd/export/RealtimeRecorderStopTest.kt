package llm.slop.liquidlsd.export

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Quitting while a recording is being written out: `stopRecording(waitForFinish = true)` joins the finish thread, and
 * that thread takes the recorder's lock (`resetAudioPool`). The wait must not hold the lock, or both block for ever.
 */
class RealtimeRecorderStopTest {

    @Test
    fun waitForFinishReturnsWhileTheFinishThreadNeedsTheLock() {
        val resetAudioPool = RealtimeRecorder::class.java.getDeclaredMethod("resetAudioPool").apply { isAccessible = true }
        val finishField = RealtimeRecorder::class.java.getDeclaredField("finishThread").apply { isAccessible = true }

        val started = CountDownLatch(1)
        val finish = Thread({
            started.countDown()
            Thread.sleep(200)   // give stopRecording time to start waiting
            resetAudioPool.invoke(RealtimeRecorder)
        }, "test-finish").apply { isDaemon = true }
        finishField.set(RealtimeRecorder, finish)
        finish.start()
        started.await()

        val done = CountDownLatch(1)
        val caller = Thread({
            RealtimeRecorder.stopRecording(waitForFinish = true)
            done.countDown()
        }, "test-quit").apply { isDaemon = true }
        caller.start()

        assertTrue(done.await(10, TimeUnit.SECONDS), "stopRecording(waitForFinish = true) deadlocked")
        finish.join(1000)
        assertFalse(finish.isAlive)
        finishField.set(RealtimeRecorder, null)
    }
}
