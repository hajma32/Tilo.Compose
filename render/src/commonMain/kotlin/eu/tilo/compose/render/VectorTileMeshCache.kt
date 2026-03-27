package eu.tilo.compose.render

import kotlin.math.pow
import tilo.compose.core.map.Map

class VectorTileMeshCache(
    private val maxTiles: Int = 256
) {
    private val cache = mutableMapOf<String, PreparedVectorTile>()
    private val accessOrder = mutableListOf<String>()

    internal fun prepare(commands: List<RenderCommand>, map: Map): PreparedVectorFrame {
        val grouped = commands.groupBy(::tileGroupingKey)
        val meshBatches = mutableListOf<VectorMeshBatch>()
        val fallbackPolygons = mutableListOf<RenderPolygon>()
        val points = mutableListOf<RenderPoint>()
        val labels = mutableListOf<RenderLabel>()

        commands.forEach { command ->
            when (command) {
                is RenderPoint -> points += command
                is RenderLabel -> labels += command
                is RenderLineString, is RenderPolygon -> Unit
            }
        }

        val worldUnitsPerPixel = map.projection.worldUnitsPerMapUnit /
            (2.0.pow(map.zoom) * map.viewport.pixelRatio)

        grouped.forEach { (tileKey, tileCommands) ->
            val lineCommands = tileCommands.filterIsInstance<RenderLineString>()
            val polygonCommands = tileCommands.filterIsInstance<RenderPolygon>()
            if (lineCommands.isEmpty() && polygonCommands.isEmpty()) return@forEach

            val drawableCommands = buildList<RenderCommand> {
                addAll(lineCommands)
                addAll(polygonCommands)
            }
            val signature = tileSignature(drawableCommands)
            val scaleKey = worldUnitsPerPixel.toRawBits().toString()
            val cacheKey = "$tileKey|$signature|$scaleKey"
            val prepared = cache[cacheKey]?.also { touch(cacheKey) }
                ?: buildPreparedTile(
                    tileKey = tileKey,
                    signature = signature,
                    polygonCommands = polygonCommands,
                    lineCommands = lineCommands,
                    worldUnitsPerPixel = worldUnitsPerPixel
                ).also {
                    cache[cacheKey] = it
                    touch(cacheKey)
                    trimToSize()
                }
            meshBatches += prepared.meshBatches
            fallbackPolygons += prepared.fallbackPolygons
        }

        return PreparedVectorFrame(
            meshBatches = meshBatches,
            fallbackPolygons = fallbackPolygons,
            points = points,
            labels = labels
        )
    }

    private fun buildPreparedTile(
        tileKey: String,
        signature: Int,
        polygonCommands: List<RenderPolygon>,
        lineCommands: List<RenderLineString>,
        worldUnitsPerPixel: Double
    ): PreparedVectorTile {
        val meshBatches = mutableListOf<VectorMeshBatch>()
        val fallbackPolygons = mutableListOf<RenderPolygon>()

        polygonCommands
            .groupBy { it.style }
            .values
            .forEach { group ->
                val polygons = VectorMeshBuilder.buildPolygonMesh(group)
                meshBatches += polygons.meshBatch
                fallbackPolygons += polygons.fallbackPolygons
            }

        lineCommands
            .groupBy { it.style }
            .values
            .forEach { group ->
                val widthWorld = (group.first().style.strokeWidth ?: 2.0) * worldUnitsPerPixel
                VectorMeshBuilder.buildLineMesh(group, widthWorldUnits = widthWorld)?.let(meshBatches::add)
            }

        return PreparedVectorTile(
            tileKey = tileKey,
            signature = signature,
            meshBatches = meshBatches,
            fallbackPolygons = fallbackPolygons
        )
    }

    private fun tileGroupingKey(command: RenderCommand): String {
        val head = command.id.substringBefore(':', missingDelimiterValue = "global")
        return if (head.count { it == '/' } == 2) head else "global"
    }

    private fun tileSignature(commands: List<RenderCommand>): Int {
        var result = 17
        commands.forEach { command ->
            result = 31 * result + command.id.hashCode()
            result = 31 * result + when (command) {
                is RenderPoint -> command.style.hashCode()
                is RenderLineString -> command.style.hashCode()
                is RenderPolygon -> command.style.hashCode()
                is RenderLabel -> command.style.hashCode()
            }
        }
        return result
    }

    private fun touch(key: String) {
        accessOrder.remove(key)
        accessOrder += key
    }

    private fun trimToSize() {
        while (accessOrder.size > maxTiles) {
            val eldestKey = accessOrder.removeAt(0)
            cache.remove(eldestKey)
        }
    }

    internal fun debugSnapshotKeys(): List<String> = accessOrder.toList()
}
