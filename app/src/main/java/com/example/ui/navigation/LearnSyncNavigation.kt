package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.ui.screens.*
import com.example.ui.viewmodels.LearnSyncViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Accueil", Icons.Default.Home)
    object Courses : Screen("courses", "Cours", Icons.Default.MenuBook)
    object Review : Screen("review", "Réviser", Icons.Default.School)
    object Calendar : Screen("calendar", "Calendrier", Icons.Default.CalendarMonth)
    object Stats : Screen("stats", "Stats", Icons.Default.BarChart)
    object Profile : Screen("profile", "Profil", Icons.Default.Person)
}

@Composable
fun LearnSyncNavigation(viewModel: LearnSyncViewModel) {
    val navController = rememberNavController()
    val courses by viewModel.courses.collectAsState()
    val dueFlashcards by viewModel.dueFlashcards.collectAsState()
    val allFlashcards by viewModel.allFlashcards.collectAsState()
    val reviewLogs by viewModel.reviewLogs.collectAsState()
    val preferences by viewModel.preferences.collectAsState()

    val screens = listOf(
        Screen.Home,
        Screen.Courses,
        Screen.Review,
        Screen.Calendar,
        Screen.Stats,
        Screen.Profile
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    courses = courses,
                    dueCards = dueFlashcards,
                    reviewLogs = reviewLogs,
                    onNavigateToReview = { navController.navigate(Screen.Review.route) },
                    onNavigateToCourses = { navController.navigate(Screen.Courses.route) },
                    onImportClick = { navController.navigate(Screen.Courses.route) }
                )
            }
            composable(Screen.Courses.route) {
                CoursesScreen(
                    courses = courses,
                    onImportCourse = { uri, name -> viewModel.importCourse(uri, name) },
                    onGenerateMaterial = { course -> viewModel.generateMaterial(course) },
                    onDeleteCourse = { courseId -> viewModel.deleteCourse(courseId) }
                )
            }
            composable(Screen.Review.route) {
                ReviewScreen(
                    dueCards = dueFlashcards,
                    onReviewCard = { card, rating, time -> viewModel.reviewCard(card, rating, time) },
                    onFinishReview = { navController.navigate(Screen.Home.route) }
                )
            }
            composable(Screen.Calendar.route) {
                CalendarScreen(allFlashcards = allFlashcards)
            }
            composable(Screen.Stats.route) {
                StatsScreen(reviewLogs = reviewLogs, allFlashcards = allFlashcards)
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    preferences = preferences,
                    onUpdatePreferences = { prefs -> viewModel.updatePreferences(prefs) }
                )
            }
        }
    }
}
