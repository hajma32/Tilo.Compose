package eu.tilo.compose.cuzk

internal enum class ZabagedLayer(
    val serviceId: Int,
    val keyPrefix: String,
    val minimumZoom: Double,
    val outFields: String,
    val isBasemap: Boolean = false,
    val supportsAreaFiltering: Boolean = false,
    val areaFilterMultiplier: Double = 1.0,
) {
    ArableLand(
        serviceId = 138,
        keyPrefix = "arable-land",
        minimumZoom = 10.0,
        outFields = "OBJECTID",
        isBasemap = true,
        supportsAreaFiltering = true,
        areaFilterMultiplier = 1.25,
    ),
    Grassland(
        serviceId = 139,
        keyPrefix = "grassland",
        minimumZoom = 10.0,
        outFields = "OBJECTID",
        isBasemap = true,
        supportsAreaFiltering = true,
        areaFilterMultiplier = 1.25,
    ),
    OrchardsAndGardens(
        serviceId = 135,
        keyPrefix = "orchard-garden",
        minimumZoom = 13.0,
        outFields = "OBJECTID",
        isBasemap = true,
        supportsAreaFiltering = true,
    ),
    MaintainedGreenery(
        serviceId = 134,
        keyPrefix = "maintained-greenery",
        minimumZoom = 13.0,
        outFields = "OBJECTID",
        isBasemap = true,
        supportsAreaFiltering = true,
    ),
    Forest(
        serviceId = 142,
        keyPrefix = "forest",
        minimumZoom = 10.0,
        outFields = "OBJECTID",
        isBasemap = true,
        supportsAreaFiltering = true,
        areaFilterMultiplier = 1.25,
    ),
    CategorizedForest(
        serviceId = 144,
        keyPrefix = "categorized-forest",
        minimumZoom = 13.0,
        outFields = "OBJECTID",
        isBasemap = true,
        supportsAreaFiltering = true,
    ),
    WaterAreas(
        serviceId = 132,
        keyPrefix = "water-area",
        minimumZoom = 10.0,
        outFields = "OBJECTID",
        isBasemap = true,
        supportsAreaFiltering = true,
        areaFilterMultiplier = 0.25,
    ),
    SettlementAreas(
        serviceId = 115,
        keyPrefix = "settlement-area",
        minimumZoom = 10.0,
        outFields = "OBJECTID",
        isBasemap = true,
        supportsAreaFiltering = true,
        areaFilterMultiplier = 0.5,
    ),
    Watercourses(
        serviceId = 93,
        keyPrefix = "watercourse",
        minimumZoom = 10.0,
        outFields = "OBJECTID,jmeno",
        isBasemap = true,
    ),
    Buildings(
        serviceId = 99,
        keyPrefix = "building",
        minimumZoom = 14.0,
        outFields = "OBJECTID",
        isBasemap = true,
        supportsAreaFiltering = true,
    ),
    AdministrativeBoundaries(
        serviceId = 1,
        keyPrefix = "boundary",
        minimumZoom = 10.0,
        outFields = "OBJECTID,vyzn_zsh_k,vyzn_zsh_p",
    ),
    Municipalities(
        serviceId = 2,
        keyPrefix = "municipality",
        minimumZoom = 10.0,
        outFields = "OBJECTID,nazevlau2,pocobyv,typobce,typdbobc_p",
    ),
    Roads(
        serviceId = 79,
        keyPrefix = "road",
        minimumZoom = 10.0,
        outFields = "OBJECTID,silnice,jmeno,typsil_k,typsil_p",
    ),
    Streets(
        serviceId = 84,
        keyPrefix = "street",
        minimumZoom = 14.0,
        outFields = "OBJECTID,nazev,typulice_p",
    ),
}

internal enum class ZabagedWatercourseDetail(val whereClause: String) {
    Overview(MajorRiverNames.joinToString(prefix = "jmeno IN ('", separator = "','", postfix = "')")),
    Network("jmeno IS NOT NULL"),
    Detailed("1=1"),
    ;

    companion object {
        fun forZoom(zoom: Double): ZabagedWatercourseDetail =
            when {
                zoom >= 13.0 -> Detailed
                zoom >= 12.0 -> Network
                else -> Overview
            }
    }
}

internal val MajorRiverNames = setOf(
    "Berounka",
    "Dyje",
    "Jizera",
    "Labe",
    "Lužnice",
    "Morava",
    "Odra",
    "Ohře",
    "Opava",
    "Orlice",
    "Otava",
    "Sázava",
    "Svitava",
    "Svratka",
    "Vltava",
)

internal enum class ZabagedGeometryProfile(
    val toleranceInPixels: Double,
    val minimumAreaInPixels: Double,
) {
    Overview(toleranceInPixels = 4.0, minimumAreaInPixels = 1_024.0),
    Network(toleranceInPixels = 1.5, minimumAreaInPixels = 96.0),
    Detailed(toleranceInPixels = 0.75, minimumAreaInPixels = 0.5),
    ;

    companion object {
        fun forZoom(zoom: Double): ZabagedGeometryProfile =
            when {
                zoom >= 14.0 -> Detailed
                zoom >= 13.0 -> Network
                else -> Overview
            }
    }
}

internal enum class ZabagedWaterLabelDetail {
    MajorRivers,
    AllNamedWatercourses,
    ;

    companion object {
        fun forZoom(zoom: Double): ZabagedWaterLabelDetail =
            if (zoom >= 13.0) AllNamedWatercourses else MajorRivers
    }
}

internal enum class ZabagedRoadDetail(
    val whereClause: String,
    val widthScale: Double,
) {
    Overview(
        whereClause = "(" +
            "typsil_p LIKE 'dálnice%' OR " +
            "typsil_p LIKE 'silnice I. třídy%' OR " +
            "typsil_p LIKE 'silnice pro motorová vozidla%'" +
        ") AND typsil_p NOT LIKE '%větev%'",
        widthScale = 0.45,
    ),
    Network(
        whereClause = "typsil_p NOT LIKE '%větev%'",
        widthScale = 0.65,
    ),
    Detailed(
        whereClause = "1=1",
        widthScale = 1.0,
    ),
    ;

    companion object {
        fun forZoom(zoom: Double): ZabagedRoadDetail =
            when {
                zoom >= 13.5 -> Detailed
                zoom >= 11.5 -> Network
                else -> Overview
            }
    }
}

internal enum class ZabagedBoundaryDetail(
    val whereClause: String,
) {
    Regions("vyzn_zsh_p LIKE '%Kraj%'"),
    Districts("vyzn_zsh_p LIKE '%Okres%'"),
    Municipalities("vyzn_zsh_p LIKE '%Obec%'"),
    ;

    companion object {
        fun forZoom(zoom: Double): ZabagedBoundaryDetail =
            when {
                zoom >= 12.5 -> Municipalities
                zoom >= 10.5 -> Districts
                else -> Regions
            }
    }
}

internal enum class ZabagedMunicipalityDetail(
    val whereClause: String,
) {
    MajorCities("typdbobc_p = 'Hlavní' AND pocobyv >= 50000"),
    Towns("typdbobc_p = 'Hlavní' AND pocobyv >= 5000"),
    AllMunicipalities("typdbobc_p = 'Hlavní'"),
    ;

    companion object {
        fun forZoom(zoom: Double): ZabagedMunicipalityDetail =
            when {
                zoom >= 11.5 -> AllMunicipalities
                zoom >= 10.0 -> Towns
                else -> MajorCities
            }
    }
}

internal data class ZabagedFeatureData(
    val layer: ZabagedLayer,
    val objectId: String,
    val kind: String?,
)
