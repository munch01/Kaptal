package com.Muncho.kaptal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Muncho.kaptal.utils.PdfRow

enum class ColumnRole {
    DATE, AMOUNT, CAPITAL, NONE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfColumnPickerSheet(
    rows: List<PdfRow>,
    onDismiss: () -> Unit,
    onConfirm: (startRowIdx: Int, dateColIdx: Int, amountColIdx: Int, capitalColIdx: Int) -> Unit
) {
    val previewRows = rows.take(40) // On affiche plus de lignes pour trouver le début du tableau
    val maxCols = previewRows.maxOfOrNull { it.cells.size } ?: 0
    
    val columnRoles = remember { mutableStateMapOf<Int, ColumnRole>() }
    var startRowIndex by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // --- INSTRUCTIONS ---
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Étape 1 : Cliquez sur la 1ère ligne du tableau", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Étape 2 : Identifiez les colonnes en haut de la grille", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Grille de sélection
            Box(modifier = Modifier.weight(1f).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // En-têtes (Sélecteurs de rôles)
                    item {
                        Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
                            // On ajoute une case vide pour la colonne de sélection de ligne
                            Box(modifier = Modifier.width(40.dp).height(60.dp).border(1.dp, Color.LightGray))
                            
                            repeat(maxCols) { colIndex ->
                                val currentRole = columnRoles[colIndex] ?: ColumnRole.NONE
                                ColumnHeader(
                                    index = colIndex,
                                    role = currentRole,
                                    onRoleClick = {
                                        val nextRole = when (currentRole) {
                                            ColumnRole.NONE -> ColumnRole.DATE
                                            ColumnRole.DATE -> ColumnRole.AMOUNT
                                            ColumnRole.AMOUNT -> ColumnRole.CAPITAL
                                            ColumnRole.CAPITAL -> ColumnRole.NONE
                                        }
                                        if (nextRole != ColumnRole.NONE) {
                                            columnRoles.entries.find { it.value == nextRole }?.let {
                                                columnRoles.remove(it.key)
                                            }
                                        }
                                        columnRoles[colIndex] = nextRole
                                    }
                                )
                            }
                        }
                    }

                    // Données brutes
                    itemsIndexed(previewRows) { rowIndex, row ->
                        val isStartRow = rowIndex == startRowIndex
                        val isIgnored = rowIndex < startRowIndex
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isStartRow) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                .clickable { startRowIndex = rowIndex }
                                .border(0.5.dp, Color.LightGray.copy(alpha = 0.5f))
                        ) {
                            // Sélecteur de ligne de départ
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(48.dp)
                                    .background(if (isStartRow) MaterialTheme.colorScheme.secondary else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isStartRow) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                } else {
                                    Text("${rowIndex + 1}", fontSize = 10.sp, color = if (isIgnored) Color.Gray else Color.Unspecified)
                                }
                            }

                            repeat(maxCols) { colIndex ->
                                val cell = row.cells.getOrNull(colIndex)
                                val role = columnRoles[colIndex] ?: ColumnRole.NONE
                                
                                Box(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(48.dp)
                                        .padding(4.dp)
                                        .background(
                                            when {
                                                isIgnored -> Color.LightGray.copy(alpha = 0.1f)
                                                role != ColumnRole.NONE -> getRoleColor(role).copy(alpha = 0.1f)
                                                else -> Color.Transparent
                                            }
                                        ),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = cell?.text ?: "",
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isIgnored) Color.Gray else Color.Unspecified,
                                        fontWeight = if (role != ColumnRole.NONE) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Boutons d'action
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Annuler")
                }
                
                val canConfirm = columnRoles.values.contains(ColumnRole.DATE) && 
                                 columnRoles.values.contains(ColumnRole.AMOUNT)
                
                Button(
                    onClick = {
                        val dateIdx = columnRoles.entries.find { it.value == ColumnRole.DATE }?.key ?: -1
                        val amountIdx = columnRoles.entries.find { it.value == ColumnRole.AMOUNT }?.key ?: -1
                        val capitalIdx = columnRoles.entries.find { it.value == ColumnRole.CAPITAL }?.key ?: -1
                        onConfirm(startRowIndex, dateIdx, amountIdx, capitalIdx)
                    },
                    enabled = canConfirm,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importer")
                }
            }
        }
    }
}

private fun getRoleColor(role: ColumnRole): Color {
    return when (role) {
        ColumnRole.DATE -> Color(0xFF1976D2)
        ColumnRole.AMOUNT -> Color(0xFF2E7D32)
        ColumnRole.CAPITAL -> Color(0xFFE65100)
        ColumnRole.NONE -> Color.Transparent
    }
}

@Composable
fun ColumnHeader(index: Int, role: ColumnRole, onRoleClick: () -> Unit) {
    val backgroundColor = when (role) {
        ColumnRole.DATE -> Color(0xFFE3F2FD)
        ColumnRole.AMOUNT -> Color(0xFFE8F5E9)
        ColumnRole.CAPITAL -> Color(0xFFFFF3E0)
        ColumnRole.NONE -> Color.Transparent
    }
    
    val textColor = getRoleColor(role)

    Box(
        modifier = Modifier
            .width(120.dp)
            .height(60.dp)
            .background(backgroundColor)
            .border(1.dp, Color.LightGray)
            .clickable { onRoleClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when (role) {
                    ColumnRole.DATE -> "🗓️ Date"
                    ColumnRole.AMOUNT -> "💰 Montant"
                    ColumnRole.CAPITAL -> "📉 Capital"
                    ColumnRole.NONE -> "Cliquer..."
                },
                color = if (role == ColumnRole.NONE) Color.Gray else textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Col. ${index + 1}",
                fontSize = 9.sp,
                color = Color.Gray
            )
        }
    }
}
