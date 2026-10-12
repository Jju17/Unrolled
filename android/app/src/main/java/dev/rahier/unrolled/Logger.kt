package dev.rahier.unrolled

import android.util.Log

interface Logger {
    fun warn(tag: String, message: String, error: Throwable? = null)
}

object AndroidLogger : Logger {
    override fun warn(tag: String, message: String, error: Throwable?) {
        Log.w(tag, message, error)
    }
}
