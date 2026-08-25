package `in`.gov.tribalfln.util

import android.graphics.Bitmap
import android.util.Log

/**
 * BitmapPoolManager — Reuses and pools bitmap allocations to reduce GC pressure
 * on low-spec Android Go devices with 2GB RAM and 180MB heap ceiling.
 */
object BitmapPoolManager {

    private const val TAG = "BitmapPoolManager"
    private const val MAX_POOL_SIZE = 10

    private val pool = mutableListOf<Bitmap>()
    private val lock = Any()
    private var totalAllocatedBytes = 0L

    /**
     * Get a pooled bitmap of the specified dimensions, or create a new one.
     */
    fun acquire(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        synchronized(lock) {
            val targetSize = width * height * when (config) {
                Bitmap.Config.ARGB_8888 -> 4
                Bitmap.Config.RGB_565 -> 2
                Bitmap.Config.ALPHA_8 -> 1
                else -> 4
            }

            // Try to find a reusable bitmap in the pool
            val iterator = pool.iterator()
            while (iterator.hasNext()) {
                val bitmap = iterator.next()
                if (!bitmap.isRecycled && bitmap.width == width && bitmap.height == height && bitmap.config == config) {
                    iterator.remove()
                    totalAllocatedBytes -= bitmap.allocationByteCount.toLong()
                    Log.d(TAG, "Reused bitmap ${width}x${height} from pool")
                    return bitmap
                }
            }

            // No reusable bitmap found, create new
            val bitmap = Bitmap.createBitmap(width, height, config)
            totalAllocatedBytes += bitmap.allocationByteCount.toLong()
            Log.d(TAG, "Created new bitmap ${width}x${height}")
            return bitmap
        }
    }

    /**
     * Return a bitmap to the pool for reuse.
     */
    fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        synchronized(lock) {
            if (pool.size < MAX_POOL_SIZE) {
                pool.add(bitmap)
                Log.d(TAG, "Bitmap returned to pool (${pool.size}/${MAX_POOL_SIZE})")
            } else {
                bitmap.recycle()
                Log.d(TAG, "Pool full, bitmap recycled")
            }
        }
    }

    /**
     * Clear all pooled bitmaps.
     */
    fun clearPool() {
        synchronized(lock) {
            pool.forEach { if (!it.isRecycled) it.recycle() }
            pool.clear()
            totalAllocatedBytes = 0
        }
        Log.d(TAG, "Pool cleared")
    }

    /**
     * Get current pool statistics.
     */
    fun getStats(): PoolStats {
        synchronized(lock) {
            return PoolStats(pool.size, totalAllocatedBytes)
        }
    }

    data class PoolStats(val count: Int, val totalBytes: Long) {
        fun toLogString(): String = "BitmapPool: $count items, ${totalBytes / 1024}KB allocated"
    }
}
