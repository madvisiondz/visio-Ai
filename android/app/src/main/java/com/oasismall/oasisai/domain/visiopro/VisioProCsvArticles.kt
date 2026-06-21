package com.oasismall.oasisai.domain.visiopro

/**
 * Auto-generated from docs/VISIOPRO_CSV_ARTICLES.md + affichage PSD mapping.
 * Generated: 2026-06-19 — re-run scripts/map_visiopro_affichage_psd.py
 */
object VisioProCsvArticles {

    private fun article(
        slug: String,
        csvDesignation: String,
        labelAr: String,
        barcodeSuffix: String,
        printProductAsset: String? = null,
        vararg keywords: String,
    ) = VisioProArticleDef(
        slug = slug,
        labelFr = csvDesignation,
        designationKeywords = keywords.toList(),
        csvDesignation = csvDesignation,
        barcodeSuffix = barcodeSuffix.takeIf { it.isNotBlank() },
        labelAr = labelAr.takeIf { it.isNotBlank() },
        printProductAsset = printProductAsset,
    )

    val fruits: List<VisioProArticleDef> = listOf(
        article("abricot_frais", "ABRICOT FRAIS", "مشمش", "032", "visiopro/fv_print/products/abricot_frais.png", "abricot frais"),
        article("ananas_1pc", "ANANAS 1PC", "أناناس", "010", "visiopro/fv_print/products/ananas_1pc.png", "ananas 1pc"),
        article("banane", "BANANE", "موز", "014", "visiopro/fv_print/products/banane.png", "banane"),
        article("banane_promo", "BANANE PROMO", "موز", "189", "visiopro/fv_print/products/banane_promo.png", "banane promo"),
        article("cerise", "CERISE", "كرز", "098", "visiopro/fv_print/products/cerise.png", "cerise"),
        article("cerise_1ere_choix", "CERISE 1ERE CHOIX", "كرز", "104", "visiopro/fv_print/products/cerise_1ere_choix.png", "cerise 1ere choix"),
        article("citron", "CITRON", "ليمون", "048", "visiopro/fv_print/products/citron.png", "citron"),
        article("coing", "COING", "سفرجل", "090", "visiopro/fv_print/products/coing.png", "coing"),
        article("figue", "FIGUE", "تين", "030", "visiopro/fv_print/products/figue.png", "figue"),
        article("figue_de_barbarie_fruit", "FIGUE DE BARBARIE FRUIT", "تين شوكي", "184", "visiopro/fv_print/products/figue_de_barbarie_fruit.png", "figue de barbarie fruit"),
        article("figue_frais_gm", "FIGUE FRAIS GM", "تين", "100", "visiopro/fv_print/products/figue_frais_gm.png", "figue frais gm"),
        article("figue_pm", "FIGUE PM", "تين", "081", "visiopro/fv_print/products/figue_pm.png", "figue pm"),
        article("fraise", "FRAISE", "فراولة", "077", "visiopro/fv_print/products/fraise.png", "fraise"),
        article("fruit_promo", "FRUIT PROMO", "", "401", null, "fruit promo"),
        article("grenade", "GRENADE", "رمان", "075", "visiopro/fv_print/products/grenade.png", "grenade"),
        article("grenade_1er_choix", "GRENADE 1ER CHOIX", "رمان", "072", "visiopro/fv_print/products/grenade_1er_choix.png", "grenade 1er choix"),
        article("kiwi", "KIWI", "كيوي", "019", "visiopro/fv_print/products/kiwi.png", "kiwi"),
        article("kiwi_fruit", "KIWI FRUIT", "كيوي", "174", "visiopro/fv_print/products/kiwi_fruit.png", "kiwi fruit"),
        article("mandarine", "MANDARINE", "يوسفي", "087", "visiopro/fv_print/products/mandarine.png", "mandarine"),
        article("mandarine_2", "MANDARINE", "يوسفي", "194", "visiopro/fv_print/products/mandarine_2.png", "mandarine"),
        article("mandarine_wilki", "MANDARINE wilki", "", "192", "visiopro/fv_print/products/mandarine_wilki.png", "mandarine wilki"),
        article("mangue_fruit", "MANGUE FRUIT", "مانجو", "178", "visiopro/fv_print/products/mangue_fruit.png", "mangue fruit"),
        article("melon", "MELON", "شمام", "022", "visiopro/fv_print/products/melon.png", "melon"),
        article("melon_cantaloup", "MELON CANTALOUP", "شمام", "021", "visiopro/fv_print/products/melon_cantaloup.png", "melon cantaloup"),
        article("nefle", "NEFLE", "إسكدينية", "079", "visiopro/fv_print/products/nefle.png", "nefle"),
        article("noix_de_coco_fruit", "NOIX DE COCO FRUIT", "جوز الهند", "176", null, "noix de coco fruit"),
        article("orange_1er_choix", "ORANGE 1ER CHOIX", "برتقال", "011", "visiopro/fv_print/products/orange_1er_choix.png", "orange 1er choix"),
        article("orange_2eme_choix", "ORANGE 2EME CHOIX", "برتقال", "065", "visiopro/fv_print/products/orange_2eme_choix.png", "orange 2eme choix"),
        article("orange_p", "ORANGE P", "برتقال", "080", "visiopro/fv_print/products/orange_p.png", "orange p"),
        article("pasteque", "PASTEQUE", "بطيخ", "029", "visiopro/fv_print/products/pasteque.png", "pasteque"),
        article("peche", "PECHE", "خوخ", "035", "visiopro/fv_print/products/peche.png", "peche"),
        article("peche_gm", "PECHE GM", "خوخ", "036", "visiopro/fv_print/products/peche_gm.png", "peche gm"),
        article("peche_plate", "PECHE PLATE", "خوخ", "095", "visiopro/fv_print/products/peche_plate.png", "peche plate"),
        article("pitaya_fruit_du_dragon", "PITAYA/ FRUIT DU DRAGON", "فruit de dragon", "018", "visiopro/fv_print/products/pitaya_fruit_du_dragon.png", "pitaya/ fruit du dragon"),
        article("poire", "POIRE", "إجاص", "023", "visiopro/fv_print/products/poire.png", "poire"),
        article("poire_2", "POIRE 2", "إجاص", "024", "visiopro/fv_print/products/poire_2.png", "poire 2"),
        article("pomme", "POMME", "تفاح", "400", "visiopro/fv_print/products/pomme.png", "pomme"),
        article("pomme_jaune", "POMME JAUNE", "تفاح", "012", "visiopro/fv_print/products/pomme_jaune.png", "pomme jaune"),
        article("pomme_rouge", "POMME ROUGE", "تفاح", "013", "visiopro/fv_print/products/pomme_rouge.png", "pomme rouge"),
        article("prunes_japonaise_abricot", "PRUNES JAPONAISE/ ABRICOT", "برقوق", "078", null, "prunes japonaise/ abricot"),
        article("raisin", "RAISIN", "عنب", "026", "visiopro/fv_print/products/raisin.png", "raisin"),
        article("raisin_blanc", "RAISIN BLANC", "عنب", "025", "visiopro/fv_print/products/raisin_blanc.png", "raisin blanc"),
        article("raisin_rouge", "RAISIN ROUGE", "عنب", "027", "visiopro/fv_print/products/raisin_rouge.png", "raisin rouge"),
    )

