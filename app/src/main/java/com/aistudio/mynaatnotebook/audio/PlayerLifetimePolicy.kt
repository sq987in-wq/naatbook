package com.aistudio.mynaatnotebook.audio

/** Process-singleton player policy for MediaSessionService teardown. */
internal object PlayerLifetimePolicy {
    fun shouldEnsureIdleOnServiceDestroy(engineIdle: Boolean, previewActive: Boolean): Boolean =
        !engineIdle && !previewActive
}
