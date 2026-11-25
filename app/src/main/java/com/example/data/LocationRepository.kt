package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    val displayName: String
)

class LocationRepository(private val context: Context) {

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(): Result<ResolvedLocation> {
        if (!hasLocationPermission()) {
            return Result.failure(SecurityException("Location permission not granted"))
        }

        val location = fetchFromFusedClient()
            ?: fetchFromLocationManager()
            ?: return Result.failure(IllegalStateException("Unable to obtain GPS fix. Ensure location is turned on or select a city offline."))

        val label = reverseGeocode(location.latitude, location.longitude)
            ?: formatCoordinates(location.latitude, location.longitude)

        return Result.success(
            ResolvedLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                displayName = label
            )
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchFromFusedClient(): Location? = withTimeoutOrNull(8_000L) {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        // First try active fresh fix
        val fresh = suspendCancellableCoroutine<Location?> { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        }
        if (fresh != null) return@withTimeoutOrNull fresh

        // Fallback to last known location from Fused client
        suspendCancellableCoroutine<Location?> { cont ->
            fused.lastLocation
                .addOnSuccessListener { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchFromLocationManager(): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (provider in providers) {
            val candidate = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            if (candidate != null) {
                if (best == null || candidate.time > best.time) {
                    best = candidate
                }
            }
        }
        return best
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(4_000L) {
                suspendCancellableCoroutine<String?> { cont ->
                    runCatching {
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) {
                                    cont.resume(addresses.firstOrNull()?.toReadableName())
                                }
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }.onFailure {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } else {
            runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.toReadableName()
            }.getOrNull()
        }
    }

    suspend fun searchPlace(query: String): Result<SearchedPlace> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a city or place name"))
        }

        // 1. Check offline curated global cities first for instant exact/prefix match when offline or known
        val offlineExact = OfflineWorldCities.firstOrNull {
            it.name.equals(trimmed, ignoreCase = true) ||
                it.name.substringBefore(",").equals(trimmed, ignoreCase = true)
        }
        if (offlineExact != null) {
            return@withContext Result.success(offlineExact)
        }

        // 2. Try Android Geocoder forward search for any place worldwide
        val geocoded = geocodeByName(trimmed)
        if (geocoded != null) {
            return@withContext Result.success(geocoded)
        }

        // 3. Partial fallback match against offline global cities
        val offlinePartial = OfflineWorldCities.firstOrNull {
            it.name.contains(trimmed, ignoreCase = true)
        }
        if (offlinePartial != null) {
            return@withContext Result.success(offlinePartial)
        }

        Result.failure(IllegalStateException("Could not find \"$trimmed\". Try another city name."))
    }

    private suspend fun geocodeByName(query: String): SearchedPlace? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(5_000L) {
                suspendCancellableCoroutine<Address?> { cont ->
                    runCatching {
                        geocoder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }.onFailure {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } else {
            runCatching {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, 1)?.firstOrNull()
            }.getOrNull()
        }

        if (address == null || !address.hasLatitude() || !address.hasLongitude()) return null
        val name = address.toReadableName() ?: query.replaceFirstChar { it.uppercase() }
        val estimatedZone = estimateZoneFromLongitude(address.longitude)
        return SearchedPlace(
            name = name,
            latitude = address.latitude,
            longitude = address.longitude,
            zoneId = estimatedZone
        )
    }

    private fun estimateZoneFromLongitude(longitude: Double): java.time.ZoneId {
        val offsetHours = kotlin.math.round(longitude / 15.0).toInt().coerceIn(-12, 14)
        return java.time.ZoneOffset.ofHours(offsetHours)
    }

    private fun Address.toReadableName(): String? {
        val city = locality ?: subLocality ?: subAdminArea ?: featureName
        val region = adminArea ?: countryName ?: countryCode
        return when {
            !city.isNullOrBlank() && !region.isNullOrBlank() && !city.equals(region, ignoreCase = true) -> "$city, $region"
            !city.isNullOrBlank() -> city
            !region.isNullOrBlank() -> region
            else -> null
        }
    }

    companion object {
        fun formatCoordinates(lat: Double, lon: Double): String {
            val latDir = if (lat >= 0) "N" else "S"
            val lonDir = if (lon >= 0) "E" else "W"
            return String.format(
                Locale.US,
                "%.2f°%s, %.2f°%s",
                kotlin.math.abs(lat),
                latDir,
                kotlin.math.abs(lon),
                lonDir
            )
        }

        private val OfflineWorldCities: List<SearchedPlace> = listOf(
            SearchedPlace("San Francisco, CA", 37.7749, -122.4194, java.time.ZoneId.of("America/Los_Angeles")),
            SearchedPlace("Los Angeles, CA", 34.0522, -118.2437, java.time.ZoneId.of("America/Los_Angeles")),
            SearchedPlace("Seattle, WA", 47.6062, -122.3321, java.time.ZoneId.of("America/Los_Angeles")),
            SearchedPlace("New York, NY", 40.7128, -74.0060, java.time.ZoneId.of("America/New_York")),
            SearchedPlace("Chicago, IL", 41.8781, -87.6298, java.time.ZoneId.of("America/Chicago")),
            SearchedPlace("Miami, FL", 25.7617, -80.1918, java.time.ZoneId.of("America/New_York")),
            SearchedPlace("Toronto, Canada", 43.6532, -79.3832, java.time.ZoneId.of("America/Toronto")),
            SearchedPlace("Vancouver, Canada", 49.2827, -123.1207, java.time.ZoneId.of("America/Vancouver")),
            SearchedPlace("London, UK", 51.5074, -0.1278, java.time.ZoneId.of("Europe/London")),
            SearchedPlace("Paris, France", 48.8566, 2.3522, java.time.ZoneId.of("Europe/Paris")),
            SearchedPlace("Berlin, Germany", 52.5200, 13.4050, java.time.ZoneId.of("Europe/Berlin")),
            SearchedPlace("Madrid, Spain", 40.4168, -3.7038, java.time.ZoneId.of("Europe/Madrid")),
            SearchedPlace("Rome, Italy", 41.9028, 12.4964, java.time.ZoneId.of("Europe/Rome")),
            SearchedPlace("Zurich, Switzerland", 47.3769, 8.5417, java.time.ZoneId.of("Europe/Zurich")),
            SearchedPlace("Istanbul, Turkey", 41.0082, 28.9784, java.time.ZoneId.of("Europe/Istanbul")),
            SearchedPlace("Reykjavik, Iceland", 64.1466, -21.9426, java.time.ZoneId.of("Atlantic/Reykjavik")),
            SearchedPlace("Cairo, Egypt", 30.0444, 31.2357, java.time.ZoneId.of("Africa/Cairo")),
            SearchedPlace("Mecca, Saudi Arabia", 21.3891, 39.8579, java.time.ZoneId.of("Asia/Riyadh")),
            SearchedPlace("Medina, Saudi Arabia", 24.5247, 39.5692, java.time.ZoneId.of("Asia/Riyadh")),
            SearchedPlace("Riyadh, Saudi Arabia", 24.7136, 46.6753, java.time.ZoneId.of("Asia/Riyadh")),
            SearchedPlace("Dubai, UAE", 25.2048, 55.2708, java.time.ZoneId.of("Asia/Dubai")),
            SearchedPlace("Karachi, Pakistan", 24.8607, 67.0011, java.time.ZoneId.of("Asia/Karachi")),
            SearchedPlace("Lahore, Pakistan", 31.5204, 74.3587, java.time.ZoneId.of("Asia/Karachi")),
            SearchedPlace("Mumbai, India", 19.0760, 72.8777, java.time.ZoneId.of("Asia/Kolkata")),
            SearchedPlace("Delhi, India", 28.6139, 77.2090, java.time.ZoneId.of("Asia/Kolkata")),
            SearchedPlace("Bengaluru, India", 12.9716, 77.5946, java.time.ZoneId.of("Asia/Kolkata")),
            SearchedPlace("Kolkata, India", 22.5726, 88.3639, java.time.ZoneId.of("Asia/Kolkata")),
            SearchedPlace("Dhaka, Bangladesh", 23.8103, 90.4125, java.time.ZoneId.of("Asia/Dhaka")),
            SearchedPlace("Chittagong, Bangladesh", 22.3569, 91.7832, java.time.ZoneId.of("Asia/Dhaka")),
            SearchedPlace("Sylhet, Bangladesh", 24.8949, 91.8687, java.time.ZoneId.of("Asia/Dhaka")),
            SearchedPlace("Bangkok, Thailand", 13.7563, 100.5018, java.time.ZoneId.of("Asia/Bangkok")),
            SearchedPlace("Kuala Lumpur, Malaysia", 3.1390, 101.6869, java.time.ZoneId.of("Asia/Kuala_Lumpur")),
            SearchedPlace("Singapore", 1.3521, 103.8198, java.time.ZoneId.of("Asia/Singapore")),
            SearchedPlace("Jakarta, Indonesia", -6.2088, 106.8456, java.time.ZoneId.of("Asia/Jakarta")),
            SearchedPlace("Beijing, China", 39.9042, 116.4074, java.time.ZoneId.of("Asia/Shanghai")),
            SearchedPlace("Seoul, South Korea", 37.5665, 126.9780, java.time.ZoneId.of("Asia/Seoul")),
            SearchedPlace("Tokyo, Japan", 35.6762, 139.6503, java.time.ZoneId.of("Asia/Tokyo")),
            SearchedPlace("Sydney, Australia", -33.8688, 151.2093, java.time.ZoneId.of("Australia/Sydney")),
            SearchedPlace("Melbourne, Australia", -37.8136, 144.9631, java.time.ZoneId.of("Australia/Melbourne")),
            SearchedPlace("Auckland, New Zealand", -36.8509, 174.7645, java.time.ZoneId.of("Pacific/Auckland")),
            SearchedPlace("Sao Paulo, Brazil", -23.5505, -46.6333, java.time.ZoneId.of("America/Sao_Paulo")),
            SearchedPlace("Mexico City, Mexico", 19.4326, -99.1332, java.time.ZoneId.of("America/Mexico_City"))
        )
    }
}

data class SearchedPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: java.time.ZoneId
)
