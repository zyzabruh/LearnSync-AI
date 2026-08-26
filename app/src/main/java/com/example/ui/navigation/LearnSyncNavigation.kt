package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.domain.model.Course
import com.example.ui.screens.*
import com.example.ui.viewmodels.LearnSyncViewModel
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Accueil", Icons.Default.Home)
    object Courses : Screen("courses", "Cours", Icons.AutoMirrored.Filled.MenuBook)
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
    val uiState by viewModel.uiState.collectAsState()
    val generationProgress by viewModel.generationProgress.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is LearnSyncViewModel.UiState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearState()
            }
            is LearnSyncViewModel.UiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearState()
            }
            else -> {}
        }
    }

    val screens = listOf(
        Screen.Home,
        Screen.Courses,
        Screen.Review,
        Screen.Calendar,
        Screen.Stats,
        Screen.Profile
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            // Only show bottom bar for top-level destinations
            val isTopLevelDestination = screens.any { it.route == currentRoute }
            if (isTopLevelDestination) {
                NavigationBar {
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
                    onSelectCourse = { course -> navController.navigate("course_detail/${course.id}") },
                    onSyncCalendar = { viewModel.syncToCalendar() }
                )
            }

            composable(Screen.Courses.route) {
                CoursesScreen(
                    courses = courses,
                    generationProgress = generationProgress,
                    onImportCourse = { uri, name -> viewModel.importCourse(uri, name) },
                    onGenerateMaterial = { course -> viewModel.generateMaterial(course) },
                    onSelectCourse = { course -> navController.navigate("course_detail/${course.id}") },
                    onDeleteCourse = { courseId -> viewModel.deleteCourse(courseId) }
                )
            }

            composable(
                route = "course_detail/{courseId}",
                arguments = listOf(navArgument("courseId") { type = NavType.StringType })
            ) { backStackEntry ->
                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                val course = courses.find { it.id == courseId }
                val materials by viewModel.getMaterialsForCourse(courseId).collectAsState(initial = emptyList())
                val courseFlashcards by viewModel.getFlashcardsForCourse(courseId).collectAsState(initial = emptyList())
                val courseQuiz by viewModel.getQuizQuestionsForCourse(courseId).collectAsState(initial = emptyList())

                if (course != null) {
                    CourseDetailScreen(
                        course = course,
                        materials = materials,
                        flashcards = courseFlashcards,
                        quizQuestions = courseQuiz,
                        onBackClick = { navController.popBackStack() },
                        onStartReview = { navController.navigate("course_review/${course.id}") },
                        onStartQuiz = { navController.navigate("course_quiz/${course.id}") },
                        onRegenerate = { viewModel.generateMaterial(course) }
                    )
                }
            }

            composable(Screen.Review.route) {
                ReviewScreen(
                    dueCards = dueFlashcards,
                    onReviewCard = { card, rating, time -> viewModel.reviewCard(card, rating, time) },
                    onFinishReview = { navController.navigate(Screen.Home.route) }
                )
            }

            composable(
                route = "course_review/{courseId}",
                arguments = listOf(navArgument("courseId") { type = NavType.StringType })
            ) { backStackEntry ->
                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                val courseDueFlashcards by viewModel.getDueFlashcardsForCourse(courseId).collectAsState(initial = emptyList())

                ReviewScreen(
                    dueCards = courseDueFlashcards,
                    onReviewCard = { card, rating, time -> viewModel.reviewCard(card, rating, time) },
                    onFinishReview = { navController.popBackStack() }
                )
            }


            composable(
                route = "course_quiz/{courseId}",
                arguments = listOf(navArgument("courseId") { type = NavType.StringType })
            ) { backStackEntry ->
                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                val course = courses.find { it.id == courseId }
                val courseQuiz by viewModel.getQuizQuestionsForCourse(courseId).collectAsState(initial = emptyList())

                QuizScreen(
                    courseTitle = course?.title ?: "Cours",
                    quizQuestions = courseQuiz,
                    onFinishQuiz = { navController.popBackStack() }
                )
            }

            composable(Screen.Calendar.route) {
                CalendarScreen(
                    allFlashcards = allFlashcards,
                    onSyncCalendar = { viewModel.syncToCalendar() }
                )
            }

            composable(Screen.Stats.route) {
                StatsScreen(
                    reviewLogs = reviewLogs,
                    allFlashcards = allFlashcards
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    preferences = preferences,
                    onUpdatePreferences = { prefs -> viewModel.updatePreferences(prefs) },
                    onSyncCloud = { viewModel.syncWithCloud() },
                    onSyncCalendar = { viewModel.syncToCalendar() }
                )
            }
        }
    }
}
