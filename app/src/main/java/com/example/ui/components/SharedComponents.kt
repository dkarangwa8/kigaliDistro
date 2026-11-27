package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DeliveryOrder
import com.example.model.KigaliCatalog
import com.example.model.OrderStatus
import com.example.model.PaymentStatus
import com.example.model.TrafficLevel
import com.example.service.ProofMediaUtils
import com.example.ui.theme.BrewGold
import com.example.ui.theme.BrewGoldContainer
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.KigaliEmerald
import com.example.ui.theme.KigaliMintTint
import com.example.ui.theme.PaidVerifiedBlue
import com.example.ui.theme.TrafficHeavyRed
import com.example.ui.theme.TrafficLightGreen
import com.example.ui.theme.TrafficModerateAmber
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun StatusPill(status: OrderStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        OrderStatus.PENDING -> BrewGoldContainer to Color(0xFF6B3E00)
        OrderStatus.ACCEPTED, OrderStatus.LOADING -> Color(0xFFDDEEFF) to PaidVerifiedBlue
        OrderStatus.EN_ROUTE -> KigaliMintTint to KigaliEmerald
        OrderStatus.ARRIVED -> Color(0xFFFFE2D6) to Color(0xFF9E3814)
        OrderStatus.DELIVERED -> Color(0xFFD2F4E3) to TrafficLightGreen
        OrderStatus.CANCELLED -> Color(0xFFFFDAD6) to TrafficHeavyRed
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun TrafficPill(level: TrafficLevel, etaMinutes: Long, modifier: Modifier = Modifier) {
    val color = when (level) {
        TrafficLevel.LIGHT -> TrafficLightGreen
        TrafficLevel.MODERATE -> TrafficModerateAmber
        TrafficLevel.HEAVY -> TrafficHeavyRed
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Traffic,
                contentDescription = level.label,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${level.label} · ${etaMinutes}m",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun OrderSummaryCard(
    order: DeliveryOrder,
    modifier: Modifier = Modifier,
    actionButton: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.barName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${order.barNeighborhood}, Kigali · ${order.totalCrates} crate(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusPill(status = order.parsedStatus)
            }

            Text(
                text = order.itemsSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = KigaliCatalog.formatRwf(order.totalAmountRwf),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if (order.parsedStatus == OrderStatus.DELIVERED) {
                    Surface(
                        color = PaidVerifiedBlue.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "${order.parsedPaymentMethod.shortBadge} · ${PaymentStatus.PAID.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PaidVerifiedBlue,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                } else if (order.parsedStatus != OrderStatus.CANCELLED) {
                    TrafficPill(level = order.parsedTrafficLevel, etaMinutes = order.etaMinutes)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = "Timestamp",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatTimestamp(order.updatedAt ?: order.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (order.distributorName.isNotBlank()) {
                    Text(
                        text = "Driver: ${order.distributorName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (actionButton != null) {
                actionButton()
            }
        }
    }
}

@Composable
fun DeliveryProgressStepper(currentStatus: OrderStatus, modifier: Modifier = Modifier) {
    val steps = listOf(
        OrderStatus.PENDING to "Placed",
        OrderStatus.ACCEPTED to "Accepted",
        OrderStatus.LOADING to "Loading",
        OrderStatus.EN_ROUTE to "En Route",
        OrderStatus.ARRIVED to "Arrived",
        OrderStatus.DELIVERED to "Verified"
    )
    val activeIdx = currentStatus.stepIndex

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        steps.forEachIndexed { idx, (_, shortLabel) ->
            val completed = activeIdx >= idx && activeIdx >= 0
            val isCurrent = activeIdx == idx
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                completed -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .border(
                            width = if (isCurrent) 2.dp else 0.dp,
                            color = if (isCurrent) BrewGold else Color.Transparent,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (completed) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = shortLabel,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    } else {
                        Text(
                            text = "${idx + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SignaturePreviewCanvas(
    serializedStrokes: String,
    modifier: Modifier = Modifier
) {
    val strokes = remember(serializedStrokes) {
        ProofMediaUtils.parseSignatureStrokes(serializedStrokes)
    }
    val inkColor = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        strokes.forEach { pts ->
            if (pts.size >= 2) {
                val path = Path().apply {
                    moveTo(pts.first().x * size.width, pts.first().y * size.height)
                    pts.drop(1).forEach { pt ->
                        lineTo(pt.x * size.width, pt.y * size.height)
                    }
                }
                drawPath(
                    path = path,
                    color = inkColor,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            } else if (pts.size == 1) {
                drawCircle(
                    color = inkColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(pts.first().x * size.width, pts.first().y * size.height)
                )
            }
        }
    }
}

@Composable
fun StarRatingRow(
    rating: Int,
    onRatingChange: ((Int) -> Unit)? = null,
    starSizeDp: Int = 28
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            val selected = i <= rating
            val iconModifier = if (onRatingChange != null) {
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onRatingChange(i) }
                    .padding(10.dp)
            } else {
                Modifier.size(starSizeDp.dp)
            }
            Icon(
                imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = "$i stars",
                tint = if (selected) BrewGold else MaterialTheme.colorScheme.outline,
                modifier = iconModifier
            )
        }
    }
}

fun formatTimestamp(timestamp: Timestamp?): String {
    val date = (timestamp ?: Timestamp.now()).toDate()
    val fmt = SimpleDateFormat("MMM d · HH:mm", Locale.getDefault())
    return fmt.format(date)
}
