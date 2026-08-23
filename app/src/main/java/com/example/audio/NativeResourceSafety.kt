package com.example.audio

/** Small, platform-independent release primitive kept testable on the JVM. */
internal object NativeResourceSafety {
    inline fun stopAndRelease(stop: () -> Unit, release: () -> Unit) {
        try {
            stop()
        } finally {
            release()
        }
    }
}
