package com.oasismall.oasisai.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.oasismall.oasisai.ui.components.AgentNavIcon

/** Fixed width per tab so all 8 destinations stay tappable and scroll horizontally on narrow phones. */
private val NavItemWidth = 76.dp
/** Material 3 navigation bar height (dp). */
private val NavBarHeight = 80.dp

@Composable
fun SwipeableBottomNavBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    navController: NavHostController,
) {
    val scrollState = rememberScrollState()
    val selectedIndex = items.indexOfFirst { isBottomNavSelected(it.route, currentRoute) }.coerceAtLeast(0)
    val density = LocalDensity.current

    LaunchedEffect(currentRoute, selectedIndex) {
        val itemWidthPx = with(density) { NavItemWidth.roundToPx() }
        val target = ((selectedIndex * itemWidthPx) - itemWidthPx).coerceAtLeast(0)
        scrollState.scrollTo(target)
    }

    Surface(
        tonalElevation = NavigationBarDefaults.Elevation,
        shadowElevation = NavigationBarDefaults.Elevation,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NavBarHeight)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val selected = isBottomNavSelected(item.route, currentRoute)
                    ScrollNavBarItem(
                        item = item,
                        selected = selected,
                        onClick = {
                            val dest = if (item.route == OasisRoute.CheckShoot.route) {
                                OasisRoute.CheckShoot.create()
                            } else {
                                item.route
                            }
                            navController.navigate(dest) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.width(NavItemWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrollNavBarItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textColor = iconColor
    val indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    Column(
        modifier = modifier
            .clickable(onClick = onClick, role = Role.Tab)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) indicatorColor else Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (item.useAgentIcon) {
                AgentNavIcon(selected = selected, modifier = Modifier.size(32.dp))
            } else {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

fun isBottomNavSelected(itemRoute: String, currentRoute: String?): Boolean {
    if (currentRoute == null) return false
    if (itemRoute == OasisRoute.VisioProHome.route) {
        return currentRoute == OasisRoute.VisioProHome.route ||
            currentRoute.startsWith("visio_pro/")
    }
    if (itemRoute == OasisRoute.CheckShoot.route) {
        return currentRoute.startsWith(OasisRoute.CheckShoot.route)
    }
    return currentRoute == itemRoute || currentRoute.startsWith("$itemRoute?")
}

fun showMainBottomBar(currentRoute: String?): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == OasisRoute.ParayLearnSession.route) return false
    if (currentRoute == OasisRoute.VisioProHome.route || currentRoute.startsWith("visio_pro/")) {
        return true
    }
    return bottomNavItems.any { isBottomNavSelected(it.route, currentRoute) }
}
