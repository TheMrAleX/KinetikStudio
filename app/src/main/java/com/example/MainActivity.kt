package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.local.KinetikDatabase
import com.example.data.media.MediaVault
import com.example.data.repository.ProjectRepository
import com.example.ui.screens.EditorStudioScreen
import com.example.ui.screens.ProjectHubScreen
import com.example.ui.theme.KinetikStudioTheme
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.ProjectsViewModel
import kotlinx.serialization.Serializable

@Serializable
object ProjectsRoute

@Serializable
data class EditorRoute(val projectId: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KinetikStudioTheme {
                KinetikApp()
            }
        }
    }
}

@Composable
private fun KinetikApp() {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) {
        val db = KinetikDatabase.getInstance(context)
        ProjectRepository(
            projectDao = db.projectDao(),
            mediaVault = MediaVault(context)
        )
    }

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ProjectsRoute
    ) {
        composable<ProjectsRoute> {
            val vm: ProjectsViewModel = viewModel(
                factory = ProjectsViewModel.Factory(repository)
            )
            val uiState by vm.uiState.collectAsStateWithLifecycle()

            ProjectHubScreen(
                uiState = uiState,
                onSearchChanged = vm::onSearchQueryChanged,
                onSortChanged = vm::onSortOrderChanged,
                onCreateProject = { title, uris ->
                    vm.createProject(title, uris) { createdId ->
                        navController.navigate(EditorRoute(createdId))
                    }
                },
                onOpenProject = { id ->
                    navController.navigate(EditorRoute(id))
                },
                onRenameProject = vm::renameProject,
                onDuplicateProject = vm::duplicateProject,
                onDeleteProject = vm::deleteProject,
                onDismissError = vm::clearError
            )
        }

        composable<EditorRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<EditorRoute>()
            val vm: EditorViewModel = viewModel(
                key = "editor_${route.projectId}",
                factory = EditorViewModel.Factory(context, route.projectId, repository)
            )
            EditorStudioScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
