package com.example.viewmodel

import java.io.File

/** File-ownership rules for an editor draft, separated for deterministic JVM tests. */
internal object DraftFileCleanup {
    fun discard(existingSavedPath: String?, temporaryPaths: Iterable<String?>) {
        temporaryPaths.filterNotNull().toSet()
            .filter { it != existingSavedPath }
            .forEach { path -> try { File(path).delete() } catch (_: Exception) {} }
    }
}
