package app.olsaathi.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NetworkGuardTest {

    @Test
    fun concurrentIncrementsAreNeverLost() {
        NetworkGuard.reset()
        val threads = 8
        val reps = 2000
        val start = CountDownLatch(1)
        val finished = CountDownLatch(threads)

        repeat(threads) {
            Thread {
                start.await()
                try {
                    repeat(reps) { NetworkGuard.recordNetworkCall() }
                } finally {
                    finished.countDown()
                }
            }.start()
        }

        start.countDown()
        assertTrue("adders did not finish in time", finished.await(30, TimeUnit.SECONDS))

        // Every increment must count: a lost update here would read less
        // than threads * reps and fail exactly the way the Proof screen
        // would misreport a network call as zero.
        assertEquals(threads * reps, NetworkGuard.callCount)
        NetworkGuard.reset()
    }

    @Test
    fun resetReturnsCounterToZero() {
        NetworkGuard.reset()
        NetworkGuard.recordNetworkCall()
        NetworkGuard.recordNetworkCall()
        assertEquals(2, NetworkGuard.callCount)
        NetworkGuard.reset()
        assertEquals(0, NetworkGuard.callCount)
    }
}