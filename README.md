# LearnSync AI

LearnSync AI est une application mobile Android moderne de révision intelligente, propulsée par l'intelligence artificielle (Firebase AI Logic / Gemini) et un algorithme avancé de répétition espacée.

---

## Fonctionnalités Principales

1. **Import de cours & Extraction** : Support robuste des fichiers PDF (via PdfBox-Android) et TXT avec parsing local sécurisé et réactif.
2. **Génération Pédagogique IA (Firebase AI Logic)** : Analyse sémantique générant des résumés structurés, points clés, astuces mnémotechniques, flashcards atomiques et QCM stricts à 4 choix distincts avec validation.
3. **Moteur de Répétition Espacée (FSRS / Leitner)** : Algorithme intelligent avec notation sur 4 niveaux (*À revoir*, *Difficile*, *Bien*, *Facile*), calcul dynamique d'intervalles et progression de boîtes.
4. **Persistance Locale & Mode Hors-ligne (Room Database)** : Architecture offline-first complète avec transactions atomiques, clés étrangères et suppression en cascade.
5. **Synchronisation Cloud Bidirectionnelle (Firebase Firestore)** : Synchronisation UP + DOWN par lots (batches de 400 documents) avec résolution des conflits par horodatage.
6. **Authentification Sécurisée (Google Sign-In)** : Connexion via Jetpack `CredentialManager` et Firebase Auth.
7. **Rappels & Notifications (WorkManager)** : Planification quotidienne selon l'heure choisie par l'utilisateur, avec gestion des permissions Android 13+ (`POST_NOTIFICATIONS`).
8. **Intégration Calendrier Système** : Synchronisation des sessions de révision avec le calendrier Android via `androidEventId` et gestion des permissions d'exécution.

---

## Architecture

Le projet adopte une **Clean Architecture** stricte structurée en trois couches :
- **Presentation** : Jetpack Compose (Material 3), Navigation Compose, ViewModels (StateFlow / UDF).
- **Domain** : Entités pures (`Course`, `StudyMaterial`, `Flashcard`, `QuizQuestion`, `ReviewLog`, `UserPreferences`), interfaces de repositories et cas d'utilisation (`SpacedRepetition`, `QuizValidator`).
- **Data** : Base de données Room (`LearnSyncDatabase`), DAOs avec requêtes réactives (Flow), repositories d'accès aux données, parseurs de documents (`DocumentParser`), intégrations système (`CalendarHelper`, `ReviewNotificationWorker`) et client d'IA Firebase (`AiRepositoryImpl`).

---

## Configuration & Compilation

### Prérequis
- Android SDK 34+
- Java / JDK 17
- Gradle 8.x / 9.x

### Configuration Firebase
1. Connectez votre projet à la console Firebase.
2. Ajoutez le fichier de configuration `google-services.json` dans le répertoire `/app`.
3. Activez Firebase Authentication (Google Sign-in), Cloud Firestore et Firebase AI / Vertex AI in Firebase.

### Commandes de Build
- Exécuter les tests unitaires et d'intégration Robolectric :
  ```bash
  ./gradlew testDebugUnitTest
  ```
- Exécuter l'analyse de qualité de code Lint :
  ```bash
  ./gradlew lintDebug
  ```
- Compiler l'APK de débogage :
  ```bash
  ./gradlew assembleDebug
  ```

---

## CI / CD

Le pipeline GitHub Actions (`.github/workflows/android.yml`) valide automatiquement la conformité du projet :
1. Validation du Gradle Wrapper
2. Exécution des tests unitaires (`testDebugUnitTest`)
3. Vérification Lint (`lintDebug`)
4. Compilation de l'APK Debug (`assembleDebug`)
