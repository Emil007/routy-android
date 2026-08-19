package com.routy.app.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.routy.app.R
import com.routy.app.RoutyApplication

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapScreen(
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as RoutyApplication
    val viewModel: MapViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MapViewModel(app.networkCache, app.bootstrapLoader) }
        },
    )
    val uiState by viewModel.uiState.collectAsState()
    var mapStyle by remember { mutableStateOf(BaseMapStyle.STREETS) }

    if (uiState.loading && uiState.nodes.isEmpty() && !uiState.loadFailed) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.loadFailed && uiState.nodes.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.map_load_error))
            TextButton(onClick = viewModel::refresh) {
                Text(stringResource(R.string.stats_retry))
            }
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        RoutyMapView(
            style = mapStyle,
            nodes = uiState.nodes,
            segments = uiState.segments,
            routeGeometry = emptyList(),
            stations = emptyList(),
            myLocation = null,
            fitKey = uiState.nodes.size,
            fitToRouteOnly = false,
            emphasizeNetworkSegments = true,
            selectedNodeId = uiState.selectedNode?.id,
            onMapClick = viewModel::onMapClick,
            modifier = Modifier.fillMaxSize(),
        )

        MapStyleSwitcher(
            selected = mapStyle,
            onSelect = { mapStyle = it },
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            uiState.selectedNode?.let { node ->
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.name ?: "#${node.id}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (node.isHome) {
                                Text(
                                    text = stringResource(R.string.map_node_home),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        CompactOutlinedButton(onClick = viewModel::clearSelection) {
                            Text(stringResource(R.string.common_close), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shape = MaterialTheme.shapes.medium,
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                stringResource(R.string.map_nodes_count, uiState.nodes.size),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                    if (uiState.offlineCached) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(stringResource(R.string.map_offline_cached), style = MaterialTheme.typography.labelSmall)
                            },
                        )
                    }
                    CompactButton(onClick = onStartRecording) {
                        Text(stringResource(R.string.map_record_track), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Text(
                text = stringResource(R.string.map_tap_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

private val CompactPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)

@Composable
private fun CompactButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, enabled = enabled, contentPadding = CompactPadding, content = content)
}

@Composable
private fun CompactOutlinedButton(onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = CompactPadding, content = content)
}
