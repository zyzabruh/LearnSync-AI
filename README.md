# LearnSync AI

LearnSync AI est une application mobile Android moderne de révision intelligente propulsée par l'intelligence artificielle (Firebase AI Logic / Gemini) et un algorithme de répétition espacée.

---

## Fonctionnalités

1. **Import de cours** : Support des fichiers PDF et TXT avec extraction de texte et gestion locale.
2. **Génération IA** : Analyse automatique des cours pour produire des résumés, points clés, astuces mnémotechniques, flashcards atomiques et questions QCM.
3. **Répétition Espacée** : Algorithme intelligent basé sur 4 niveaux (À revoir, Difficile, Bien, Facile) pour optimiser la mémorisation à long terme.
4. **Suivi de Streak & Statistiques** : Calcul précis du streak sur jours consécutifs et visualisations statistiques.
5. **Mode Hors-Ligne & Synchronisation Cloud** : Persistance locale via Room et synchronisation cloud avec Firebase Firestore & Authentication.
6. **Notifications & Calendrier** : Rappels quotidiens via WorkManager et synchronisation du calendrier.

---

## Architecture

L'application respecte les principes de Clean Architecture et MVVM :
- **Presentation** : Composables Jetpack Compose, ViewModels, Navigation Compose.
- **Domain** : Modèles de données, interfaces de repositories, use cases (répétition espacée).
- **Data** : DAOs Room, entités, repositories implémentés, parseur de documents, IA Repository (Firebase AI).

---

## Configuration & Installation

1. Cloner le projet.
2. Configurer votre projet Firebase et télécharger le fichier `google-services.json` dans `app/`.
3. Configurer la clé API Gemini dans le fichier `.env` ou via les Secrets AI Studio.
4. Compiler le projet :
   ```bash
   ./gradlew assembleDebug
   ```
5. Lancer les tests :
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## GitHub Actions & CI/CD

Le workflow `.github/workflows/android.yml` automatise à chaque push :
1. Checkout du code
2. Installation de Java 17
3. Exécution des tests unitaires & Robolectric
4. Analyse Lint
5. Construction de l'APK Debug et publication de l'artefact.
