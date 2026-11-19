package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ProjectRepository
import com.example.data.repository.StudioProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ProjectSortOrder(val label: String) {
    RECENT("Recientes"),
    NAME("Nombre"),
    DURATION("Duración")
}

data class ProjectsUiState(
    val projects: List<StudioProject> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: ProjectSortOrder = ProjectSortOrder.RECENT,
    val isImporting: Boolean = false,
    val errorMessage: String? = null
)

class ProjectsViewModel(
    private val repository: ProjectRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _sortOrder = MutableStateFlow(ProjectSortOrder.RECENT)
    private val _isImporting = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProjectsUiState> = combine(
        repository.observeProjects(),
        _searchQuery,
        _sortOrder,
        _isImporting,
        _errorMessage
    ) { allProjects, query, sort, importing, error ->
        val filtered = if (query.isBlank()) {
            allProjects
        } else {
            allProjects.filter { it.title.contains(query.trim(), ignoreCase = true) }
        }
        val sorted = when (sort) {
            ProjectSortOrder.RECENT -> filtered.sortedByDescending { it.updatedAt }
            ProjectSortOrder.NAME -> filtered.sortedBy { it.title.lowercase() }
            ProjectSortOrder.DURATION -> filtered.sortedByDescending { it.timeline.totalDurationMs }
        }
        ProjectsUiState(
            projects = sorted,
            searchQuery = query,
            sortOrder = sort,
            isImporting = importing,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ProjectsUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onSortOrderChanged(order: ProjectSortOrder) {
        _sortOrder.value = order
    }

    fun createProject(title: String, mediaUris: List<Uri>, onCreated: (String) -> Unit) {
        if (mediaUris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            _errorMessage.value = null
            runCatching {
                repository.createProjectFromMedia(title, mediaUris)
            }.onSuccess { project ->
                _isImporting.value = false
                onCreated(project.id)
            }.onFailure { err ->
                _isImporting.value = false
                _errorMessage.value = err.localizedMessage ?: "Error al importar los clips."
            }
        }
    }

    fun renameProject(projectId: String, newTitle: String) {
        viewModelScope.launch {
            runCatching { repository.renameProject(projectId, newTitle) }
                .onFailure { _errorMessage.value = it.localizedMessage }
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            runCatching { repository.duplicateProject(projectId) }
                .onFailure { _errorMessage.value = it.localizedMessage }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteProject(projectId) }
                .onFailure { _errorMessage.value = it.localizedMessage }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    class Factory(private val repository: ProjectRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProjectsViewModel(repository) as T
        }
    }
}
