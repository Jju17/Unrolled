package dev.rahier.unrolled

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder

class UnrolledApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        AlarmReminderScheduler.ensureChannel(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
}
