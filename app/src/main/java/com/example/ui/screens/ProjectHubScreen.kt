package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.repository.StudioProject
import com.example.ui.theme.CoralAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedSurface
import com.example.ui.theme.HairlineOutline
import com.example.ui.theme.MonoTimecodeStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.util.formatDurationShort
import com.example.ui.viewmodel.ProjectSortOrder
import com.example.ui.viewmodel.ProjectsUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectHubScreen(
    uiState: ProjectsUiState,
    onSearchChanged: (String) -> Unit,
    onSortChanged: (ProjectSortOrder) -> Unit,
    onCreateProject: (String, List<Uri>) -> Unit,
    onOpenProject: (String) -> Unit,
    onRenameProject: (String, String) -> Unit,
    onDuplicateProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onDismissError: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingRenameProject by remember { mutableStateOf<StudioProject?>(null) }
    var pendingDeleteProject by remember { mutableStateOf<StudioProject?>(null) }
    var pendingTitleDraft by remember { mutableStateOf<List<Uri>?>(null) }
    var newProjectTitle by remember { mutableStateOf("") }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 15)
    ) { uris ->
        if (uris.isNotEmpty()) {
            newProjectTitle = "Proyecto ${uiState.projects.size + 1}"
            pendingTitleDraft = uris
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            onDismissError()
        }
    }

    val launchPicker = {
        mediaPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.projects.isNotEmpty()) {
                FloatingActionButton(
                    onClick = launchPicker,
                    containerColor = ElectricCyan,
                    contentColor = Color(0xFF002026),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("new_project_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nuevo proyecto")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // Studio Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(ElectricCyan, CoralAmber))
                                )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "KINETIK STUDIO",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Mis Proyectos",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StudioSurface,
                    border = BorderStroke(1.dp, HairlineOutline)
                ) {
                    Text(
                        text = "v1.0.0",
                        style = MonoTimecodeStyle,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Search & Sort Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChanged,
                    placeholder = {
                        Text("Buscar proyectos...", color = TextMuted)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = StudioSurface,
                        unfocusedContainerColor = StudioSurface,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = HairlineOutline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_projects_input")
                )

                Spacer(Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ProjectSortOrder.entries) { order ->
                        val selected = uiState.sortOrder == order
                        FilterChip(
                            selected = selected,
                            onClick = { onSortChanged(order) },
                            label = { Text(order.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.16f),
                                selectedLabelColor = ElectricCyan,
                                containerColor = StudioSurface,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = HairlineOutline,
                                selectedBorderColor = ElectricCyan
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            AnimatedVisibility(visible = uiState.isImporting) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    color = ElevatedSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = ElectricCyan,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Importando clips y generando miniaturas y ondas de audio...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            if (uiState.projects.isEmpty() && !uiState.isImporting) {
                EmptyStudioHero(
                    hasQuery = uiState.searchQuery.isNotBlank(),
                    onCreateClick = launchPicker,
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(20.dp)
                )
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val columns = when {
                        maxWidth >= 900.dp -> 3
                        maxWidth >= 600.dp -> 2
                        else -> 1
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 8.dp,
                            bottom = 96.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.projects, key = { it.id }) { project ->
                            ProjectCard(
                                project = project,
                                onOpen = { onOpenProject(project.id) },
                                onRename = { pendingRenameProject = project },
                                onDuplicate = { onDuplicateProject(project.id) },
                                onDelete = { pendingDeleteProject = project }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create project title dialog
    pendingTitleDraft?.let { uris ->
        AlertDialog(
            onDismissRequest = { pendingTitleDraft = null },
            containerColor = StudioSurface,
            title = { Text("Nuevo proyecto", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        text = "${uris.size} clip(s) seleccionados para el estudio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newProjectTitle,
                        onValueChange = { newProjectTitle = it },
                        label = { Text("Nombre del proyecto") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = newProjectTitle.ifBlank { "Proyecto Studio" }
                        pendingTitleDraft = null
                        onCreateProject(title, uris)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color(0xFF002026)
                    ),
                    modifier = Modifier.testTag("confirm_create_project_button")
                ) {
                    Text("Crear y Editar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTitleDraft = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // Rename dialog
    pendingRenameProject?.let { target ->
        var editedName by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { pendingRenameProject = null },
            containerColor = StudioSurface,
            title = { Text("Renombrar proyecto", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameProject(target.id, editedName)
                        pendingRenameProject = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color(0xFF002026)
                    )
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRenameProject = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // Delete confirmation dialog
    pendingDeleteProject?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteProject = null },
            containerColor = StudioSurface,
            title = { Text("Eliminar proyecto", color = TextPrimary) },
            text = {
                Text(
                    "Se eliminará \"${target.title}\" junto con sus copias multimedia del estudio. Esta acción no se puede deshacer.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProject(target.id)
                        pendingDeleteProject = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralAmber)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteProject = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun EmptyStudioHero(
    hasQuery: Boolean,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = StudioSurface),
            border = BorderStroke(1.dp, HairlineOutline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                ElectricCyan.copy(alpha = 0.09f),
                                Color.Transparent,
                                CoralAmber.copy(alpha = 0.06f)
                            )
                        )
                    )
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ElevatedSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MovieCreation,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = if (hasQuery) "Sin resultados" else "Tu estudio está listo",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (hasQuery) {
                        "No encontramos proyectos que coincidan con tu búsqueda."
                    } else {
                        "Importa videos o fotos para editar con keyframes, curvas Bézier, máscaras, transiciones GPU y exportación H.264/H.265."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                if (!hasQuery) {
                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = onCreateClick,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = Color(0xFF002026)
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                        modifier = Modifier.testTag("empty_create_project_button")
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Crear Nuevo Proyecto", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: StudioProject,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateStr = remember(project.updatedAt) {
        SimpleDateFormat("dd MMM • HH:mm", Locale.getDefault()).format(Date(project.updatedAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .testTag("project_card_${project.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, HairlineOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElevatedSurface),
                contentAlignment = Alignment.Center
            ) {
                if (project.thumbnailPath != null && File(project.thumbnailPath).exists()) {
                    AsyncImage(
                        model = File(project.thumbnailPath),
                        contentDescription = project.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MovieCreation,
                        contentDescription = null,
                        tint = TextMuted
                    )
                }
                Surface(
                    color = ObsidianBg.copy(alpha = 0.84f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = formatDurationShort(project.timeline.totalDurationMs),
                        style = MonoTimecodeStyle,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioBadge(project.timeline.aspectRatio.label, ElectricCyan)
                    StudioBadge("${project.resolutionLabel} • ${project.timeline.fps}fps", TextSecondary)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${project.timeline.mainClips.size} clips • $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("project_menu_${project.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones del proyecto",
                        tint = TextSecondary
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = ElevatedSurface
                ) {
                    DropdownMenuItem(
                        text = { Text("Renombrar", color = TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.DriveFileRenameOutline, null, tint = ElectricCyan)
                        },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicar", color = TextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, null, tint = TextSecondary)
                        },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = CoralAmber) },
                        leadingIcon = {
                            Icon(Icons.Default.DeleteOutline, null, tint = CoralAmber)
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
