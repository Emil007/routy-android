package com.routy.app.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routy.app.R
import com.routy.app.RoutyApplication
import com.routy.app.ui.OfflineBanner
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.GoldenSegmentDto
import com.routy.app.logic.api.SegmentUsageStat
import com.routy.app.logic.api.WalkLogEntryDto
import com.routy.app.logic.geo.walkPathPoints
import com.routy.app.logic.time.formatDurationHours
import com.routy.app.logic.time.parseServerInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as RoutyApplication
    val viewModel: StatsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { StatsViewModel(app.apiClientProvider, app.networkCache, app.bootstrapLoader, app.applicationContext) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var pendingDeleteWalkId by remember { mutableStateOf<Int?>(null) }

    pendingDeleteWalkId?.let { walkId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteWalkId = null },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteWalkId = null
                    viewModel.deleteWalk(walkId)
                }) {
                    Text(stringResource(R.string.map_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteWalkId = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            text = { Text(stringResource(R.string.stats_delete_walk_confirm)) },
        )
    }

    if (uiState.loading) {
        Column(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (!uiState.loading && uiState.stats == null) {
        Column(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.error && uiState.offlineCached) {
                OfflineBanner(modifier = Modifier.padding(bottom = 12.dp))
            }
            Text(stringResource(R.string.common_error))
            TextButton(onClick = viewModel::refresh) {
                Text(stringResource(R.string.stats_retry))
            }
        }
        return
    }

    val stats = uiState.stats!!
    val streak = uiState.streak
    val achievements = uiState.achievements

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (uiState.offlineCached) {
            item { OfflineBanner() }
        }
        if (uiState.error) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.common_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.stats_retry)) }
                }
            }
        }
        uiState.messageRes?.let { res ->
            item {
                Text(
                    stringResource(res),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item {
            GameHubSection(
                pointBalance = uiState.gameDaily?.pointBalance ?: uiState.points?.totalPoints ?: 0,
                streakMultiplier = uiState.gameDaily?.streakMultiplier ?: uiState.points?.streakMultiplier ?: 1.0,
                weeklyPoints = uiState.points?.weeklyPoints ?: 0,
                dailyChallenge = uiState.gameDaily?.dailyChallenge,
                goldenSegments = uiState.goldenSegments,
            )
        }
        item {
            StatsSection(title = stringResource(R.string.stats_your_stats)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatChip("${formatKm(stats.totalLengthM)} ${stringResource(R.string.common_km)}")
                    StatChip("${stats.walkCount} ${stringResource(R.string.stats_walks)}")
                    StatChip("${formatDurationHours(stats.totalDurationMin)}h")
                    StatChip("${stats.segmentsExplored}/${stats.totalSegments} ${stringResource(R.string.stats_segments)}")
                    if (streak != null) {
                        StatChip("${stringResource(R.string.stats_streak_current)}: ${streak.currentStreak}")
                        StatChip("${stringResource(R.string.stats_streak_longest)}: ${streak.longestStreak}")
                    }
                }
            }
        }

        if (uiState.networkUsage.isNotEmpty()) {
            item {
                NetworkUsageSection(
                    usage = uiState.networkUsage,
                    nodeNames = uiState.nodeNames,
                )
            }
        }

        if (uiState.pointsLeaderboard.isNotEmpty()) {
            item {
                StatsSection(title = stringResource(R.string.stats_points_leaderboard)) {
                    uiState.pointsLeaderboard.forEachIndexed { index, entry ->
                        Text(
                            text = "${index + 1}. ${entry.displayName} — ${entry.totalPoints}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        item {
            StatsSection(title = stringResource(R.string.stats_leaderboard)) {
                if (uiState.leaderboard.isEmpty()) {
                    Text(stringResource(R.string.stats_leaderboard_empty), style = MaterialTheme.typography.bodySmall)
                } else {
                    uiState.leaderboard.forEachIndexed { index, entry ->
                        val highlight = entry.userId == uiState.currentUserId
                        Text(
                            text = "${index + 1}. ${entry.displayName} — ${formatKm(entry.totalLengthM)} ${stringResource(R.string.common_km)} (${entry.walkCount})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        if (achievements != null) {
            item {
                StatsSection(title = stringResource(R.string.stats_achievements)) {
                    achievements.scalable.forEach { a ->
                        Text(
                            text = "${a.categoryLabel}: ${a.tierLabel ?: "—"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = a.progressLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        achievements.special.forEach { s ->
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = {
                                    Text(
                                        text = "${if (s.earned) "✓" else "·"} ${s.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            StatsSection(title = stringResource(R.string.stats_recent_walks)) {
                if (uiState.recentWalks.isEmpty()) {
                    Text(stringResource(R.string.stats_no_walks), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        items(uiState.recentWalks) { walk ->
            WalkRow(
                walk = walk,
                nodeNames = uiState.nodeNames,
                nodeCoords = uiState.nodeCoords,
                segmentGeometry = uiState.segmentGeometry,
                deleting = uiState.deletingWalkId == walk.id,
                onDelete = { pendingDeleteWalkId = walk.id },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameHubSection(
    pointBalance: Int,
    streakMultiplier: Double,
    weeklyPoints: Int,
    dailyChallenge: String?,
    goldenSegments: List<GoldenSegmentDto>,
) {
    StatsSection(title = stringResource(R.string.stats_game_hub_title)) {
        Text(
            text = stringResource(R.string.stats_game_point_balance, pointBalance),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatChip(stringResource(R.string.stats_game_streak_multiplier, streakMultiplier))
            StatChip(stringResource(R.string.stats_weekly_points) + ": $weeklyPoints")
        }
        dailyChallenge?.takeIf { it.isNotBlank() }?.let { challenge ->
            Text(challenge, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (goldenSegments.isNotEmpty()) {
            Text(stringResource(R.string.stats_golden_today_title), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            goldenSegments.forEach { golden ->
                Text(
                    text = golden.name?.let { "$it (×${golden.multiplier})" }
                        ?: stringResource(R.string.map_proposal_segment, golden.segmentId) + " (×${golden.multiplier})",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        } else {
            Text(stringResource(R.string.stats_golden_today_empty), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NetworkUsageSection(
    usage: List<SegmentUsageStat>,
    nodeNames: Map<Int, String>,
) {
    fun nodeName(id: Int) = nodeNames[id] ?: "#$id"
    val sorted = usage.sortedByDescending { it.usageCount }
    val mostUsed = sorted.take(5)
    val leastUsed = sorted.takeLast(5).reversed()

    StatsSection(title = stringResource(R.string.stats_network_usage_title)) {
        Text(stringResource(R.string.stats_network_most_used), style = MaterialTheme.typography.labelMedium)
        mostUsed.forEach { stat ->
            Text(
                stringResource(
                    R.string.stats_network_usage_row,
                    nodeName(stat.startNodeId),
                    nodeName(stat.endNodeId),
                    stat.usageCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        if (sorted.size > 5) {
            Text(stringResource(R.string.stats_network_least_used), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            leastUsed.forEach { stat ->
                Text(
                    stringResource(
                        R.string.stats_network_usage_row,
                        nodeName(stat.startNodeId),
                        nodeName(stat.endNodeId),
                        stat.usageCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun StatsSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun StatChip(label: String) {
    AssistChip(onClick = {}, enabled = false, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
}

@Composable
private fun WalkRow(
    walk: WalkLogEntryDto,
    nodeNames: Map<Int, String>,
    nodeCoords: Map<Int, GeoPoint>,
    segmentGeometry: Map<Int, List<GeoPoint>>,
    deleting: Boolean,
    onDelete: () -> Unit,
) {
    fun nodeLabel(id: Int?) = id?.let { nodeNames[it] ?: "#$it" } ?: "?"
    val startEnd = "${nodeLabel(walk.nodeChain.firstOrNull())} → ${nodeLabel(walk.nodeChain.lastOrNull())}"
    val pathPoints = walkPathPoints(
        segmentIds = walk.segmentIds,
        geometryBySegmentId = segmentGeometry,
        fallbackNodeChain = walk.nodeChain,
        fallbackCoords = nodeCoords,
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            WalkPathThumbnail(points = pathPoints)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(formatWalkDate(walk.acceptedAt), style = MaterialTheme.typography.labelMedium)
                if (walk.nickname.isNullOrBlank()) {
                    Text(startEnd, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(walk.nickname!!, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        startEnd,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${formatKm(walk.lengthM)} ${stringResource(R.string.common_km)} · ${walk.durationMin} ${stringResource(R.string.common_min)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (deleting) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            } else {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.map_delete), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun formatKm(lengthM: Int): String = "%.1f".format(Locale.US, lengthM / 1000.0)

private fun formatWalkDate(iso: String): String {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())
    return formatter.format(parseServerInstant(iso))
}
