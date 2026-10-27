package com.routy.app.route

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.routy.app.logic.api.GeoPoint
import com.routy.app.logic.api.RouteDisplayPayload
import com.routy.app.logic.geo.LatLng
import com.routy.app.logic.route.VoiceCue
import com.routy.app.logic.route.VoiceCueTracker
import com.routy.app.logic.route.WaypointProgressTracker
import com.routy.app.logic.route.goldenCanonicalForFinishedHop

/**
 * Route-live-effects, extracted byte-faithfully from RouteScreen (J1, §9.4).
 * Same behaviour; callers now live in the atlas shell from J2 on. The service is
 * never stopped explicitly on dispose — only unbindService (no stopAll).
 */
@Composable
internal fun ActiveRouteLocationEffect(uiState: RouteUiState, locationActive: Boolean, viewModel: RouteViewModel) {
    val context = LocalContext.current
    DisposableEffect(uiState.mode, locationActive, uiState.trackEnabled, uiState.voiceEnabled) {
        val needsService = RouteTrackingForegroundService.needsForegroundService(uiState.trackEnabled, uiState.voiceEnabled)
        if (uiState.mode != RouteMode.ACTIVE || !locationActive || needsService) {
            return@DisposableEffect onDispose {}
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    viewModel.setMyLocation(
                        GeoPoint(loc.latitude, loc.longitude),
                        if (loc.hasAccuracy()) loc.accuracy else null,
                    )
                    viewModel.addRetagLocationSample(
                        loc.latitude,
                        loc.longitude,
                        if (loc.hasAccuracy()) loc.accuracy else null,
                    )
                    viewModel.setLocationMotion(
                        if (loc.hasBearing()) loc.bearing else null,
                        if (loc.hasSpeed()) loc.speed else null,
                    )
                }
            }
        }
        requestLocationUpdatesIfPermitted(context, client, request, callback)
        onDispose { client.removeLocationUpdates(callback) }
    }
}

