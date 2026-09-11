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
import com.learnsyncai.domain.usecase.SpacedRepetition
import com.learnsyncai.data.parser.OutlineEntry
import com.learnsyncai.ui.viewmodels.LibraryViewModel
import com.learnsyncai.ui.viewmodels.ProfileViewModel
import com.learnsyncai.ui.viewmodels.ReviewViewModel
import com.learnsyncai.ui.viewmodels.SearchViewModel
import com.learnsyncai.ui.viewmodels.SyncViewModel
import com.learnsyncai.ui.viewmodels.TutorViewModel
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
    val tutorViewModel: TutorViewModel = viewModel()
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
    val syncStatus by syncViewModel.syncStatus.collectAsState()
    val generationProgress by libraryViewModel.generationProgress.collectAsState()
    val ocrRequest by libraryViewModel.ocrRequest.collectAsState()
    val ocrProgress by libraryViewModel.ocrProgress.collectAsState()

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
                                    onImportApkg = { uri, name -> libraryViewModel.importApkg(uri, name) },
                                    onSeedDemo = { libraryViewModel.seedDemoCourse() },
                                    onNavigateToGraph = { navController.navigate("knowledge_graph") },
                                    onImportFromUrl = { url -> libraryViewModel.importCourseFromUrl(url) },
                                    onImportFromTranscript = { title, url, transcript -> libraryViewModel.importFromTranscript(title, url, transcript) },
                                    onGenerateMaterial = { course -> libraryViewModel.generateMaterial(course) },
                                    onSelectCourse = { course -> navController.navigate("course_detail/${course.id}") },
                                    onDeleteCourse = { courseId -> libraryViewModel.deleteCourse(courseId) },
                                    onUpdateCourseTags = { courseId, tags -> libraryViewModel.updateCourseTags(courseId, tags) },
                                    onUpdateCourseFolder = { courseId, folder -> libraryViewModel.updateCourseFolder(courseId, folder) },
                                    onUpdateExamDate = { courseId, date -> libraryViewModel.updateExamDate(courseId, date) },
                                    onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                                    onNavigateToSearch = { navController.navigate("search") },
                                    onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                                    onReviewCourse = { courseId -> navController.navigate("course_review/$courseId") },
                                    ocrRequest = ocrRequest,
                                    ocrProgress = ocrProgress,
                                    onRunPdfOcr = { libraryViewModel.runPdfOcr(it) },
                                    onCancelPdfOcr = { libraryViewModel.cancelPdfOcr() }
                                )
                            }

                            composable("search") {
                                val allQuiz by searchViewModel.allQuizQuestions.collectAsState()
                                val allMaterials by searchViewModel.allMaterials.collectAsState()
                                val searchCourses by searchViewModel.courses.collectAsState()
                                val searchFlashcards by searchViewModel.allFlashcards.collectAsState()
                                val searchNotes by searchViewModel.allNotes.collectAsState()
                                SearchScreen(
                                    courses = searchCourses,
                                    flashcards = searchFlashcards,
                                    quizQuestions = allQuiz,
                                    materials = allMaterials,
                                    notes = searchNotes,
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
                                    val courseNote by libraryViewModel.getNoteForCourse(course.id)
                                        .collectAsState(initial = null)
                                    val courseMedia by libraryViewModel.getMediaForCourse(course.id)
                                        .collectAsState(initial = emptyList())
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
                                        onCancelGeneration = { libraryViewModel.cancelGeneration(course.id) },
                                        onCourseLanguageChange = { lang -> libraryViewModel.updateCourseLanguage(course, lang) },
                                        onDeleteCourse = {
                                            libraryViewModel.deleteCourse(course.id)
                                            navController.popBackStack()
                                        },
                                        onExportCsv = { uri -> libraryViewModel.exportCourseToCsv(uri, course.id) },
                                        onOpenDocument = { libraryViewModel.openCourseDocument(course.id) },
                                        onNavigateToTutor = { navController.navigate("course_tutor/${course.id}") },
                                        onNavigateToLearn = { navController.navigate("course_learn/${course.id}") },
                                        onNavigateToExam = { navController.navigate("course_exam/${course.id}") },
                                        onNavigateToPdf = { navController.navigate("course_pdf/${course.id}") },
                                        onNavigateToMindMap = { navController.navigate("course_mindmap/${course.id}") },
                                        onNavigateToOral = { navController.navigate("course_oral/${course.id}") },
                                        onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                                        onAddFlashcard = { q, a, exp, dir, typeAns -> libraryViewModel.addCustomFlashcard(course.id, q, a, exp, dir, typeAns) },
                                        onAddImageCard = { uri, answer, mx, my, mw, mh -> libraryViewModel.createImageCard(course.id, uri, answer, mx, my, mw, mh) },
                                        onQuickAddFlashcard = { q, a, excerpt -> libraryViewModel.quickAddFlashcard(course.id, q, a, excerpt) },
                                        onGenerateFromExcerpt = { excerpt -> libraryViewModel.generateFlashcardsFromExcerpt(course, excerpt) },
                                        courseNote = courseNote,
                                        onSaveNote = { content -> libraryViewModel.saveNote(course.id, content) },
                                        onConvertNotes = { content -> libraryViewModel.convertNotesToCards(course.id, content) },
                                        onConvertSingleLine = { line -> libraryViewModel.convertSingleNoteLine(course.id, line) },
                                        courseMedia = courseMedia,
                                        onAddAudio = { path -> libraryViewModel.addAudioMedia(course.id, path) },
                                        onUpdateTranscript = { media, text -> libraryViewModel.updateMediaTranscript(media, text) },
                                        onDeleteMedia = { media -> libraryViewModel.deleteMedia(media) },
                                        onCardsFromTranscript = { text -> libraryViewModel.cardsFromTranscript(course, text) },
                                        onOpenConcept = { name ->
                                            val encoded = try {
                                                java.net.URLEncoder.encode(name, "UTF-8")
                                            } catch (_: Exception) { name }
                                            navController.navigate("concept/${course.id}/$encoded")
                                        },
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
                                val canUndo by reviewViewModel.canUndo.collectAsState()
                                val gapCount = remember(dueFlashcards, reviewLogs) {
                                    reviewViewModel.gapCount(dueFlashcards, reviewLogs)
                                }
                                val explanation by reviewViewModel.explanation.collectAsState()
                                val explaining by reviewViewModel.explaining.collectAsState()
                                val examByCourse = remember(courses) { courses.associate { it.id to it.examDate } }
                                val examCards = remember(allFlashcards, examByCourse) {
                                    val now = System.currentTimeMillis()
                                    allFlashcards.filter { c ->
                                        val ex = examByCourse[c.courseId] ?: 0L
                                        ex > now && !c.suspended && c.dueDate <= ex
                                    }.sortedBy { it.dueDate }
                                }

                                ReviewScreen(
                                    dueCards = dueFlashcards,
                                    aheadCount = aheadCards.size,
                                    reviewQueue = reviewQueue,
                                    onReviewCard = { item, rating, time -> reviewViewModel.rateCurrentCard(item, rating, time) },
                                    onSpeakQuestion = { text -> reviewViewModel.speakQuestion(text) },
                                    onSpeakAnswer = { text -> reviewViewModel.speakAnswer(text) },
                                    onStartSession = { limit -> reviewViewModel.startReviewSession(dueFlashcards, limit) },
                                    onStartAheadSession = { reviewViewModel.startReviewSession(aheadCards, null) },
                                    onStartGapSession = { reviewViewModel.startGapSession(dueFlashcards, reviewLogs) },
                                    gapCount = gapCount,
                                    examCount = examCards.size,
                                    onStartExam = { reviewViewModel.startExamSession(examCards) },
                                    explanation = explanation,
                                    explaining = explaining,
                                    onExplainCard = { card, q, a -> reviewViewModel.explainCard(card, q, a) },
                                    onDismissExplanation = { reviewViewModel.clearExplanation() },
                                    onEndSession = { reviewViewModel.endReviewSession() },
                                    onFinishReview = { navController.navigate(Screen.Home.route) },
                                    canUndo = canUndo,
                                    onUndo = { reviewViewModel.undoLastRating() },
                                    onUpdateCard = { card, q, a, dir, typeAns ->
                                        libraryViewModel.updateFlashcardContent(card, q, a, dir, typeAns)
                                        reviewViewModel.refreshQueueCard(card.id, q, a)
                                    },
                                    onPostponeCard = { card ->
                                        libraryViewModel.postponeFlashcard(card)
                                        reviewViewModel.removeCardFromQueue(card.id)
                                    },
                                    onSuspendCard = { card ->
                                        libraryViewModel.setFlashcardSuspended(card, true)
                                        reviewViewModel.removeCardFromQueue(card.id)
                                    },
                                    onOpenPdfPage = { card ->
                                        if (card.sourcePage >= 0) {
                                            navController.navigate("course_pdf/${card.courseId}?page=${card.sourcePage}")
                                        }
                                    }
                                )
                            }

                            composable(
                                route = "course_review/{courseId}",
                                arguments = listOf(navArgument("courseId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                                val reviewCourse = courses.find { it.id == courseId }
                                val courseDueFlashcards by reviewViewModel.getDueFlashcardsForCourse(courseId).collectAsState(initial = emptyList())
                                val courseAheadCards = allFlashcards.filter { it.courseId == courseId && it.dueDate > System.currentTimeMillis() }
                                val canUndoCourse by reviewViewModel.canUndo.collectAsState()
                                val courseGapCount = remember(courseDueFlashcards, reviewLogs) {
                                    reviewViewModel.gapCount(courseDueFlashcards, reviewLogs)
                                }
                                val courseExplanation by reviewViewModel.explanation.collectAsState()
                                val courseExplaining by reviewViewModel.explaining.collectAsState()
                                val courseExamCards = remember(courseId, allFlashcards, reviewCourse?.examDate) {
                                    reviewViewModel.examEligible(
                                        allFlashcards.filter { it.courseId == courseId },
                                        reviewCourse?.examDate ?: 0L
                                    )
                                }

                                ReviewScreen(
                                    dueCards = courseDueFlashcards,
                                    aheadCount = courseAheadCards.size,
                                    reviewQueue = reviewQueue,
                                    onReviewCard = { item, rating, time -> reviewViewModel.rateCurrentCard(item, rating, time) },
                                    onSpeakQuestion = { text -> reviewViewModel.speakQuestion(text) },
                                    onSpeakAnswer = { text -> reviewViewModel.speakAnswer(text) },
                                    onStartSession = { limit -> reviewViewModel.startReviewSession(courseDueFlashcards, limit) },
                                    onStartAheadSession = { reviewViewModel.startReviewSession(courseAheadCards, null) },
                                    onStartGapSession = { reviewViewModel.startGapSession(courseDueFlashcards, reviewLogs) },
                                    gapCount = courseGapCount,
                                    examCount = courseExamCards.size,
                                    onStartExam = { reviewViewModel.startExamSession(courseExamCards) },
                                    explanation = courseExplanation,
                                    explaining = courseExplaining,
                                    onExplainCard = { card, q, a -> reviewViewModel.explainCard(card, q, a) },
                                    onDismissExplanation = { reviewViewModel.clearExplanation() },
                                    onEndSession = { reviewViewModel.endReviewSession() },
                                    onFinishReview = { navController.popBackStack() },
                                    canUndo = canUndoCourse,
                                    onUndo = { reviewViewModel.undoLastRating() },
                                    onUpdateCard = { card, q, a, dir, typeAns ->
                                        libraryViewModel.updateFlashcardContent(card, q, a, dir, typeAns)
                                        reviewViewModel.refreshQueueCard(card.id, q, a)
                                    },
                                    onPostponeCard = { card ->
                                        libraryViewModel.postponeFlashcard(card)
                                        reviewViewModel.removeCardFromQueue(card.id)
                                    },
                                    onSuspendCard = { card ->
                                        libraryViewModel.setFlashcardSuspended(card, true)
                                        reviewViewModel.removeCardFromQueue(card.id)
                                    },
                                    onOpenPdfPage = { card ->
                                        if (card.sourcePage >= 0) {
                                            navController.navigate("course_pdf/${card.courseId}?page=${card.sourcePage}")
                                        }
                                    }
                                )
                            }

                            composable(
                                route = "course_tutor/{courseId}",
                                arguments = listOf(navArgument("courseId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                                val course = courses.find { it.id == courseId }
                                val tutorMessages by tutorViewModel.messages.collectAsState()
                                val tutorSending by tutorViewModel.sending.collectAsState()
                                val tutorError by tutorViewModel.error.collectAsState()
                                CourseTutorScreen(
                                    courseTitle = course?.title ?: "Cours",
                                    messages = tutorMessages,
                                    sending = tutorSending,
                                    error = tutorError,
                                    onSend = { q -> tutorViewModel.send(courseId, q) },
                                    onCreateCard = { q, a -> tutorViewModel.createCardFromAnswer(courseId, q, a) },
                                    onClearError = { tutorViewModel.clearError() },
                                    onBackClick = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = "course_learn/{courseId}",
                                arguments = listOf(navArgument("courseId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                                val course = courses.find { it.id == courseId }
                                val learnMaterials by libraryViewModel.getMaterialsForCourse(courseId).collectAsState(initial = emptyList())
                                val learnDues by reviewViewModel.getDueFlashcardsForCourse(courseId).collectAsState(initial = emptyList())
                                val learnQuiz by libraryViewModel.getQuizQuestionsForCourse(courseId).collectAsState(initial = emptyList())
                                val learnCards = remember(allFlashcards) { allFlashcards.filter { it.courseId == courseId } }
                                val learnLeeches = remember(learnCards) {
                                    learnCards.count { !it.suspended && it.lapses >= SpacedRepetition.LEECH_LAPSE_THRESHOLD }
                                }
                                CourseLearnScreen(
                                    courseTitle = course?.title ?: "Cours",
                                    hasSummary = learnMaterials.firstOrNull()?.summary?.isNotBlank() == true,
                                    flashcardsCount = learnCards.size,
                                    dueCount = learnDues.size,
                                    quizCount = learnQuiz.size,
                                    leechCount = learnLeeches,
                                    onBackClick = { navController.popBackStack() },
                                    onSeeSummary = { navController.popBackStack() },
                                    onGenerate = { if (course != null) libraryViewModel.generateMaterial(course) },
                                    onGoReview = { navController.navigate("course_review/$courseId") },
                                    onGoQuiz = { navController.navigate("course_quiz/$courseId") },
                                    onStartGap = {
                                        reviewViewModel.startGapSession(learnDues, reviewLogs)
                                        navController.navigate("course_review/$courseId")
                                    },
                                    onGoTutor = { navController.navigate("course_tutor/$courseId") }
                                )
                            }

                            composable(
                                route = "concept/{courseId}/{name}",
                                arguments = listOf(
                                    navArgument("courseId") { type = NavType.StringType },
                                    navArgument("name") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                                val rawName = backStackEntry.arguments?.getString("name") ?: ""
                                val conceptName = try {
                                    java.net.URLDecoder.decode(rawName, "UTF-8")
                                } catch (_: Exception) { rawName }
                                val course = courses.find { it.id == courseId }
                                val conceptNote by libraryViewModel.getNoteForCourse(courseId).collectAsState(initial = null)
                                val conceptMaterials by libraryViewModel.getMaterialsForCourse(courseId).collectAsState(initial = emptyList())
                                val conceptCards = remember(allFlashcards, conceptName) {
                                    allFlashcards.filter {
                                        it.courseId == courseId &&
                                            com.learnsyncai.domain.usecase.Concepts.cardMentions(it, conceptName)
                                    }
                                }
                                val conceptSnippets = remember(conceptNote, conceptMaterials, conceptName) {
                                    val corpus = ((conceptNote?.content ?: "") + "\n" +
                                        conceptMaterials.firstOrNull()?.summary.orEmpty())
                                    com.learnsyncai.domain.usecase.Concepts.snippets(corpus, conceptName)
                                }
                                val conceptMastery = remember(conceptCards) {
                                    if (conceptCards.isEmpty()) 0 else {
                                        val now = System.currentTimeMillis()
                                        val total = conceptCards.sumOf { card ->
                                            val elapsed = card.lastReviewedAt?.let {
                                                ((now - it) / (1000f * 3600 * 24)).coerceAtLeast(0f)
                                            } ?: 0f
                                            com.learnsyncai.domain.usecase.SpacedRepetition
                                                .calculateRetrievability(elapsed, card.easeFactor).toDouble()
                                        }
                                        ((total / conceptCards.size) * 100).toInt()
                                    }
                                }
                                ConceptScreen(
                                    conceptName = conceptName.ifBlank { "Concept" },
                                    courseTitle = course?.title ?: "Cours",
                                    snippets = conceptSnippets,
                                    cards = conceptCards,
                                    masteryPercent = conceptMastery,
                                    onBackClick = { navController.popBackStack() },
                                    onReviewCards = {
                                        reviewViewModel.startReviewSession(conceptCards, null)
                                        navController.navigate(Screen.Review.route)
                                    }
                                )
                            }

                            composable(
                                route = "course_oral/{courseId}",
                                arguments = listOf(navArgument("courseId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                                val course = courses.find { it.id == courseId }
                                val oralDues by reviewViewModel.getDueFlashcardsForCourse(courseId).collectAsState(initial = emptyList())
                                val oralItems = remember(oralDues) {
                                    com.learnsyncai.domain.usecase.ReviewQueue.expand(oralDues)
                                }
                                CourseOralScreen(
                                    items = oralItems,
                                    onSpeak = { text -> reviewViewModel.speakQuestion(text) },
                                    onRate = { item, rating -> reviewViewModel.rateCurrentCard(item, rating, 0L) },
                                    onQuit = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = "course_mindmap/{courseId}",
                                arguments = listOf(navArgument("courseId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                                val course = courses.find { it.id == courseId }
                                val mindNote by libraryViewModel.getNoteForCourse(courseId).collectAsState(initial = null)
                                val mindMaterials by libraryViewModel.getMaterialsForCourse(courseId).collectAsState(initial = emptyList())
                                val mindConcepts = remember(allFlashcards, mindNote, mindMaterials) {
                                    val corpus = ((mindNote?.content ?: "") + "\n" +
                                        mindMaterials.firstOrNull()?.summary.orEmpty())
                                    val names = com.learnsyncai.domain.usecase.Concepts.extract(corpus)
                                    val cardsByCourse = allFlashcards.filter { it.courseId == courseId }
                                    names.map { name ->
                                        name to cardsByCourse.count {
                                            com.learnsyncai.domain.usecase.Concepts.cardMentions(it, name)
                                        }
                                    }.sortedByDescending { it.second }
                                }
                                CourseMindMapScreen(
                                    courseTitle = course?.title ?: "Cours",
                                    concepts = mindConcepts,
                                    onOpenConcept = { name ->
                                        val encoded = try {
                                            java.net.URLEncoder.encode(name, "UTF-8")
                                        } catch (_: Exception) { name }
                                        navController.navigate("concept/$courseId/$encoded")
                                    },
                                    onBackClick = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = "course_pdf/{courseId}?page={page}",
                                arguments = listOf(
                                    navArgument("courseId") { type = NavType.StringType },
                                    navArgument("page") { type = NavType.IntType; defaultValue = 0 }
                                )
                            ) { backStackEntry ->
                                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                                val startPage = backStackEntry.arguments?.getInt("page") ?: 0
                                val course = courses.find { it.id == courseId }
                                val pdfAnnotations by libraryViewModel.getAnnotationsForCourse(courseId).collectAsState(initial = emptyList())
                                val pdfOutline by libraryViewModel.getOutlineForCourse(courseId).collectAsState(initial = emptyList())
                                val inkVersion by libraryViewModel.inkVersion.collectAsState()
                                val loadPageText: suspend (Int) -> String = { page ->
                                    try { libraryViewModel.getPageText(courseId, page) } catch (_: Exception) { "" }
                                }
                                PdfReaderScreen(
                                    courseTitle = course?.title ?: "Cours",
                                    pdfFile = remember(courseId) { libraryViewModel.getLocalDocument(courseId) },
                                    initialPage = startPage,
                                    onLoadPageText = loadPageText,
                                    outline = pdfOutline,
                                    annotations = pdfAnnotations,
                                    onAddAnnotation = { page, text, kind -> libraryViewModel.addAnnotation(courseId, page, text, kind) },
                                    onQuickAddCard = { page, q, a ->
                                        libraryViewModel.quickAddFlashcard(courseId, q, a, "PDF p. ${page + 1}")
                                    },
                                    onLoadHighlightRects = { page, text ->
                                        try { libraryViewModel.getHighlightRects(courseId, page, text) } catch (_: Exception) { emptyList() }
                                    },
                                    inkVersion = inkVersion,
                                    onLoadInkStrokes = { try { libraryViewModel.getInkStrokes(courseId) } catch (_: Exception) { emptyList() } },
                                    onSaveInkStroke = { page, stroke -> libraryViewModel.saveInkStroke(courseId, stroke) },
                                    onClearInkPage = { page -> libraryViewModel.clearInkPage(courseId, page) },
                                    onDeleteAnnotation = { id -> libraryViewModel.deleteAnnotation(id) },
                                    onCardsFromAnnotation = { annotation ->
                                        if (course != null) libraryViewModel.cardsFromAnnotation(course, annotation)
                                    },
                                    onBackClick = { navController.popBackStack() }
                                )
                            }

                            composable("knowledge_graph") {
                                val sharedConcepts by libraryViewModel.getSharedConcepts().collectAsState(initial = emptyList())
                                KnowledgeGraphScreen(
                                    courses = courses,
                                    sharedConcepts = sharedConcepts,
                                    onOpenConcept = { courseId, name ->
                                        val encoded = try {
                                            java.net.URLEncoder.encode(name, "UTF-8")
                                        } catch (_: Exception) { name }
                                        navController.navigate("concept/$courseId/$encoded")
                                    },
                                    onBackClick = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = "course_exam/{courseId}",
                                arguments = listOf(navArgument("courseId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
                                val course = courses.find { it.id == courseId }
                                val examQuiz by libraryViewModel.getQuizQuestionsForCourse(courseId).collectAsState(initial = emptyList())
                                CourseExamScreen(
                                    courseTitle = course?.title ?: "Cours",
                                    quizQuestions = examQuiz,
                                    onFinishExam = { _, _ -> libraryViewModel.addXp(50) },
                                    onQuit = { navController.popBackStack() }
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
                                    courses = courses,
                                    onSuspendCard = { card -> libraryViewModel.setFlashcardSuspended(card, true) },
                                    onUnsuspendCard = { card -> libraryViewModel.setFlashcardSuspended(card, false) },
                                    dailyGoal = preferences.dailyGoal,
                                    xp = preferences.xp
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
                                    syncStatus = syncStatus,
                                    onUpdatePeriodicSync = { enabled -> profileViewModel.updatePeriodicSync(enabled) },
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
