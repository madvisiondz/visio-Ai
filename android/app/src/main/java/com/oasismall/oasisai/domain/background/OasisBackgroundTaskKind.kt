package com.oasismall.oasisai.domain.background

enum class OasisBackgroundTaskKind(val displayName: String) {
    SYNC_SUB_PNGS("Sync sub-PNGs"),
    REINDEX_IMAGES("Re-index images"),
    EXPORT_PNG_DATABASE("Export PNG database"),
    EXPORT_FULL_BACKUP("Export full backup"),
    IMPORT_FULL_BACKUP("Import full backup"),
    EXPORT_VISIOPRO_BUNDLE("Export VisioPRO presets"),
    IMPORT_VISIOPRO_BUNDLE("Import VisioPRO presets"),
    PURGE_GESTIUM("Purge Gestium catalog"),
    LOAD_SAMPLE_DATA("Load sample data"),
    LOAD_READY_PNGS("Load Oasis PNGs"),
    CSV_IMPORT("CSV import"),
}
