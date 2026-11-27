package com.example.model

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class BralirwaProduct(
    val sku: String,
    val name: String,
    val category: String,
    val packSize: String,
    val pricePerCrateRwf: Long,
    val abvLabel: String
)

data class KigaliNeighborhood(
    val name: String,
    val district: String,
    val lat: Double,
    val lng: Double,
    val primaryCorridor: String
)

data class BralirwaDepot(
    val id: String,
    val name: String,
    val neighborhood: String,
    val lat: Double,
    val lng: Double
)

object KigaliCatalog {
    val products: List<BralirwaProduct> = listOf(
        BralirwaProduct("PRI-72", "Primus 72cl", "Beers", "12 bottles / crate", 14500L, "5.0% ABV"),
        BralirwaProduct("PRI-CIT", "Primus Citron 50cl", "Beers", "20 bottles / crate", 18000L, "3.5% ABV"),
        BralirwaProduct("MUT-65", "Mützig 65cl", "Beers", "12 bottles / crate", 16800L, "5.5% ABV"),
        BralirwaProduct("MUT-LT", "Mützig Lite 33cl", "Beers", "24 bottles / crate", 22500L, "4.2% ABV"),
        BralirwaProduct("TBK-33", "Turbo King 33cl", "Beers", "24 bottles / crate", 20400L, "6.5% ABV"),
        BralirwaProduct("HNK-33", "Heineken 33cl", "Premium", "24 bottles / crate", 36000L, "5.0% ABV"),
        BralirwaProduct("AMS-33", "Amstel Malt 33cl", "Premium", "24 bottles / crate", 28800L, "5.0% ABV"),
        BralirwaProduct("LEG-33", "Legend Extra Stout", "Beers", "24 bottles / crate", 24000L, "7.5% ABV"),
        BralirwaProduct("COC-30", "Coca-Cola 30cl", "Soft Drinks", "24 bottles / crate", 13200L, "0.0%"),
        BralirwaProduct("FNT-OR", "Fanta Orange / Citron", "Soft Drinks", "24 bottles / crate", 13200L, "0.0%"),
        BralirwaProduct("SPR-30", "Sprite / Stoney 30cl", "Soft Drinks", "24 bottles / crate", 13200L, "0.0%"),
        BralirwaProduct("VIT-50", "Vital'O Water 50cl", "Soft Drinks", "24 bottles / pack", 8500L, "Still")
    )

    val neighborhoods: List<KigaliNeighborhood> = listOf(
        KigaliNeighborhood("Kimihurura", "Gasabo", -1.9536, 30.0928, "KG 7 Ave / KCC"),
        KigaliNeighborhood("Remera", "Gasabo", -1.9579, 30.1086, "KG 11 Ave / Kisimenti"),
        KigaliNeighborhood("Nyarugenge CBD", "Nyarugenge", -1.9499, 30.0588, "KN 3 Rd / City Center"),
        KigaliNeighborhood("Kimironko", "Gasabo", -1.9430, 30.1165, "KG 11 Ave / Market Rd"),
        KigaliNeighborhood("Kicukiro Centre", "Kicukiro", -1.9845, 30.1032, "KK 15 Rd / Sonatubes"),
        KigaliNeighborhood("Nyabugogo", "Nyarugenge", -1.9397, 30.0445, "KN 1 Rd / Nyabugogo"),
        KigaliNeighborhood("Kacyiru", "Gasabo", -1.9355, 30.0825, "KG 7 Ave / Boulevard"),
        KigaliNeighborhood("Gikondo", "Kicukiro", -1.9695, 30.0748, "KK 6 Ave / Industrial"),
        KigaliNeighborhood("Kiyovu", "Nyarugenge", -1.9540, 30.0642, "KN 14 Ave / Kiyovu"),
        KigaliNeighborhood("Kanombe", "Kicukiro", -1.9708, 30.1355, "KN 5 Rd / Airport Rd"),
        KigaliNeighborhood("Nyamirambo", "Nyarugenge", -1.9775, 30.0430, "KN 2 Ave / Biryogo"),
        KigaliNeighborhood("Gacuriro", "Gasabo", -1.9225, 30.0942, "KG 9 Ave / Vision City")
    )

    val depots: List<BralirwaDepot> = listOf(
        BralirwaDepot("depot_gikondo", "Gikondo Main Depot", "Gikondo", -1.9695, 30.0748),
        BralirwaDepot("depot_nyabugogo", "Nyabugogo West Hub", "Nyabugogo", -1.9397, 30.0445),
        BralirwaDepot("depot_remera", "Remera East Depot", "Remera", -1.9562, 30.1105),
        BralirwaDepot("depot_kicukiro", "Kicukiro South Hub", "Kicukiro", -1.9822, 30.0985),
        BralirwaDepot("depot_kacyiru", "Kacyiru North Hub", "Kacyiru", -1.9348, 30.0831)
    )

    fun findNeighborhood(name: String): KigaliNeighborhood =
        neighborhoods.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: neighborhoods.first()

    fun findNearestDepot(lat: Double, lng: Double): BralirwaDepot =
        depots.minByOrNull { haversineKm(lat, lng, it.lat, it.lng) } ?: depots.first()

    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return ((r * c) * 100.0).roundToInt() / 100.0
    }

    fun formatRwf(amount: Long): String {
        val formatter = NumberFormat.getNumberInstance(Locale.US)
        return "${formatter.format(amount)} RWF"
    }
}
