package com.example.viewmodel

import java.io.File

/** File-ownership rules for an editor draft, separated for deterministic JVM tests. */
internal object DraftFileCleanup {
    fun discard(existingSavedPaths: Iterable<String?>, temporaryPaths: Iterable<String?>) {
        val protectedPaths = existingSavedPaths.filterNotNull().toSet()
        temporaryPaths.filterNotNull().toSet()
            .filter { it !in protectedPaths }
            .forEach { path -> try { File(path).delete() } catch (_: Exception) {} }
    }
}
