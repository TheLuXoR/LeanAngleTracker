package com.example.leanangletracker.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A Box that draws an animated border segment around its content.
 * Similar to CircularProgressIndicator but follows any [Shape].
 */
@Composable
fun AnimatedBorderBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    borderWidth: Dp = 2.dp,
    borderColor: Color = MaterialTheme.colorScheme.primary,
    animationDuration: Int = 2000,
    isAnimating: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AnimatedBorder")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(animationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Box(
        modifier = modifier
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = Path()
                
                // Convert Outline to Path
                when (outline) {
                    is Outline.Rectangle -> path.addRect(outline.rect)
                    is Outline.Rounded -> path.addRoundRect(outline.roundRect)
                    is Outline.Generic -> path.addPath(outline.path)
                }

                val pathMeasure = PathMeasure()
                pathMeasure.setPath(path, false)
                val totalLength = pathMeasure.length

                onDrawWithContent {
                    drawContent()
                    if (isAnimating && totalLength > 0f) {
                        val segmentLength = totalLength * 0.25f // Length of the animated segment
                        val startDist = progress * totalLength
                        val endDist = startDist + segmentLength

                        val strokeWidthPx = borderWidth.toPx()

                        if (endDist <= totalLength) {
                            val segmentPath = Path()
                            pathMeasure.getSegment(startDist, endDist, segmentPath)
                            drawPath(
                                path = segmentPath,
                                color = borderColor,
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                            )
                        } else {
                            // Handle wrapping around the path
                            val segmentPath1 = Path()
                            pathMeasure.getSegment(startDist, totalLength, segmentPath1)
                            val segmentPath2 = Path()
                            pathMeasure.getSegment(0f, endDist % totalLength, segmentPath2)
                            
                            drawPath(
                                path = segmentPath1,
                                color = borderColor,
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                            )
                            drawPath(
                                path = segmentPath2,
                                color = borderColor,
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
