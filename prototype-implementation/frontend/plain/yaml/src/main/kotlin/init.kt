package org.jetbrains.deft.proto.frontend

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.*


internal fun withBuildFile(buildFile: Path, func: BuildFileAware.() -> PotatoModule): PotatoModule =
    with(object : BuildFileAware {
        override val buildFile: Path = buildFile
    }) {
        this.func()
    }

class YamlModelInit : ModelInit {
    override fun getModel(root: Path): Model {
        if (!root.exists()) {
            throw RuntimeException("Can't find ${root.absolutePathString()}")
        }
        val modules = Files.walk(root)
            .filter {
                it.name == "Pot.yaml" && ignorePaths.none { ignorePath -> it.startsWith(ignorePath) }
            }
            .map { withBuildFile(it.toAbsolutePath()) { parseModule(it.readText()) } }
            .collect(Collectors.toList())

        return object : Model {
            override val modules: List<PotatoModule> = modules
        }
    }
}