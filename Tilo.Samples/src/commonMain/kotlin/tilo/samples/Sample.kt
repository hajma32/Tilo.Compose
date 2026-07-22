package tilo.samples

internal enum class SampleSection(
    val title: String,
) {
    Basics("MAP BASICS"),
    Interaction("INTERACTION"),
}

internal enum class Sample(
    val number: String,
    val title: String,
    val description: String,
    val section: SampleSection,
) {
    OpenStreetMap("01", "OpenStreetMap", "A minimal XYZ basemap", SampleSection.Basics),
    Geometries("02", "Geometries", "Points, lines and polygons", SampleSection.Basics),
    CustomStyles("03", "Custom styles", "Layer and feature styling", SampleSection.Basics),
    StyleLab("04", "Style lab", "Zoom, labels and casing", SampleSection.Basics),
    Callout("05", "Callout", "Selection with app-owned UI", SampleSection.Interaction),
    NonMercator("06", "Non-Mercator CRS", "ČÚZK ortofoto in EPSG:5514", SampleSection.Interaction),
    Drawing("07", "Drawing", "Point, line and polygon editing", SampleSection.Interaction),
}
