package com.example.service

import android.util.Log
import com.example.BuildConfig
import com.example.model.DeliveryOrder
import com.example.model.KigaliCatalog
import com.example.model.TrafficLevel
import com.google.android.gms.maps.model.LatLng
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

@JsonClass(generateAdapter = true)
data class ComputeRoutesRequest(
    val origin: RouteWaypoint,
    val destination: RouteWaypoint,
    val intermediates: List<RouteWaypoint>? = null,
    val travelMode: String = "DRIVE",
    val routingPreference: String = "TRAFFIC_AWARE_OPTIMAL",
    val optimizeWaypointOrder: Boolean? = null,
    val extraComputations: List<String> = listOf("TRAFFIC_ON_POLYLINE")
)

@JsonClass(generateAdapter = true)
data class RouteWaypoint(
    val location: RouteLocation
)

@JsonClass(generateAdapter = true)
data class RouteLocation(
    val latLng: RouteLatLng
)

@JsonClass(generateAdapter = true)
data class RouteLatLng(
    val latitude: Double,
    val longitude: Double
)

@JsonClass(generateAdapter = true)
data class ComputeRoutesResponse(
    val routes: List<GoogleRoute>? = null
)

@JsonClass(generateAdapter = true)
data class GoogleRoute(
    val distanceMeters: Int? = null,
    val duration: String? = null,
    val staticDuration: String? = null,
    val description: String? = null,
    val polyline: GoogleEncodedPolyline? = null,
    val optimizedIntermediateWaypointIndex: List<Int>? = null
)

@JsonClass(generateAdapter = true)
data class GoogleEncodedPolyline(
    val encodedPolyline: String? = null
)

interface GoogleRoutesApi {
    @POST("directions/v2:computeRoutes")
    suspend fun computeRoutes(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String =
            "routes.duration,routes.staticDuration,routes.distanceMeters,routes.description,routes.polyline.encodedPolyline,routes.optimizedIntermediateWaypointIndex",
        @Body body: ComputeRoutesRequest
    ): ComputeRoutesResponse
}

data class RouteOptimizationResult(
    val distanceKm: Double,
    val etaMinutes: Int,
    val trafficLevel: TrafficLevel,
    val routeSummary: String,
    val polylinePoints: List<LatLng>,
    val usedLiveGoogleApi: Boolean
)

data class MultiStopRoutePlan(
    val orderedStops: List<DeliveryOrder>,
    val totalDistanceKm: Double,
    val totalEtaMinutes: Int,
    val trafficLevel: TrafficLevel,
    val routeSummary: String,
    val polylinePoints: List<LatLng>,
    val usedLiveGoogleApi: Boolean
)

object PolylineDecoder {
    fun decode(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dLat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dLat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dLng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dLng
            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }
}

class KigaliRouteOptimizer {

    private val api: GoogleRoutesApi by lazy {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://routes.googleapis.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GoogleRoutesApi::class.java)
    }

    fun hasConfiguredMapsKey(): Boolean {
        val key = BuildConfig.MAPS_API_KEY
        return key.isNotBlank() && key != "MY_GOOGLE_MAPS_API_KEY"
    }

