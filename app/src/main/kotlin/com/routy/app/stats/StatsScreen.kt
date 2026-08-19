package com.routy.app.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.routy.app.logic.api.WalkLogEntryDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as RoutyApplication
    val viewModel: StatsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { StatsViewModel(app.apiClientProvider) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.loading) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.error || uiState.stats == null) {
        Column(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            StatsSection(title = stringResource(R.string.stats_your_stats)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatChip("${formatKm(stats.totalLengthM)} ${stringResource(R.string.common_km)}")
                    StatChip("${stats.walkCount} ${stringResource(R.string.stats_walks)}")
                    StatChip("${stats.totalDurationMin / 60}h")
                    StatChip("${stats.segmentsExplored}/${stats.totalSegments} ${stringResource(R.string.stats_segments)}")
                    if (streak != null) {
                        StatChip("${stringResource(R.string.stats_streak_current)}: ${streak.currentStreak}")
                        StatChip("${stringResource(R.string.stats_streak_longest)}: ${streak.longestStreak}")
                    }
                    uiState.points?.let { points ->
                        StatChip("${stringResource(R.string.stats_points)}: ${points.totalPoints}")
                        StatChip("${stringResource(R.string.stats_weekly_points)}: ${points.weeklyPoints}")
                    }
                }
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
            WalkRow(walk)
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
private fun WalkRow(walk: WalkLogEntryDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(formatWalkDate(walk.acceptedAt), style = MaterialTheme.typography.labelMedium)
            Text(
                text = walk.nickname ?: "${walk.nodeChain.firstOrNull()} → ${walk.nodeChain.lastOrNull()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${formatKm(walk.lengthM)} ${stringResource(R.string.common_km)} · ${walk.durationMin} ${stringResource(R.string.common_min)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatKm(lengthM: Int): String = "%.1f".format(Locale.US, lengthM / 1000.0)

private fun formatWalkDate(iso: String): String {
    val instant = Instant.parse(iso.replace(" ", "T") + "Z")
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}
