package com.Muncho.kaptal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun KaptalGeometricLogo(
    modifier: Modifier = Modifier,
    isBackgroundMode: Boolean = true
) {
    val color1 = Color(0xFF0D47A1)
    val color2 = Color(0xFF00E5FF)
    val alphaFactor = if (isBackgroundMode) 0.12f else 1.0f

    Canvas(modifier = modifier.fillMaxSize()) {
        // Récupération des dimensions réelles de l'écran/conteneur
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        // Taille du logo basée sur la largeur de l'écran (ex: 55% de la largeur)
        val logoSize = size.width * 0.55f
        val strokeWidthVal = logoSize / 10f

        val gradientBrush = Brush.linearGradient(
            colors = listOf(color1.copy(alpha = alphaFactor), color2.copy(alpha = alphaFactor)),
            start = Offset(centerX - logoSize / 2, centerY - logoSize / 2),
            end = Offset(centerX + logoSize / 2, centerY + logoSize / 2)
        )

        // Décalage léger à gauche pour équilibrer la forme asymétrique du K
        val visualCenterX = centerX - (logoSize * 0.1f)

        // 1. Barre verticale principale
        val barPath = Path().apply {
            moveTo(visualCenterX - logoSize * 0.25f, centerY - logoSize * 0.45f)
            lineTo(visualCenterX - logoSize * 0.25f, centerY + logoSize * 0.45f)
        }
        drawPath(
            path = barPath,
            brush = gradientBrush,
            style = Stroke(width = strokeWidthVal, cap = StrokeCap.Round)
        )

        // 2. Branches diagonales du K
        val branchPath = Path().apply {
            // Branche du haut
            moveTo(visualCenterX - logoSize * 0.25f, centerY)
            lineTo(visualCenterX + logoSize * 0.30f, centerY - logoSize * 0.40f)

            // Branche du bas
            moveTo(visualCenterX - logoSize * 0.25f, centerY)
            lineTo(visualCenterX + logoSize * 0.30f, centerY + logoSize * 0.40f)
        }
        drawPath(
            path = branchPath,
            brush = gradientBrush,
            style = Stroke(width = strokeWidthVal, cap = StrokeCap.Round)
        )

        // 3. Sphère / Nœud central
        drawCircle(
            brush = gradientBrush,
            radius = strokeWidthVal * 1.3f,
            center = Offset(visualCenterX - logoSize * 0.25f, centerY)
        )
    }
}