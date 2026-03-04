package eu.tilo.compose.render

sealed interface SceneOp {
    data class Add(val command: RenderCommand) : SceneOp
    data class Remove(val id: String) : SceneOp
    data class Update(val command: RenderCommand) : SceneOp
}

object SceneDiff {
    fun diff(previous: List<RenderCommand>, next: List<RenderCommand>): List<SceneOp> {
        val prevById = previous.associateBy { it.id }
        val nextById = next.associateBy { it.id }

        val ops = mutableListOf<SceneOp>()

        prevById.keys.filter { it !in nextById.keys }.forEach { id ->
            ops += SceneOp.Remove(id)
        }

        nextById.forEach { (id, cmd) ->
            val prev = prevById[id]
            if (prev == null) {
                ops += SceneOp.Add(cmd)
            } else if (prev != cmd) {
                ops += SceneOp.Update(cmd)
            }
        }

        return ops
    }

    fun diffMaps(previous: Map<String, RenderCommand>, next: Map<String, RenderCommand>): List<SceneOp> {
        val ops = mutableListOf<SceneOp>()
        previous.keys.filter { it !in next.keys }.forEach { id ->
            ops += SceneOp.Remove(id)
        }
        next.forEach { (id, cmd) ->
            val prev = previous[id]
            if (prev == null) {
                ops += SceneOp.Add(cmd)
            } else if (prev != cmd) {
                ops += SceneOp.Update(cmd)
            }
        }
        return ops
    }

    fun apply(previous: Map<String, RenderCommand>, ops: List<SceneOp>): Map<String, RenderCommand> {
        val out = previous.toMutableMap()
        ops.forEach { op ->
            when (op) {
                is SceneOp.Add -> out[op.command.id] = op.command
                is SceneOp.Update -> out[op.command.id] = op.command
                is SceneOp.Remove -> out.remove(op.id)
            }
        }
        return out
    }
}

