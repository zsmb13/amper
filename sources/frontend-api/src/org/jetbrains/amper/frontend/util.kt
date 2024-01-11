/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import java.nio.file.Path
import java.util.*
import kotlin.collections.AbstractMap
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.reflect.KMutableProperty0


private val prettyRegex = "_.".toRegex()
fun String.doCamelCase() = this.lowercase().replace(prettyRegex) { it.value.removePrefix("_").uppercase() }
fun String.doCapitalize() =
    this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

fun String.camelMerge(other: String) = when {
    isBlank() -> other
    other.isBlank() -> this
    else -> this + other.doCapitalize()
}

/**
 * Shortcut to find an instance of a class in some collection.
 */
inline fun <reified T : Any> Collection<Any>.findInstance() = filterIsInstance<T>().firstOrNull()

/**
 * Simple approach to do some action for fragment closure.
 */
fun Fragment.forClosure(
    includeSelf: Boolean = true,
    traverseTypes: Set<FragmentDependencyType> = setOf(FragmentDependencyType.REFINE),
    block: (Fragment) -> Unit
) {
    if (includeSelf) block(this)
    traverseDependencies(traverseTypes) { block(it.target) }
}

/**
 * Simple approach to traverse dependencies transitively.
 */
fun Fragment.traverseDependencies(
    traverseTypes: Set<FragmentDependencyType> = setOf(FragmentDependencyType.REFINE),
    block: (FragmentLink) -> Unit,
) {
    val traversed = mutableSetOf<FragmentLink>()
    val blockPlusCheck: (FragmentLink) -> Unit = {
        if (!traversed.add(it)) error("Cyclic dependency!")
        block(it)
    }
    doTraverseDependencies(traverseTypes, blockPlusCheck)
}

private fun Fragment.doTraverseDependencies(
    traverseTypes: Set<FragmentDependencyType>,
    block: (FragmentLink) -> Unit,
) {
    fragmentDependencies.forEach(block)
    fragmentDependencies
        .filter { it.type in traverseTypes }
        .map { it.target.doTraverseDependencies(traverseTypes, block) }
}

/**
 * Try to set a value to a bind property if the value is not null.
 */
infix fun <T : Any> KMutableProperty0<T>.trySet(value: T?) =
    value?.let { set(it) }


fun String.prepareToNamespace(): String = listOf("+", "-").fold(this) { acc: String, symbol: String ->
    acc.replace(symbol, "")
}

/**
 * Simple class to associate enum values by some string key.
 */
abstract class EnumMap<EnumT : Enum<EnumT>, KeyT>(
    valuesGetter: () -> Array<EnumT>,
    private val key: EnumT.() -> KeyT,
) : AbstractMap<KeyT, EnumT>() {
    val enumClass = valuesGetter().first()::class
    override val entries = valuesGetter().associateBy { it.key() }.entries
}

fun Path.isModuleYaml() = name == "module.yaml"

val Path.amperIgnoreIfAny: Path?
    get() = resolve(".amperignore").takeIf { it.exists() }