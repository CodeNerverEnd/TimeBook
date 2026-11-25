package com.liubishchev.timebook.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ColorDot(colorArgb: Int, size: Int = 10) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(colorArgb))
    )
}

/** 标签小胶囊：浅底色 + 同色文字，用来标出事项挂在哪些标签下。 */
@Composable
fun TagChip(name: String, colorArgb: Int, modifier: Modifier = Modifier) {
    val color = Color(colorArgb)
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = name,
            fontSize = 11.sp,
            maxLines = 1,
            color = color
        )
    }
}

@Composable
fun SectionHeader(title: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** 浅色轨道 + 实际用时的横向占比条。 */
@Composable
fun RatioBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

/**
 * 极简柱状图：不引第三方图表库，Canvas 画柱 + 下方用 Text 排标签。
 * 按日（最多 31 根）/ 按月（12 根）都够用。
 */
@Composable
fun BarChart(
    values: List<Long>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    if (values.isEmpty()) return
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val maxValue = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)

    Column(modifier = modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            val n = values.size
            val gap = size.width / (n * 4f)
            val barWidth = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
            val radius = CornerRadius(barWidth / 3f, barWidth / 3f)

            values.forEachIndexed { index, value ->
                val x = index * (barWidth + gap)
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = radius
                )
                if (value > 0L) {
                    val h = (value.toFloat() / maxValue) * size.height
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = radius
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val show = labels.size <= 12 || index % 5 == 0 || index == labels.lastIndex
                Text(
                    text = if (show) label else "",
                    modifier = Modifier.weight(1f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun VerticalGap(height: Int) {
    Spacer(Modifier.height(height.dp))
}

@Composable
fun HorizontalGap(width: Int) {
    Spacer(Modifier.width(width.dp))
}
