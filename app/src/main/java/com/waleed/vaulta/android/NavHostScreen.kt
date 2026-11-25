package com.waleed.vaulta.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.waleed.vaulta.android.feature.add_expense.AddExpense
import com.waleed.vaulta.android.feature.auth.LoginScreen
import com.waleed.vaulta.android.feature.auth.PrivacyPolicyConsentScreen
import com.waleed.vaulta.android.feature.auth.RegisterScreen
import com.waleed.vaulta.android.feature.budget.SpendsDashboardScreen
import com.waleed.vaulta.android.feature.home.HomeScreen
import com.waleed.vaulta.android.feature.profile.ProfileScreen
import com.waleed.vaulta.android.feature.splash.SplashScreen
import com.waleed.vaulta.android.feature.stats.StatsScreen
import com.waleed.vaulta.android.feature.transactionlist.TransactionListScreen
import com.waleed.vaulta.android.ui.theme.Zinc
import com.waleed.vaulta.android.utils.NavRouts
import com.waleed.vaulta.android.widget.VaultaSnackbarHost

@Composable
fun NavHostScreen(
    isLoggedIn: Boolean = false,
    isPrivacyPolicyAccepted: Boolean = false
) {
    val navController = rememberNavController()
    var bottomBarVisibility by remember {
        mutableStateOf(false)
    }
    val startDest = NavRouts.splash

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(bottomBar = {
            AnimatedVisibility(visible = bottomBarVisibility) {
                NavigationBottomBar(
                    navController = navController,
                    items = listOf(
                        NavItem(route = "/home", icon = R.drawable.ic_home),
                        NavItem(route = "/stats", icon = R.drawable.ic_stats),
                        NavItem(route = "/profile", icon = R.drawable.ic_profile)
                    )
                )
            }
        }) {
            NavHost(
                navController = navController,
                startDestination = startDest,
                modifier = Modifier.padding(it)
            ) {
                composable(route = NavRouts.splash) {
                    bottomBarVisibility = false
                    SplashScreen(
                        navController = navController,
                        isLoggedIn = isLoggedIn,
                        isPrivacyPolicyAccepted = isPrivacyPolicyAccepted
                    )
                }

                composable(route = NavRouts.privacy_policy_consent) {
                    bottomBarVisibility = false
                    PrivacyPolicyConsentScreen(navController = navController)
                }

                composable(route = "/login") {
                    bottomBarVisibility = false
                    LoginScreen(navController)
                }

                composable(route = "/signup") {
                    bottomBarVisibility = false
                    RegisterScreen(navController)
                }

                composable(route = "/home") {
                    bottomBarVisibility = true
                    HomeScreen(navController)
                }

                composable(route = "/add_income") {
                    bottomBarVisibility = false
                    AddExpense(navController, isIncome = true)
                }

                composable(route = "/add_exp") {
                    bottomBarVisibility = false
                    AddExpense(navController, isIncome = false)
                }

                composable(route = "/stats") {
                    bottomBarVisibility = true
                    StatsScreen(navController)
                }

                composable(route = NavRouts.spends_dashboard) {
                    bottomBarVisibility = false
                    SpendsDashboardScreen(navController)
                }

                composable(route = "/all_transactions") {
                    bottomBarVisibility = true
                    TransactionListScreen(navController)
                }

                composable(route = "/profile") {
                    bottomBarVisibility = true
                    ProfileScreen(navController)
                }
            }
        }

        VaultaSnackbarHost(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (bottomBarVisibility) 74.dp else 24.dp)
                .zIndex(999f)
        )
    }
}


data class NavItem(
    val route: String,
    val icon: Int
)

@Composable
fun NavigationBottomBar(
    navController: NavController,
    items: List<NavItem>
) {
    // Bottom Navigation Bar - Slim & Compact (62.dp)
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    BottomAppBar(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(id = item.icon),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                    unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                    unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}
