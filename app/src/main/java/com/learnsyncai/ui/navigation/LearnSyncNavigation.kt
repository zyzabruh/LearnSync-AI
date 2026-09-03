package com.learnsyncai.ui.navigation

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.learnsyncai.ui.screens.*
import com.learnsyncai.ui.theme.*
import com.learnsyncai.ui.viewmodels.LibraryViewModel
import com.learnsyncai.ui.viewmodels.ProfileViewModel
import com.learnsyncai.ui.viewmodels.ReviewViewModel
import com.learnsyncai.ui.viewmodels.SearchViewModel
import com.learnsyncai.ui.viewmodels.SyncViewModel
import com.learnsyncai.ui.viewmodels.UiState

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Accueil", Icons.Default.Home)
    object Courses : Screen("courses", "Cours", Icons.AutoMirrored.Filled.MenuBook)
    object Review : Screen("review", "Réviser", Icons.Default.School)
    object Stats : Screen("stats", "Stats", Icons.Default.BarChart)
    object Profile : Screen("profile", "Profil", Icons.Default.Person)
    object Calendar : Screen("calendar", "Calendrier", Icons.Default.CalendarMonth)
}

@Composable
fun LearnSyncNavigation(
    requestedRoute: String? = null,
    requestId: Int = 0
) {
    val libraryViewModel: LibraryViewModel = viewModel()
    val reviewViewModel: ReviewViewModel = viewModel()
    val profileViewModel: ProfileViewModel = viewModel()
    val syncViewModel: SyncViewModel = viewModel()
    val searchViewModel: SearchViewModel = viewModel()

    val navController = rememberNavController()
    val courses by libraryViewModel.courses.collectAsState()
    val dueFlashcards by reviewViewModel.dueFlashcards.collectAsState()
    val reviewQueue by reviewViewModel.reviewQueue.collectAsState()
    val allFlashcards by libraryViewModel.allFlashcards.collectAsState()
    val reviewLogs by reviewViewModel.reviewLogs.collectAsState()
    val reviewSessions by reviewViewModel.reviewSessions.collectAsState()
    val preferences by profileViewModel.preferences.collectAsState()
    val aiProfiles by profileViewModel.aiProfiles.collectAsState()
    val activeAiProfile by profileViewModel.activeAiProfile.collectAsState()
    val hasValidAiConfig by libraryViewModel.hasValidAiConfig.collectAsState()
    val modelDownloadProgress by profileViewModel.modelDownloadProgress.collectAsState()
    val localModels by profileViewModel.localModels.collectAsState()
    val uiState by libraryViewModel.uiState.collectAsState()
    val profileUiState by profileViewModel.uiState.collectAsState()
    val syncUiState by syncViewModel.uiState.collectAsState()
    val generationProgress by libraryViewModel.generationProgress.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(requestId, requestedRoute) {
        if (requestedRoute != null && navController.currentDestination?.route != requestedRoute) {
            navController.navigate(requestedRoute) {
                launchSingleTop = true
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
            }
        }
    }

    UiStateSnackbarEffect(uiState, libraryViewModel::clearState, snackbarHostState)
    UiStateSnackbarEffect(profileUiState, profileViewModel::clearState, snackbarHostState)
    UiStateSnackbarEffect(syncUiState, syncViewModel::clearState, snackbarHostState)

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
                                    NavItemIcon(
                                        icon = screen.icon,
                                        contentDescription = screen.title,
                                        showBadge = isReview && dueFlashcards.isNotEmpty(),
                                        badgeCount = dueFlashcards.size,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                                onClick = { navigateToTopLevel(navController, currentRoute, screen.route) }
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
                                    NavItemIcon(
                                        icon = screen.icon,
                                        contentDescription = screen.title,
                                        showBadge = isReview && dueFlashcards.isNotEmpty(),
                                        badgeCount = dueFlashcards.size
                                    )
                                },
                                label = { Text(screen.title) },
                                selected = isSelected,
                                onClick = { navigateToTopLevel(navController, currentRoute, screen.route) }
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
                                    onNavigateToReview = { navController.navigate(Screen.Review.route) },
                                    onNavigateToCourses = { navController.navigate(Screen.Courses.route) },
                                    onNavigateToStats = { navController.navigate(Screen.Stats.route) },
                                    onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                                    onSelectCourse = { course -> navController.navigate("course_detail/${course.id}") },
                                    onSyncCalendar = { syncViewModel.syncToCalendar() }
                                )
                            }

                            composable(Screen.Courses.route) {
                                CoursesScreen(
                                    courses = courses,
                                    allFlashcards = allFlashcards,
                                    dueCards = dueFlashcards,
                                    hasValidAiConfig = hasValidAiConfig,
                                    onImportCourse = { uri, name -> libraryViewModel.importCourse(uri, name) },
                                    onImportFromUrl = { url -> libraryViewModel.importCourseFromUrl(url) },
                                    onGenerateMaterial = { course -> libraryViewModel.generateMaterial(course) },
                                    onSelectCourse = { course -> navController.navigate("course_detail/${course.id}") },
                                    onDeleteCourse = { courseId -> libraryViewModel.deleteCourse(courseId) },
                                    onUpdateCourseTag = { courseId, tag -> libraryViewModel.updateCourseTag(courseId, tag) },
                                    onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                                    onNavigateToSearch = { navController.navigate("search") },
                                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                                    onReviewCourse = { courseId -> navController.navigate("course_review/$courseId") }
                                )
                            }

                            composable("search") {
                                val allQuiz by searchViewModel.allQuizQuestions.collectAsState()
                                val allMaterials by searchViewModel.allMaterials.collectAsState()
                                val searchCourses by searchViewModel.courses.collectAsState()
                                val searchFlashcards by searchViewModel.allFlashcards.collectAsState()
                                SearchScreen(
                                    courses = searchCourses,
                                    flashcards = searchFlashcards,
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
                                val materials by libraryViewModel.getMaterialsForCourse(courseId).collectAsState(initial = emptyList())
                                val courseFlashcards by libraryViewModel.getFlashcardsForCourse(courseId).collectAsState(initial = emptyList())
                                val courseQuiz by libraryViewModel.getQuizQuestionsForCourse(courseId).collectAsState(initial = emptyList())

                                if (course != null) {
                                    val coursePreview by libraryViewModel.getCoursePreview(course.id)
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
                                        onRegenerate = { libraryViewModel.generateMaterial(course) },
                                        onGenerateMore = { libraryViewModel.generateMoreMaterial(course) },
                                        onCourseLanguageChange = { lang -> libraryViewModel.updateCourseLanguage(course, lang) },
                                        onDeleteCourse = {
                                            libraryViewModel.deleteCourse(course.id)
                                            navController.popBackStack()
                                        },
                                        onExportCsv = { uri -> libraryViewModel.exportCourseToCsv(uri, course.id) },
                                        onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                                        onAddFlashcard = { q, a, exp -> libraryViewModel.addCustomFlashcard(course.id, q, a, exp) },
                                        onDeleteFlashcard = { cardId -> libraryViewModel.deleteFlashcard(cardId) },
                                        onAddQuizQuestion = { q, opts, ans, exp -> libraryViewModel.addCustomQuizQuestion(course.id, q, opts, ans, exp) },
                                        onDeleteQuizQuestion = { qId -> libraryViewModel.deleteQuizQuestion(qId) },
                                        onSaveSummary = { summary -> libraryViewModel.saveCustomSummary(course.id, summary) },
                                        onAddKeyPoint = { point -> libraryViewModel.addCustomKeyPoint(course.id, point) },
                                        onRemoveKeyPoint = { point -> libraryViewModel.removeCustomKeyPoint(course.id, point) }
                                    )
                                }
                            }

                            composable(Screen.Review.route) {
                                val aheadCards = allFlashcards.filter { it.dueDate > System.currentTimeMillis() }

                                ReviewScreen(
                                    dueCards = dueFlashcards,
                                    aheadCount = aheadCards.size,
                                    reviewQueue = reviewQueue,
                                    onReviewCard = { card, rating, time -> reviewViewModel.rateCurrentCard(card, rating, time) },
                                    onSpeakQuestion = { text -> reviewViewModel.speakQuestion(text) },
                                    onSpeakAnswer = { text -> reviewViewModel.speakAnswer(text) },
                                    onStartSession = { limit -> reviewViewModel.startReviewSession(dueFlashcards, limit) },
                                    onStartAheadSession = { reviewViewModel.startReviewSession(aheadCards, null) },
                                    onEndSession = { reviewViewModel.endReviewSession() },
                                    onFinishReview = { navController.navigate(Screen.Home.route) }
                                )
                            }

                            composable(
                                route = "course_review/{courseId}",
                                arguments = listOf(navArgument("courseId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                                val courseDueFlashcards by reviewViewModel.getDueFlashcardsForCourse(courseId).collectAsState(initial = emptyList())
                                val courseAheadCards = allFlashcards.filter { it.courseId == courseId && it.dueDate > System.currentTimeMillis() }

                                ReviewScreen(
                                    dueCards = courseDueFlashcards,
                                    aheadCount = courseAheadCards.size,
                                    reviewQueue = reviewQueue,
                                    onReviewCard = { card, rating, time -> reviewViewModel.rateCurrentCard(card, rating, time) },
                                    onSpeakQuestion = { text -> reviewViewModel.speakQuestion(text) },
                                    onSpeakAnswer = { text -> reviewViewModel.speakAnswer(text) },
                                    onStartSession = { limit -> reviewViewModel.startReviewSession(courseDueFlashcards, limit) },
                                    onStartAheadSession = { reviewViewModel.startReviewSession(courseAheadCards, null) },
                                    onEndSession = { reviewViewModel.endReviewSession() },
                                    onFinishReview = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = "course_quiz/{courseId}",
                                arguments = listOf(navArgument("courseId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                                val course = courses.find { it.id == courseId }
                                val courseQuiz by libraryViewModel.getQuizQuestionsForCourse(courseId).collectAsState(initial = emptyList())

                                QuizScreen(
                                    courseTitle = course?.title ?: "Cours",
                                    quizQuestions = courseQuiz,
                                    onFinishQuiz = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.Calendar.route) {
                                CalendarScreen(
                                    allFlashcards = allFlashcards,
                                    reviewLogs = reviewLogs,
                                    reviewSessions = reviewSessions,
                                    courses = courses,
                                    onSyncCalendar = { syncViewModel.syncToCalendar() },
                                    onBackClick = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.Stats.route) {
                                StatsScreen(
                                    reviewLogs = reviewLogs,
                                    reviewSessions = reviewSessions,
                                    allFlashcards = allFlashcards,
                                    courses = courses
                                )
                            }

                            composable(Screen.Profile.route) {
                                ProfileScreen(
                                    preferences = preferences,
                                    aiProfiles = aiProfiles,
                                    activeAiProfile = activeAiProfile,
                                    onUpdatePreferences = { prefs -> profileViewModel.updatePreferences(prefs) },
                                    onAddAiProfile = { name, provider, baseUrl, apiKey, modelName ->
                                        profileViewModel.addAiProfile(name, provider, baseUrl, apiKey, modelName)
                                    },
                                    onUpdateAiProfile = { profile -> profileViewModel.updateAiProfile(profile) },
                                    onDeleteAiProfile = { profileId -> profileViewModel.deleteAiProfile(profileId) },
                                    onSetActiveAiProfile = { profileId -> profileViewModel.setActiveAiProfile(profileId) },
                                    onSyncCloud = { syncViewModel.syncWithCloud() },
                                    onSyncCalendar = { syncViewModel.syncToCalendar() },
                                    onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                                    onTestAiConnection = { baseUrl, apiKey, modelName ->
                                        profileViewModel.testAiConnection(baseUrl, apiKey, modelName)
                                    },
                                    onImportLocalModel = { uri ->
                                        profileViewModel.importLocalGemmaModel(uri)
                                    },
                                    onDownloadGemmaModel = { url, token, onResult ->
                                        profileViewModel.downloadGemmaModel(url, token, onResult)
                                    },
                                    modelDownloadProgress = modelDownloadProgress,
                                    localModels = localModels,
                                    onRefreshLocalModels = { profileViewModel.refreshLocalModels() },
                                    onDeleteLocalModel = { path -> profileViewModel.deleteLocalModel(path) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Navigation vers un onglet de premier niveau (save/restore state, single top). */
private fun navigateToTopLevel(
    navController: androidx.navigation.NavController,
    currentRoute: String?,
    route: String
) {
    if (currentRoute != route) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}

/**
 * Icône d'item de navigation avec badge de cartes dues, partagée par la
 * bottom bar et le rail. [tint] null = teinte par défaut du thème (rail).
 */
@Composable
private fun NavItemIcon(
    icon: ImageVector,
    contentDescription: String,
    showBadge: Boolean,
    badgeCount: Int,
    tint: androidx.compose.ui.graphics.Color? = null
) {
    BadgedBox(
        badge = {
            if (showBadge) {
                Badge(
                    containerColor = AmberFlame,
                    contentColor = Slate900
                ) {
                    Text("$badgeCount")
                }
            }
        }
    ) {
        if (tint != null) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
        } else {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}

/** Affiche le message d'un état UI transitoire en snackbar puis le consomme. */
@Composable
private fun UiStateSnackbarEffect(
    state: UiState,
    onConsume: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val message = when (state) {
        is UiState.Success -> state.message
        is UiState.Error -> state.message
        else -> null
    }
    LaunchedEffect(state) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onConsume()
        }
    }
}