    suspend fun computeTrafficAwareRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        destNeighborhood: String
    ): RouteOptimizationResult = withContext(Dispatchers.IO) {
        if (hasConfiguredMapsKey()) {
            try {
                val request = ComputeRoutesRequest(
                    origin = RouteWaypoint(RouteLocation(RouteLatLng(originLat, originLng))),
                    destination = RouteWaypoint(RouteLocation(RouteLatLng(destLat, destLng)))
                )
                val response = api.computeRoutes(apiKey = BuildConfig.MAPS_API_KEY, body = request)
                val route = response.routes?.firstOrNull()
                if (route != null) {
                    val distKm = (((route.distanceMeters ?: 0) / 1000.0) * 10.0).roundToInt() / 10.0
                    val liveSeconds = parseDurationSeconds(route.duration)
                    val staticSeconds = parseDurationSeconds(route.staticDuration)
                    val ratio = if (staticSeconds > 0) liveSeconds.toDouble() / staticSeconds.toDouble() else 1.15
                    val traffic = when {
                        ratio >= 1.35 -> TrafficLevel.HEAVY
                        ratio >= 1.12 -> TrafficLevel.MODERATE
                        else -> TrafficLevel.LIGHT
                    }
                    val etaMin = max(2, (liveSeconds / 60.0).roundToInt())
                    val points = route.polyline?.encodedPolyline
                        ?.takeIf { it.isNotBlank() }
                        ?.let { PolylineDecoder.decode(it) }
                        ?: buildInterpolatedRoadPath(originLat, originLng, destLat, destLng)
                    val summary = route.description?.takeIf { it.isNotBlank() }
                        ?.let { "Via $it (Google Live Traffic)" }
                        ?: "Optimized via ${KigaliCatalog.findNeighborhood(destNeighborhood).primaryCorridor}"
                    return@withContext RouteOptimizationResult(
                        distanceKm = max(0.3, distKm),
                        etaMinutes = etaMin,
                        trafficLevel = traffic,
                        routeSummary = summary,
                        polylinePoints = points,
                        usedLiveGoogleApi = true
                    )
                }
            } catch (e: Exception) {
                Log.w("KigaliRouteOptimizer", "Google Routes API unavailable, using Kigali corridor traffic model: ${e.message}")
            }
        }
        computeLocalKigaliCorridorRoute(originLat, originLng, destLat, destLng, destNeighborhood)
    }

    suspend fun optimizeMultiStopDeliveries(
        originLat: Double,
        originLng: Double,
        activeDeliveries: List<DeliveryOrder>
    ): MultiStopRoutePlan = withContext(Dispatchers.IO) {
        if (activeDeliveries.isEmpty()) {
            return@withContext MultiStopRoutePlan(
                orderedStops = emptyList(),
                totalDistanceKm = 0.0,
                totalEtaMinutes = 0,
                trafficLevel = currentKigaliTrafficLevel(),
                routeSummary = "No active stops",
                polylinePoints = emptyList(),
                usedLiveGoogleApi = false
            )
        }

        if (activeDeliveries.size == 1) {
            val stop = activeDeliveries.first()
            val single = computeTrafficAwareRoute(
                originLat, originLng, stop.barLat, stop.barLng, stop.barNeighborhood
            )
            return@withContext MultiStopRoutePlan(
                orderedStops = listOf(stop),
                totalDistanceKm = single.distanceKm,
                totalEtaMinutes = single.etaMinutes,
                trafficLevel = single.trafficLevel,
                routeSummary = single.routeSummary,
                polylinePoints = single.polylinePoints,
                usedLiveGoogleApi = single.usedLiveGoogleApi
            )
        }

        // Nearest-neighbor baseline ordering for Kigali stops
        val remaining = activeDeliveries.toMutableList()
        val ordered = mutableListOf<DeliveryOrder>()
        var curLat = originLat
        var curLng = originLng
        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { KigaliCatalog.haversineKm(curLat, curLng, it.barLat, it.barLng) }!!
            ordered.add(next)
            remaining.remove(next)
            curLat = next.barLat
            curLng = next.barLng
        }

        // Attempt live Google Routes API waypoint optimization if key is configured
        if (hasConfiguredMapsKey() && ordered.size >= 2) {
            try {
                val destinationStop = ordered.last()
                val intermediates = ordered.dropLast(1).map {
                    RouteWaypoint(RouteLocation(RouteLatLng(it.barLat, it.barLng)))
                }
                val request = ComputeRoutesRequest(
                    origin = RouteWaypoint(RouteLocation(RouteLatLng(originLat, originLng))),
                    destination = RouteWaypoint(RouteLocation(RouteLatLng(destinationStop.barLat, destinationStop.barLng))),
                    intermediates = intermediates,
                    optimizeWaypointOrder = true
                )
                val response = api.computeRoutes(apiKey = BuildConfig.MAPS_API_KEY, body = request)
                val route = response.routes?.firstOrNull()
                if (route != null) {
                    val reorderedIntermediates = route.optimizedIntermediateWaypointIndex
                        ?.mapNotNull { idx -> ordered.dropLast(1).getOrNull(idx) }
                        ?.takeIf { it.size == ordered.size - 1 }
                        ?: ordered.dropLast(1)
                    val finalStops = reorderedIntermediates + destinationStop
                    val distKm = (((route.distanceMeters ?: 0) / 1000.0) * 10.0).roundToInt() / 10.0
                    val liveSeconds = parseDurationSeconds(route.duration)
                    val staticSeconds = parseDurationSeconds(route.staticDuration)
                    val ratio = if (staticSeconds > 0) liveSeconds.toDouble() / staticSeconds.toDouble() else 1.15
                    val traffic = when {
                        ratio >= 1.35 -> TrafficLevel.HEAVY
                        ratio >= 1.12 -> TrafficLevel.MODERATE
                        else -> TrafficLevel.LIGHT
                    }
                    val poly = route.polyline?.encodedPolyline
                        ?.takeIf { it.isNotBlank() }
                        ?.let { PolylineDecoder.decode(it) }
                        ?: buildMultiStopPolyline(originLat, originLng, finalStops)
                    return@withContext MultiStopRoutePlan(
                        orderedStops = finalStops,
                        totalDistanceKm = max(0.5, distKm),
                        totalEtaMinutes = max(4, (liveSeconds / 60.0).roundToInt()),
                        trafficLevel = traffic,
                        routeSummary = "Google Traffic-Optimal (${finalStops.size} Kigali stops)",
                        polylinePoints = poly,
                        usedLiveGoogleApi = true
                    )
                }
            } catch (e: Exception) {
                Log.w("KigaliRouteOptimizer", "Multi-stop Google API fallback: ${e.message}")
            }
        }

        // Local Kigali traffic-aware calculation
        var totalKm = 0.0
        var totalMin = 0
        var prevLat = originLat
        var prevLng = originLng
        val traffic = currentKigaliTrafficLevel()
        ordered.forEach { stop ->
            val leg = computeLocalKigaliCorridorRoute(prevLat, prevLng, stop.barLat, stop.barLng, stop.barNeighborhood)
            totalKm += leg.distanceKm
            totalMin += leg.etaMinutes + 4 // 4 min unload per stop
            prevLat = stop.barLat
            prevLng = stop.barLng
        }
        val roundedKm = (totalKm * 10.0).roundToInt() / 10.0
        MultiStopRoutePlan(
            orderedStops = ordered,
            totalDistanceKm = roundedKm,
            totalEtaMinutes = max(5, totalMin),
            trafficLevel = traffic,
            routeSummary = "Kigali Corridor Optimized: ${ordered.joinToString(" → ") { it.barNeighborhood }}",
            polylinePoints = buildMultiStopPolyline(originLat, originLng, ordered),
            usedLiveGoogleApi = false
        )
    }

    private fun computeLocalKigaliCorridorRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        destNeighborhood: String
    ): RouteOptimizationResult {
        val straightKm = KigaliCatalog.haversineKm(originLat, originLng, destLat, destLng)
        // Kigali hillside road winding factor ~1.34x straight line
        val roadKm = max(0.4, ((straightKm * 1.34) * 10.0).roundToInt() / 10.0)
        val traffic = currentKigaliTrafficLevel()
        val avgSpeedKmh = when (traffic) {
            TrafficLevel.LIGHT -> 34.0
            TrafficLevel.MODERATE -> 23.0
            TrafficLevel.HEAVY -> 15.0
        }
        val etaMin = max(3, ((roadKm / avgSpeedKmh) * 60.0).roundToInt())
        val corridor = KigaliCatalog.findNeighborhood(destNeighborhood).primaryCorridor
        return RouteOptimizationResult(
            distanceKm = roadKm,
            etaMinutes = etaMin,
            trafficLevel = traffic,
            routeSummary = "Via $corridor",
            polylinePoints = buildInterpolatedRoadPath(originLat, originLng, destLat, destLng),
            usedLiveGoogleApi = false
        )
    }

    private fun currentKigaliTrafficLevel(): TrafficLevel {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 7..9, in 17..19 -> TrafficLevel.HEAVY
            in 10..16, in 20..21 -> TrafficLevel.MODERATE
            else -> TrafficLevel.LIGHT
        }
    }

    private fun parseDurationSeconds(duration: String?): Int {
        if (duration.isNullOrBlank()) return 900
        return duration.removeSuffix("s").toDoubleOrNull()?.roundToInt() ?: 900
    }

    private fun buildInterpolatedRoadPath(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): List<LatLng> {
        val midLat = (lat1 + lat2) / 2.0 + 0.0025
        val midLng = (lng1 + lng2) / 2.0 - 0.0020
        return listOf(
            LatLng(lat1, lng1),
            LatLng((lat1 + midLat) / 2.0, (lng1 + midLng) / 2.0),
            LatLng(midLat, midLng),
            LatLng((midLat + lat2) / 2.0, (midLng + lng2) / 2.0),
            LatLng(lat2, lng2)
        )
    }

    private fun buildMultiStopPolyline(
        originLat: Double,
        originLng: Double,
        stops: List<DeliveryOrder>
    ): List<LatLng> {
        val all = mutableListOf<LatLng>()
        var curLat = originLat
        var curLng = originLng
        stops.forEach { stop ->
            val seg = buildInterpolatedRoadPath(curLat, curLng, stop.barLat, stop.barLng)
            if (all.isNotEmpty()) all.addAll(seg.drop(1)) else all.addAll(seg)
            curLat = stop.barLat
            curLng = stop.barLng
        }
        return all
    }
}
