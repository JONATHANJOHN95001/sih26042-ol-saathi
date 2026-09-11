package app.olsaathi.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LatencyLogTest {

    @Test
    fun boundHoldsAtExactlyTwentyAfterManyAdds() {
        val log = LatencyLog()
        repeat(100) { log.add(it.toLong()) }
        assertEquals(20, log.snapshot().size)
    }

    @Test
    fun oldestIsEvictedNotNewest() {
        val log = LatencyLog()
        repeat(100) { log.add(it.toLong()) }
        val snapshot = log.snapshot()
        assertEquals(80L, snapshot.first())
        assertEquals(99L, snapshot.last())
        assertFalse(snapshot.contains(0L))
        assertFalse(snapshot.contains(79L))
    }

    @Test
    fun snapshotTakenBeforeFurtherAddsDoesNotChange() {
        val log = LatencyLog()
        log.add(1L)
        log.add(2L)
        log.add(3L)
        val before = log.snapshot()
        repeat(50) { log.add((it + 10).toLong()) }
        assertEquals(listOf(1L, 2L, 3L), before)
        // the live state moved on; the snapshot did not
        assertFalse(log.snapshot().contains(1L))
    }

    @Test
    fun concurrentAddsWhileSnapshotsNeverCorruptState() {
        val log = LatencyLog()
        val adders = 8
        val addsPerAdder = 200
        val start = CountDownLatch(1)
        val finished = CountDownLatch(adders)

        val threads = (0 until adders).map { id ->
            Thread {
                start.await()
                try {
                    repeat(addsPerAdder) { i ->
                        log.add((id * addsPerAdder + i).toLong())
                    }
                } finally {
                    finished.countDown()
                }
            }
        }
        threads.forEach { it.start() }

        // Snapshots from this thread while the adders are still writing, so
        // the test actually contends rather than merely running concurrently.
        // Any ConcurrentModificationException or torn read surfaces here.
        start.countDown()
        val seenDuringRun = mutableListOf<List<Long>>()
        while (!finished.await(5, TimeUnit.MILLISECONDS)) {
            seenDuringRun.add(log.snapshot())
        }
        threads.forEach { it.join() }

        val finalSnapshot = log.snapshot()
        assertEquals(20, finalSnapshot.size)
        seenDuringRun.forEach { assertTrue(it.size in 0..20) }
    }
}