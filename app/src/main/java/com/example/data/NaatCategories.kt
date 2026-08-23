package com.example.data

/**
 * Single source of truth for the entry taxonomy.
 *
 * The folder grid, the add/edit dropdown, the database migration and the
 * backup-restore path all read from this object, so the category list can
 * never drift between screens again.
 *
 * The old taxonomy mixed content types, ownership and media formats
 * ("Hamd-o-Naat", "Salam & Qasida", "My Own Poetry", "Audio Only").
 * "Audio Only" described a media format, not a category, and is gone:
 * an entry that has a recording but no lyrics simply lives in whichever
 * category the user picked (Naat, Hamd, ...).
 */
object NaatCategories {

    const val NAAT = "Naat"
    const val HAMD = "Hamd"
    const val MANQABAT = "Manqabat"
    const val SALAM = "Salam"
    const val QASIDA = "Qasida"
    const val NASHEED = "Nasheed"
    const val MY_KALAM = "My Kalam"
    const val OTHERS = "Others"

    /** Order shown in the folder grid and in the add/edit dropdown. */
    val ALL: List<String> = listOf(NAAT, HAMD, MANQABAT, SALAM, QASIDA, NASHEED, MY_KALAM, OTHERS)

    /** Pre-selected category when adding a brand-new entry. */
    val DEFAULT: String = NAAT

    /**
     * Legacy folder names -> new taxonomy. Applied by the Room migration for
     * existing installs and by BackupManager when restoring old backups.
     */
    private val LEGACY_MAP: Map<String, String> = mapOf(
        "hamd-o-naat" to NAAT,      // mixed bucket; entries mostly naats (user can re-file)
        "salam & qasida" to SALAM,  // merged bucket; Salam kept (user can re-file to Qasida)
        "my own poetry" to MY_KALAM,
        "audio only" to MY_KALAM    // user's own voice recordings
    )

    /**
     * Maps any stored/restored category string onto the current taxonomy.
     * Matching is case-insensitive and legacy names are upgraded; anything
     * unknown falls back to [OTHERS].
     */
    fun normalize(category: String?): String {
        if (category.isNullOrBlank()) return DEFAULT
        val trimmed = category.trim()
        ALL.firstOrNull { it.equals(trimmed, ignoreCase = true) }?.let { return it }
        return LEGACY_MAP[trimmed.lowercase()] ?: OTHERS
    }

    /** True when [category] is already one of the current taxonomy's names. */
    fun isCurrent(category: String): Boolean = ALL.any { it.equals(category, ignoreCase = true) }
}
