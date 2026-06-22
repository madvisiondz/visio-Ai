package com.oasismall.oasisai.domain.paray

/**
 * Read-side access to brand / family knowledge for AGENT recognition.
 * V1: architecture hook only — consumers may apply 0f scoring until V2.
 */
fun interface BrandKnowledgeProvider {
    fun getBrandKnowledge(articleId: Long): ParayBrandKnowledge?

    companion object {
        /** Safe fallback when brand knowledge is unavailable. */
        val None: BrandKnowledgeProvider = BrandKnowledgeProvider { null }
    }
}

/** Brand + family context resolved for one catalog article. */
data class ParayBrandKnowledge(
    val articleId: Long,
    val brand: String,
    val family: String,
    val productCount: Int = 0,
    val brandSignature: ParayVisualSignatures? = null,
    val familySignature: ParayVisualSignatures? = null,
)

/**
 * Resolves article → learn record brand/family → [ParayBrandKnowledgeStore] entry.
 */
class ParayBrandKnowledgeProvider(
    private val brandStore: ParayBrandKnowledgeStore,
    private val learnStore: ParayLearnStore,
) : BrandKnowledgeProvider {
    override fun getBrandKnowledge(articleId: Long): ParayBrandKnowledge? {
        val record = learnStore.get(articleId) ?: return null
        val brand = record.brand?.trim().orEmpty()
        val family = record.family?.trim().orEmpty()
        if (brand.isBlank() && family.isBlank()) return null
        val entry = brandStore.getEntry(brand, family)
        return ParayBrandKnowledge(
            articleId = articleId,
            brand = brand,
            family = family,
            productCount = entry?.productCount ?: 0,
            brandSignature = entry?.brandSignature ?: record.brandSignature,
            familySignature = entry?.familySignature ?: record.familySignature,
        )
    }
}
