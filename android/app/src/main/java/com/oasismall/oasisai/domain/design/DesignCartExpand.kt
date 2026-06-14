package com.oasismall.oasisai.domain.design

import com.oasismall.oasisai.data.db.dao.PreselectionWithArticle

object DesignCartExpand {
    fun expandCopies(items: List<PreselectionWithArticle>): List<PreselectionWithArticle> =
        items.flatMap { item ->
            List(item.copyCount.coerceIn(1, 99)) { item }
        }

    fun labelCount(items: List<PreselectionWithArticle>): Int = expandCopies(items).size
}