    val vegetables: List<VisioProArticleDef> = listOf(
        article("ail", "AIL", "ثوم", "092", "visiopro/fv_print/products/ail.png", "ail"),
        article("ail_vert", "AIL VERT", "ثوم أخضر", "050", "visiopro/fv_print/products/ail_vert.png", "ail vert"),
        article("ail_vert_2", "AIL VERT", "ثوم أخضر", "101", "visiopro/fv_print/products/ail_vert_2.png", "ail vert"),
        article("artichaut_rouge", "ARTICHAUT Rouge", "خرشوف", "066", null, "artichaut rouge"),
        article("artichaut_vert", "ARTICHAUT VERT", "خرشوف", "083", "visiopro/fv_print/products/artichaut_vert.png", "artichaut vert"),
        article("aubergine", "AUBERGINE", "باذنجان", "052", "visiopro/fv_print/products/aubergine.png", "aubergine"),
        article("betterave", "BETTERAVE", "شمندر", "047", "visiopro/fv_print/products/betterave.png", "betterave"),
        article("brocoli", "BROCOLI", "بروكلي", "175", "visiopro/fv_print/products/brocoli.png", "brocoli"),
        article("carotte", "CAROTTE", "جزر", "039", "visiopro/fv_print/products/carotte.png", "carotte"),
        article("celeri_krafs", "CELERI (KRAFS)", "كرافس", "180", "visiopro/fv_print/products/celeri_krafs.png", "celeri (krafs)"),
        article("citrouille", "CITROUILLE", "قرع", "061", "visiopro/fv_print/products/citrouille.png", "citrouille"),
        article("concombre", "CONCOMBRE", "خيار", "057", "visiopro/fv_print/products/concombre.png", "concombre"),
        article("courgette", "COURGETTE", "كوسة", "049", "visiopro/fv_print/products/courgette.png", "courgette"),
        article("epinard_1pcs", "EPINARD 1PCS", "سبانخ", "", null, "epinard 1pcs"),
        article("fenouil", "FENOUIL", "شمر", "038", "visiopro/fv_print/products/fenouil.png", "fenouil"),
        article("haricot_blanc_legume", "HARICOT BLANC LEGUME", "فاصوليا بيضاء", "062", null, "haricot blanc legume"),
        article("haricot_rouge_legume", "HARICOT ROUGE LEGUME", "فاصوليا حمراء", "096", "visiopro/fv_print/products/haricot_rouge_legume.png", "haricot rouge legume"),
        article("haricot_vert_legume", "HARICOT VERT LEGUME", "فاصوليا خضراء", "055", "visiopro/fv_print/products/haricot_vert_legume.png", "haricot vert legume"),
        article("legume_bio", "LEGUME BIO", "خضر", "088", null, "legume bio"),
        article("legume_promo", "LEGUME PROMO", "خضر", "357", null, "legume promo"),
        article("menthe_thyme", "MENTHE / THYME", "نعناع", "011", null, "menthe / thyme"),
        article("oignon", "OIGNON", "بصل", "058", "visiopro/fv_print/products/oignon.png", "oignon"),
        article("piment_fort", "PIMENT FORT", "فلفل حار", "054", "visiopro/fv_print/products/piment_fort.png", "piment fort"),
        article("piment_fort_2", "PIMENT FORT", "فلفل حار", "102", "visiopro/fv_print/products/piment_fort_2.png", "piment fort"),
        article("piment_fort_starteur", "PIMENT FORT STARTEUR", "فلفل حار", "084", "visiopro/fv_print/products/piment_fort_starteur.png", "piment fort starteur"),
        article("poivron", "POIVRON", "فلفل", "053", "visiopro/fv_print/products/poivron.png", "poivron"),
        article("poivron_promo", "POIVRON PROMO", "فلفل", "377", "visiopro/fv_print/products/poivron_promo.png", "poivron promo"),
        article("poivron_rouge", "POIVRON ROUGE", "فلفل أحمر", "069", "visiopro/fv_print/products/poivron_rouge.png", "poivron rouge"),
        article("tomate", "TOMATE", "طماطم", "041", "visiopro/fv_print/products/tomate.png", "tomate"),
    )

    fun fruitsAndVegetables(category: VisioProCategory): List<VisioProArticleDef> = when (category) {
        VisioProCategory.FRUITS -> fruits
        VisioProCategory.VEGETABLES -> vegetables
        else -> emptyList()
    }
}