@Composable
internal fun ActiveRouteTrackingServiceEffect(
    uiState: RouteUiState,
    route: RouteDisplayPayload,
    viewModel: RouteViewModel,
    accountLocaleTag: String,
) {
    val context = LocalContext.current
    var service by remember { mutableStateOf<RouteTrackingForegroundService?>(null) }
    val routeKey = remember(route.nodeChain) { route.nodeChain.joinToString("-") }

    val needsService = RouteTrackingForegroundService.needsForegroundService(uiState.trackEnabled, uiState.voiceEnabled)

    LaunchedEffect(needsService) {
        if (!needsService) {
            RouteTrackingForegroundService.stopAll(context)
            service = null
        }
    }

    DisposableEffect(needsService, routeKey, accountLocaleTag) {
        if (!needsService) {
            return@DisposableEffect onDispose {}
        }

        RouteTrackingForegroundService.start(
            context = context,
            stations = route.stations,
            routeGeometry = route.geometry,
            segments = uiState.segments,
            routeSegmentIds = route.segmentIds,
            todayGoldenCanonicalIds = uiState.todayGoldenSegmentIds,
            goldenHitSegmentIds = uiState.goldenHitIds,
            routeKey = routeKey,
            accountLocaleTag = accountLocaleTag,
            trackEnabled = uiState.trackEnabled,
            voiceEnabled = uiState.voiceEnabled,
            completedWaypointIndex = uiState.completedWaypointIndex,
            voiceAnnouncedIndex = uiState.voiceAnnouncedIndex,
        )

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as RouteTrackingForegroundService.LocalBinder).getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        context.bindService(
            Intent(context, RouteTrackingForegroundService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        onDispose {
            runCatching { context.unbindService(connection) }
        }
    }

    LaunchedEffect(needsService, uiState.trackEnabled, uiState.voiceEnabled) {
        if (needsService) {
            RouteTrackingForegroundService.setTrackEnabled(context, uiState.trackEnabled)
            RouteTrackingForegroundService.setVoiceEnabled(context, uiState.voiceEnabled)
        }
    }

    LaunchedEffect(service) {
        val bound = service ?: return@LaunchedEffect
        bound.state.collect { serviceState ->
            serviceState.myLocation?.let(viewModel::setMyLocation)
            viewModel.setLocationMotion(serviceState.locationBearing, serviceState.locationSpeed)
            viewModel.setTrackedGeometry(serviceState.trackedGeometry)
            viewModel.syncFromTrackingService(
                completedWaypointIndex = serviceState.completedWaypointIndex,
                voiceAnnouncedIndex = serviceState.voiceAnnouncedIndex,
                trackingActive = serviceState.active,
                trackEnabled = serviceState.trackEnabled,
                myLocation = serviceState.myLocation,
                locationBearing = serviceState.locationBearing,
                locationSpeed = serviceState.locationSpeed,
                trackedGeometry = serviceState.trackedGeometry,
            )
            if (serviceState.autoCompleteRequested) {
                bound.consumeAutoCompleteRequest()
                RouteTrackingForegroundService.stopAll(context)
                viewModel.setTrackEnabled(false)
                viewModel.complete()
            }
        }
    }

    LaunchedEffect(service) {
        val bound = service ?: return@LaunchedEffect
        bound.stopTrackRequested.collect {
            viewModel.setTrackEnabled(false)
        }
    }
}

@Composable
internal fun ActiveTrackingEffects(
    uiState: RouteUiState,
    route: RouteDisplayPayload,
    viewModel: RouteViewModel,
    accountLocaleTag: String,
) {
    val context = LocalContext.current
    val voiceController = rememberVoiceGuidanceController(accountLocaleTag)
    val cueController = remember(context) { TrackCueController(context) }
    DisposableEffect(Unit) { onDispose { cueController.release() } }

    val voiceTracker = remember(route.nodeChain) {
        VoiceCueTracker(route.stations).also { it.restore(uiState.voiceAnnouncedIndex) }
    }
    val progressTracker = remember(route.nodeChain) {
        WaypointProgressTracker(route.stations).also { it.restore(uiState.completedWaypointIndex) }
    }
    var pendingCue by remember(route.nodeChain) { mutableStateOf<VoiceCue?>(null) }
    val goldenSoundCued = remember(route.nodeChain) { mutableSetOf<Int>() }

    LaunchedEffect(uiState.completedWaypointIndex, route.nodeChain) {
        progressTracker.restore(uiState.completedWaypointIndex)
    }
    LaunchedEffect(uiState.voiceAnnouncedIndex, route.nodeChain) {
        voiceTracker.restore(uiState.voiceAnnouncedIndex)
    }

    val location = uiState.myLocation
    val voiceActive = uiState.voiceEnabled && !RouteTrackingForegroundService.needsForegroundService(uiState.trackEnabled, uiState.voiceEnabled)
    LaunchedEffect(location, voiceActive, uiState.trackEnabled) {
        if (location == null) return@LaunchedEffect
        val latLng = LatLng(location.lat, location.lng)
        if (voiceActive) voiceTracker.onLocationUpdate(latLng)?.let { pendingCue = it }
        if (uiState.trackEnabled) {
            progressTracker.onLocationUpdate(latLng)?.let { completed ->
                if (completed > uiState.completedWaypointIndex) {
                    for (arrived in (uiState.completedWaypointIndex + 1)..completed) {
                        goldenCanonicalForFinishedHop(
                            arrivedStationIndex = arrived,
                            routeSegmentIds = route.segmentIds,
                            todayGoldenCanonicalIds = uiState.todayGoldenSegmentIds,
                            segments = uiState.segments,
                            alreadyCued = goldenSoundCued,
                        )?.let { canon ->
                            goldenSoundCued.add(canon)
                            cueController.goldenHit()
                        }
                    }
                    viewModel.onWaypointCompleted(completed)
                    if (completed >= route.stations.lastIndex) {
                        cueController.routeCompleted()
                    } else {
                        cueController.waypointReached()
                    }
                }
            }
        }
    }

    pendingCue?.let { cue ->
        val spokenText = cue.toSpokenText(context, accountLocaleTag)
        LaunchedEffect(cue) {
            voiceController.speak(spokenText)
            viewModel.onVoiceCueAnnounced(voiceTracker.announcedCount())
            pendingCue = null
        }
    }
}

@SuppressLint("MissingPermission")
private fun requestLocationUpdatesIfPermitted(
    context: Context,
    client: com.google.android.gms.location.FusedLocationProviderClient,
    request: LocationRequest,
    callback: LocationCallback,
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
    client.requestLocationUpdates(request, callback, Looper.getMainLooper())
}
