package com.oasismall.oasisai.domain.design

import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle

fun PreselectionWithArticle.shelfDisplayPrice(): Double =
    if (isPromoTicket) promoPrice ?: price else price

fun PreselectionWithArticle.shelfOriginalPrice(): Double? =
    if (!isPromoTicket) null else promoOriginalPrice ?: previousPrice ?: price.takeIf { it > 0 }

fun PreselectionWithArticle.isPromoShelfTicket(): Boolean =
    isPromoTicket && shelfOriginalPrice() != null
