package com.muncho.kaptal.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muncho.kaptal.viewmodel.SettingsViewModel
import com.muncho.kaptal.model.CategoryFamily
import org.jetbrains.compose.resources.painterResource
import kaptal.composeapp.generated.resources.Res
import kaptal.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel
) {
    val userCategories = viewModel.userCategories
    var showAddFamilyDialog by remember { mutableStateOf(false) }
    var familyToRename by remember { mutableStateOf<CategoryFamily?>(null) }
    var familyToAddSub by remember { mutableStateOf<CategoryFamily?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8ECEF))
    ) {
        Image(
            painter = painterResource(Res.drawable.fond_kaptal_propre),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.3f
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_k_logo),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(0.9f),
                contentScale = ContentScale.Fit,
                alpha = 0.15f
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Gestion des catégories",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Retour"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(userCategories) { family ->
                    CategoryFamilyItem(
                        family = family,
                        onRenameClick = { familyToRename = family },
                        onDeleteFamilyClick = { viewModel.deleteCategoryFamily(family.name) },
                        onAddSubCategoryClick = { familyToAddSub = family },
                        onDeleteSubCategoryClick = { subName ->
                            viewModel.deleteSubCategory(family.name, subName)
                        }
                    )
                }

                item {
                    Button(
                        onClick = { showAddFamilyDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Ajouter une famille de catégorie")
                    }
                }
            }
        }
    }

    if (showAddFamilyDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddFamilyDialog = false },
            title = { Text("Nouvelle famille") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nom de la famille") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCategoryFamily(newName)
                        showAddFamilyDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) { Text("Ajouter") }
            },
            dismissButton = {
                TextButton(onClick = { showAddFamilyDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    familyToRename?.let { family ->
        var newName by remember { mutableStateOf(family.name) }
        AlertDialog(
            onDismissRequest = { familyToRename = null },
            title = { Text("Renommer la famille") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nouveau nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameFamily(family.name, newName)
                        familyToRename = null
                    },
                    enabled = newName.isNotBlank() && newName != family.name
                ) { Text("Renommer") }
            },
            dismissButton = {
                TextButton(onClick = { familyToRename = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    familyToAddSub?.let { family ->
        var subName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { familyToAddSub = null },
            title = { Text("Ajouter une sous-catégorie") },
            text = {
                OutlinedTextField(
                    value = subName,
                    onValueChange = { subName = it },
                    label = { Text("Nom de la sous-catégorie") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addSubCategory(family.name, subName)
                        familyToAddSub = null
                    },
                    enabled = subName.isNotBlank()
                ) { Text("Ajouter") }
            },
            dismissButton = {
                TextButton(onClick = { familyToAddSub = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
fun CategoryFamilyItem(
    family: CategoryFamily,
    onRenameClick: () -> Unit,
    onDeleteFamilyClick: () -> Unit,
    onAddSubCategoryClick: () -> Unit,
    onDeleteSubCategoryClick: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.White.copy(alpha = 0.85f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = family.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Row {
                    IconButton(onClick = onRenameClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Renommer",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDeleteFamilyClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Supprimer",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            family.subCategories.forEach { sub ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { onDeleteSubCategoryClick(sub) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Supprimer sous-catégorie",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            TextButton(
                onClick = onAddSubCategoryClick,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Sous-catégorie",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
