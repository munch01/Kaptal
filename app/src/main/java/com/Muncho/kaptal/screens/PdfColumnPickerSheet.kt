package com.Muncho.kaptal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
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
    DATE, AMOUNT, PRINCIPAL, INTEREST, INSURANCE, REMAINING_DEBT, NONE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfColumnPickerSheet(
    rows: List<PdfRow>,
    onDismiss: () -> Unit,
    onConfirm: (
        startRowIdx: Int, 
        dateColIdx: Int, 
        amountColIdx: Int, 
        principalColIdx: Int, 
        interestColIdx: Int, 
        insuranceColIdx: Int, 
        remainingDebtColIdx: Int
    ) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    var startRowIndex by remember { mutableIntStateOf(-1) }
    // Explicitly type the mutable state map to avoid inference errors
    val columnRoles = remember { mutableStateMapOf<Int, ColumnRole>() }
    val horizontalScrollState = rememberScrollState()

    LaunchedEffect(rows) {
        if (startRowIndex == -1) {
            val suggestedIdx = rows.indexOfFirst { row -> 
                row.cells.any { it.text.contains("/") && it.text.length >= 8 } 
            }
            if (suggestedIdx != -1) startRowIndex = suggestedIdx
        }
    }

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
            Text(
                text = if (currentStep == 1) "Étape 1 : Début des données" else "Étape 2 : Vos colonnes",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            
            if (currentStep == 2) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip("Date", columnRoles.values.contains(ColumnRole.DATE))
                    StatusChip("Mensualité", columnRoles.values.contains(ColumnRole.AMOUNT))
                    StatusChip("Reste à payer", columnRoles.values.contains(ColumnRole.REMAINING_DEBT))
                }
            } else {
                Text(
                    text = "Cliquez sur la ligne qui commence votre tableau.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f).border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))) {
                // VISIBILITÉ TOTALE : On montre tout pour que l'utilisateur puisse vérifier page 2
                val visibleRows = if (currentStep == 2) rows.drop(startRowIndex) else rows
                val maxCols = visibleRows.flatMap { it.cells }.maxOfOrNull { it.colIndex } ?: 5
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (currentStep == 2) {
                        item {
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(start = 40.dp)
                                    .horizontalScroll(horizontalScrollState)
                            ) {
                                repeat(maxCols + 1) { colIndex ->
                                    val currentRole = columnRoles[colIndex] ?: ColumnRole.NONE
                                    ColumnHeaderWithMenu(colIndex, currentRole) { selectedRole ->
                                        if (selectedRole != ColumnRole.NONE) {
                                            // Properly find and remove existing assignment for this role
                                            val keysToRemove = columnRoles.filter { it.value == selectedRole }.keys
                                            keysToRemove.forEach { columnRoles.remove(it) }
                                        }
                                        columnRoles[colIndex] = selectedRole
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(visibleRows) { indexInView, row ->
                        val realRowIndex = if (currentStep == 2) indexInView + startRowIndex else indexInView
                        val isSelected = realRowIndex == startRowIndex
                        val isAbove = realRowIndex < startRowIndex && startRowIndex != -1 && currentStep == 1
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected && currentStep == 1) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                .clickable { if (currentStep == 1) startRowIndex = realRowIndex }
                                .border(0.5.dp, Color.LightGray.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(48.dp)
                                    .background(if (isSelected && currentStep == 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .border(0.5.dp, Color.LightGray.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected && currentStep == 1) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                else Text("${realRowIndex + 1}", fontSize = 10.sp, color = if (isAbove) Color.LightGray else Color.Gray)
                            }

                            Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                                repeat(maxCols + 1) { colIdx ->
                                    val cell = row.cells.find { it.colIndex == colIdx }
                                    val role = columnRoles[colIdx] ?: ColumnRole.NONE
                                    
                                    Box(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .height(48.dp)
                                            .padding(4.dp)
                                            .background(
                                                if (currentStep == 2 && role != ColumnRole.NONE) getRoleColor(role).copy(alpha = 0.1f)
                                                else Color.Transparent
                                            )
                                            .border(0.5.dp, Color.LightGray.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = cell?.text ?: "",
                                            fontSize = 10.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isAbove && currentStep == 1) Color.LightGray else Color.Unspecified,
                                            fontWeight = if (role != ColumnRole.NONE) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = { if (currentStep == 2) currentStep = 1 else onDismiss() }, modifier = Modifier.weight(1f)) {
                    Text(if (currentStep == 2) "Retour" else "Annuler")
                }
                
                if (currentStep == 1) {
                    Button(
                        onClick = { currentStep = 2 },
                        enabled = startRowIndex != -1,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Suivant")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                } else {
                    val hasDate = columnRoles.values.contains(ColumnRole.DATE)
                    val hasAmount = columnRoles.values.contains(ColumnRole.AMOUNT)
                    
                    Button(
                        onClick = {
                            val dIdx = columnRoles.filter { it.value == ColumnRole.DATE }.keys.firstOrNull() ?: -1
                            val aIdx = columnRoles.filter { it.value == ColumnRole.AMOUNT }.keys.firstOrNull() ?: -1
                            val pIdx = columnRoles.filter { it.value == ColumnRole.PRINCIPAL }.keys.firstOrNull() ?: -1
                            val iIdx = columnRoles.filter { it.value == ColumnRole.INTEREST }.keys.firstOrNull() ?: -1
                            val insIdx = columnRoles.filter { it.value == ColumnRole.INSURANCE }.keys.firstOrNull() ?: -1
                            val cIdx = columnRoles.filter { it.value == ColumnRole.REMAINING_DEBT }.keys.firstOrNull() ?: -1
                            
                            onConfirm(startRowIndex, dIdx, aIdx, pIdx, iIdx, insIdx, cIdx)
                        },
                        enabled = hasDate && hasAmount,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CloudDownload, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importer")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(label: String, isDone: Boolean) {
    Surface(
        color = if (isDone) Color(0xFFE8F5E9) else Color(0xFFFDECEA),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDone) Color(0xFF2E7D32) else Color(0xFFC62828))
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isDone) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDone) Color(0xFF2E7D32) else Color(0xFFC62828))
        }
    }
}

@Composable
fun ColumnHeaderWithMenu(index: Int, role: ColumnRole, onRoleSelected: (ColumnRole) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    
    val bgColor = when (role) {
        ColumnRole.DATE -> Color(0xFFE3F2FD)
        ColumnRole.AMOUNT -> Color(0xFFE8F5E9)
        ColumnRole.PRINCIPAL -> Color(0xFFFFF3E0)
        ColumnRole.INTEREST -> Color(0xFFF3E5F5)
        ColumnRole.INSURANCE -> Color(0xFFE0F7FA)
        ColumnRole.REMAINING_DEBT -> Color(0xFFFFEBEE)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .width(130.dp)
            .height(60.dp)
            .background(bgColor)
            .border(1.dp, if (role == ColumnRole.NONE) Color.LightGray.copy(alpha = 0.5f) else getRoleColor(role))
            .clickable { expanded = true }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when (role) {
                    ColumnRole.DATE -> "🗓️ Date"
                    ColumnRole.AMOUNT -> "💰 Mensualité"
                    ColumnRole.PRINCIPAL -> "🧱 Principal"
                    ColumnRole.INTEREST -> "📈 Intérêts"
                    ColumnRole.INSURANCE -> "🛡️ Assurance"
                    ColumnRole.REMAINING_DEBT -> "📉 Restant"
                    else -> "Choisir..."
                },
                color = if (role == ColumnRole.NONE) Color.Gray else getRoleColor(role),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text("Col. ${index + 1}", fontSize = 8.sp, color = Color.Gray)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("🗓️ Date") }, onClick = { onRoleSelected(ColumnRole.DATE); expanded = false })
            DropdownMenuItem(text = { Text("💰 Mensualité") }, onClick = { onRoleSelected(ColumnRole.AMOUNT); expanded = false })
            DropdownMenuItem(text = { Text("🧱 Part Capital (Principal)") }, onClick = { onRoleSelected(ColumnRole.PRINCIPAL); expanded = false })
            DropdownMenuItem(text = { Text("📈 Part Intérêts") }, onClick = { onRoleSelected(ColumnRole.INTEREST); expanded = false })
            DropdownMenuItem(text = { Text("🛡️ Part Assurance") }, onClick = { onRoleSelected(ColumnRole.INSURANCE); expanded = false })
            DropdownMenuItem(text = { Text("📉 Capital Restant Dû") }, onClick = { onRoleSelected(ColumnRole.REMAINING_DEBT); expanded = false })
            HorizontalDivider()
            DropdownMenuItem(text = { Text("❌ Ignorer") }, onClick = { onRoleSelected(ColumnRole.NONE); expanded = false })
        }
    }
}

private fun getRoleColor(role: ColumnRole): Color {
    return when (role) {
        ColumnRole.DATE -> Color(0xFF1976D2)
        ColumnRole.AMOUNT -> Color(0xFF2E7D32)
        ColumnRole.PRINCIPAL -> Color(0xFFE65100)
        ColumnRole.INTEREST -> Color(0xFF7B1FA2)
        ColumnRole.INSURANCE -> Color(0xFF0097A7)
        ColumnRole.REMAINING_DEBT -> Color(0xFFD32F2F)
        else -> Color.Transparent
    }
}
