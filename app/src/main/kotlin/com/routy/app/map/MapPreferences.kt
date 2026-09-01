package com.routy.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.routy.app.RoutyApplication

data class MapPreferencesState(
    val baseMapStyle: BaseMapStyle,
    val waymarkedOverlay: Boolean,
    val setBaseMapStyle: (BaseMapStyle) -> Unit,
    val setWaymarkedOverlay: (Boolean) -> Unit,
)

@Composable
fun rememberMapPreferences(): MapPreferencesState {
    val store = (LocalContext.current.applicationContext as RoutyApplication).mapPreferencesStore
    var style by remember { mutableStateOf(store.baseMapStyle) }
    var waymarked by remember { mutableStateOf(store.waymarkedOverlay) }
    return MapPreferencesState(
        baseMapStyle = style,
        waymarkedOverlay = waymarked,
        setBaseMapStyle = {
            style = it
            store.baseMapStyle = it
        },
        setWaymarkedOverlay = {
            waymarked = it
            store.waymarkedOverlay = it
        },
    )
}
