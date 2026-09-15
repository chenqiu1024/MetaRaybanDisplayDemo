package com.example.datplayground.camera

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.util.concurrent.LinkedBlockingQueue

/**
 * Minimal HEVC (H.265) elementary-stream decoder rendering into a [Surface].
 *
 * The DAT camera stream does NOT hand you bitmaps — with `compressVideo = true`
 * it emits compressed HEVC access units ([com.meta.wearable.dat.camera.types.VideoFrame]),
 * and decoding them is the app's job. MediaCodec renders straight into the
 * Surface, so the GPU does the YUV->RGB work.
 *
 * Simplified from the official CameraAccess sample's HevcDecoder (BSD license).
 */
class HevcDecoder {

  private data class Frame(val data: ByteArray, val ptsUs: Long, val isConfig: Boolean)

  private val queue = LinkedBlockingQueue<Frame>(64)
  @Volatile private var running = false
  private var thread: Thread? = null

  fun start(width: Int, height: Int, surface: Surface) {
    if (running) return
    running = true
    thread =
        Thread {
          var codec: MediaCodec? = null
          try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)
            codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
            codec.configure(format, surface, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            while (running) {
              val frame = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
              if (frame != null) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                  codec.getInputBuffer(inIndex)?.apply {
                    clear()
                    put(frame.data)
                  }
                  val flags = if (frame.isConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
                  codec.queueInputBuffer(inIndex, 0, frame.data.size, frame.ptsUs, flags)
                }
              }
              // Drain: render every available output buffer to the Surface.
              var outIndex = codec.dequeueOutputBuffer(info, 0)
              while (outIndex >= 0) {
                codec.releaseOutputBuffer(outIndex, /* render = */ true)
                outIndex = codec.dequeueOutputBuffer(info, 0)
              }
            }
          } catch (t: Throwable) {
            Log.e(TAG, "Decoder loop failed", t)
          } finally {
            try {
              codec?.stop()
              codec?.release()
            } catch (t: Throwable) {
              Log.w(TAG, "Decoder release failed", t)
            }
          }
        }
            .also { it.start() }
  }

  fun decodeFrame(data: ByteArray, ptsUs: Long, isConfig: Boolean) {
    if (!running) return
    queue.offer(Frame(data, ptsUs, isConfig)) // drop frames if the queue is full
  }

  fun stop() {
    running = false
    thread?.join(500)
    thread = null
    queue.clear()
  }

  private companion object {
    const val TAG = "HevcDecoder"
  }
}
