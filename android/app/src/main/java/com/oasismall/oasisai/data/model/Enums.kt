package com.oasismall.oasisai.data.model

enum class ArticleChangeStatus {
    UNCHANGED,
    NEW,
    PRICE_CHANGED,
    RENAMED,
    REMOVED,
    NEEDS_TICKET,
}

enum class ImageStatus {
    FOUND,
    MISSING,
    NEEDS_REVIEW,
    MULTIPLE_MATCHES,
}

enum class ImportStatus {
    PENDING,
    COMPLETED,
    FAILED,
}

enum class TemplateType {
    SHELF,
    FREEZER,
    PODIUM,
    BOARD,
}

enum class PrintBatchStatus {
    GENERATED,
    PRINTED,
    PLACED,
}

enum class PromoAlertStatus {
    PENDING,
    DISMISSED,
    EXPIRED,
}

enum class ImportChangeType {
    NEW,
    PRICE_CHANGED,
    RENAMED,
    REMOVED,
    UNCHANGED,
}

/** Working lists on Home — photoshoot queue vs ready-to-share gallery assets. */
enum class CartType {
    PHOTOSHOOT,
    SHARE,
    /** Design shelf layout queue (from To share). */
    DESIGN,
    /** Printed or sent — pull up back to [DESIGN]. */
    DESIGN_DONE,
}
