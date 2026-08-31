package com.learnsyncai.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.learnsyncai.domain.model.Course
import com.learnsyncai.ui.screens.*
import com.learnsyncai.ui.theme.*
import com.learnsyncai.ui.viewmodels.LearnSyncViewModel
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Accueil", Icons.Default.Home)
    object Courses : Screen("courses", "Cours", Icons.AutoMirrored.Filled.MenuBook)
    object Review : Screen("review", "Réviser", Icons.Default.School)
    object Stats : Screen("stats", "Stats", Icons.Default.BarChart)
    object Profile : Screen("profile", "Profil", Icons.Default.Person)
    object Calendar : Screen("calendar", "Calendrier", Icons.Default.CalendarMonth)
}

@Composable
fun LearnSyncNavigation(viewModel: LearnSyncViewModel) {
    val navController = rememberNavController()
    val courses by viewModel.courses.collectAsState()
    val dueFlashcards by viewModel.dueFlashcards.collectAsState()
    val allFlashcards by viewModel.allFlashcards.collectAsState()
    val reviewLogs by viewModel.reviewLogs.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val aiProfiles by viewModel.aiProfiles.collectAsState()
    val activeAiProfile by viewModel.activeAiProfile.collectAsState()
    val hasValidAiConfig by viewModel.hasValidAiConfig.collectAsState()
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

    // 5 Primary mobile bottom navigation items
    val mainBottomScreens = listOf(
        Screen.Home,
        Screen.Courses,
        Screen.Review,
        Screen.Stats,
        Screen.Profile
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val isTopLevel = mainBottomScreens.any { it.route == currentRoute }

                if (isTopLevel && !isExpanded) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        mainBottomScreens.forEach { screen ->
                            val isSelected = currentRoute == screen.route
                            val isReview = screen == Screen.Review

                            NavigationBarItem(
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (isReview && dueFlashcards.isNotEmpty()) {
                                                Badge(
                                                    containerColor = AmberFlame,
                                                    contentColor = Slate900
                                                ) {
                                                    Text("${dueFlashcards.size}")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = screen.icon,
                                            contentDescription = screen.title,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = screen.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                selected = isSelected,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Adaptive Navigation Rail for Tablets / Wide Screens
                if (isExpanded) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(LearnSyncSpacing.medium))
                        mainBottomScreens.forEach { screen ->
                            val isSelected = currentRoute == screen.route
                            val isReview = screen == Screen.Review

                            NavigationRailItem(
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (isReview && dueFlashcards.isNotEmpty()) {
                                                Badge(containerColor = AmberFlame) {
                                                    Text("${dueFlashcards.size}")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(screen.icon, contentDescription = screen.title)
                                    }
                                },
                                label = { Text(screen.title) },
                                selected = isSelected,
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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home.route
                        ) {
                            composable(Screen.Home.route) {
                                HomeScreen(
                                    courses = courses,
                                    dueCards = dueFlashcards,
                                    allFlashcards = allFlashcards,
                                    reviewLogs = reviewLogs,
                                    dailyGoal = preferences.dailyGoal,
                                    onNavigateToReview = { navController.navigate(Screen.Review.route) },
                                    onNavigateToCourses = { navController.navigate(Screen.Courses.route) },
                                    onNavigateToStats = { navController.navigate(Screen.Stats.route) },
                                    onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                                    onSelectCourse = { course -> navController.navigate("course_detail/${course.id}") },
                                    onSyncCalendar = { viewModel.syncToCalendar() }
                                )
                            }

                            composable(Screen.Courses.route) {
                                CoursesScreen(
                                    courses = courses,
                                    allFlashcards = allFlashcards,
                                    dueCards = dueFlashcards,
                                    hasValidAiConfig = hasValidAiConfig,
                                    onImportCourse = { uri, name -> viewModel.importCourse(uri, name) },
                                    onImportFromUrl = { url -> viewModel.importCourseFromUrl(url) },
                                    onGenerateMaterial = { course -> viewModel.generateMaterial(course) },
                                    onSelectCourse = { course -> navController.navigate("course_detail/${course.id}") },
                                    onDeleteCourse = { courseId -> viewModel.deleteCourse(courseId) },
                                    onUpdateCourseTag = { courseId, tag -> viewModel.updateCourseTag(courseId, tag) },
                                    onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                                    onNavigateToSearch = { navController.navigate("search") },
                                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                                    onReviewCourse = { courseId -> navController.navigate("course_review/$courseId") }
                                )
                            }

                            composable("search") {
                                val allQuiz by viewModel.allQuizQuestions.collectAsState()
                                val allMaterials by viewModel.allMaterials.collectAsState()
                                SearchScreen(
                                    courses = courses,
                                    flashcards = allFlashcards,
                                    quizQuestions = allQuiz,
                                    materials = allMaterials,
                                    onBackClick = { navController.popBackStack() },
                                    onSelectResult = { courseId -> navController.navigate("course_detail/$courseId") }
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
                                    val coursePreview by viewModel.getCoursePreview(course.id)
                                        .collectAsState(initial = "")
                                    CourseDetailScreen(
                                        course = course,
                                        materials = materials,
                                        flashcards = courseFlashcards,
                                        quizQuestions = courseQuiz,
                                        generationProgress = generationProgress,
                                        activeAiProfile = activeAiProfile,
                                        coursePreview = coursePreview,
                                        onBackClick = { navController.popBackStack() },
                                        onStartReview = { navController.navigate("course_review/${course.id}") },
                                        onStartQuiz = { navController.navigate("course_quiz/${course.id}") },
                                        onRegenerate = { viewModel.generateMaterial(course) },
                                        onDeleteCourse = {
                                            viewModel.deleteCourse(course.id)
                                            navController.popBackStack()
                                        },
                                        onExportCsv = { uri -> viewModel.exportCourseToCsv(uri, course.id) },
                                        onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                                        onAddFlashcard = { q, a, exp -> viewModel.addCustomFlashcard(course.id, q, a, exp) },
                                        onDeleteFlashcard = { cardId -> viewModel.deleteFlashcard(cardId) },
                                        onAddQuizQuestion = { q, opts, ans, exp -> viewModel.addCustomQuizQuestion(course.id, q, opts, ans, exp) },
                                        onDeleteQuizQuestion = { qId -> viewModel.deleteQuizQuestion(qId) },
                                        onSaveSummary = { summary -> viewModel.saveCustomSummary(course.id, summary) },
                                        onAddKeyPoint = { point -> viewModel.addCustomKeyPoint(course.id, point) },
                                        onRemoveKeyPoint = { point -> viewModel.removeCustomKeyPoint(course.id, point) }
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
                                    courses = courses,
                                    onSyncCalendar = { viewModel.syncToCalendar() },
                                    onBackClick = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.Stats.route) {
                                StatsScreen(
                                    reviewLogs = reviewLogs,
                                    allFlashcards = allFlashcards,
                                    courses = courses
                                )
                            }

                            composable(Screen.Profile.route) {
                                ProfileScreen(
                                    preferences = preferences,
                                    aiProfiles = aiProfiles,
                                    activeAiProfile = activeAiProfile,
                                    onUpdatePreferences = { prefs -> viewModel.updatePreferences(prefs) },
                                    onAddAiProfile = { name, provider, baseUrl, apiKey, modelName ->
                                        viewModel.addAiProfile(name, provider, baseUrl, apiKey, modelName)
                                    },
                                    onUpdateAiProfile = { profile -> viewModel.updateAiProfile(profile) },
                                    onDeleteAiProfile = { profileId -> viewModel.deleteAiProfile(profileId) },
                                    onSetActiveAiProfile = { profileId -> viewModel.setActiveAiProfile(profileId) },
                                    onSyncCloud = { viewModel.syncWithCloud() },
                                    onSyncCalendar = { viewModel.syncToCalendar() },
                                    onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                                    onTestAiConnection = { baseUrl, apiKey, modelName ->
                                        viewModel.testAiConnection(baseUrl, apiKey, modelName)
                                    },
                                    onImportLocalModel = { uri ->
                                        viewModel.importLocalGemmaModel(uri)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

