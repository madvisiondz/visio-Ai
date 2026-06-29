package com.oasismall.oasisai.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Smoke tests for bottom navigation route matching (no Compose / device required). */
class BottomNavSelectionTest {

    @Test
    fun bottomNav_hasEightPrimaryDestinations() {
        assertEquals(8, bottomNavItems.size)
    }

    @Test
    fun visioProHome_selectedOnHomeAndCategoryRoutes() {
        assertTrue(isBottomNavSelected(OasisRoute.VisioProHome.route, OasisRoute.VisioProHome.route))
        assertTrue(isBottomNavSelected(OasisRoute.VisioProHome.route, "visio_pro/FRUITS"))
        assertFalse(isBottomNavSelected(OasisRoute.VisioProHome.route, OasisRoute.Home.route))
    }

    @Test
    fun articles_selectedOnHomeOnly() {
        assertTrue(isBottomNavSelected(OasisRoute.Home.route, OasisRoute.Home.route))
        assertFalse(isBottomNavSelected(OasisRoute.Home.route, "visio_pro_home"))
    }

    @Test
    fun agent_selectedOnCheckShootWithQueryParams() {
        assertTrue(isBottomNavSelected(OasisRoute.CheckShoot.route, "check_shoot?barcode=123"))
        assertFalse(isBottomNavSelected(OasisRoute.CheckShoot.route, OasisRoute.Home.route))
    }

    @Test
    fun showMainBottomBar_onAllPrimaryTabs() {
        bottomNavItems.forEach { item ->
            assertTrue(
                "Expected bar visible on ${item.route}",
                showMainBottomBar(item.route),
            )
        }
    }

    @Test
    fun showMainBottomBar_onVisioProCategory() {
        assertTrue(showMainBottomBar("visio_pro/LEGUMES"))
    }

    @Test
    fun showMainBottomBar_hiddenOnArticleDetail() {
        assertFalse(showMainBottomBar("article/42"))
        assertFalse(showMainBottomBar("import/1"))
    }
}
