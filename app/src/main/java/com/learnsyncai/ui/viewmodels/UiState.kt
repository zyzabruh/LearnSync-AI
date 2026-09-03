package com.learnsyncai.ui.viewmodels

/**
 * État UI transitoire partagé par les ViewModels qui émettent des messages
 * (snackbar) : chargement, succès, erreur.
 */
sealed interface UiState {
    object Idle : UiState
    data class Loading(val message: String = "Chargement...") : UiState
    data class Success(val message: String) : UiState
    data class Error(val message: String) : UiState
}
