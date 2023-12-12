/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.Platform.Companion.leafChildren
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.pretty
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schema.Module


/**
 * A seed to create new fragment.
 */
data class FragmentSeed(
    val platforms: Set<Platform>,
    val aliases: Set<String>? = null,
    val rootPlatforms: Set<Platform>? = null,
    var dependency: FragmentSeed? = null,
) {
    internal val modifiersAsStrings: Set<String> = buildList {
        addAll(aliases.orEmpty())
        addAll(rootPlatforms.orEmpty().map { it.pretty })
    }.toSet()
}

/**
 * Return combinations of platforms and aliases
 */
// TODO Maybe more comprehensive return value to trace things.
fun Module.buildFragmentSeeds(): Collection<FragmentSeed> {

    // TODO Check invariant - there should be no aliases with same platforms.

    val productPlatforms = product.value.platforms.value
    val usedAliases = aliases.value ?: emptyMap()
    val aliasPlatforms = usedAliases.values.flatten()

    val combinedLeafPlatforms = (productPlatforms + aliasPlatforms)
        .flatMap { it.leafChildren }
        .toSet()

    // Collect all nodes from natural hierarchy that match collected platforms.
    val applicableNaturalHierarchy = Platform.naturalHierarchy.entries
        .filter { (_, v) -> combinedLeafPlatforms.containsAll(v) }
        .associate { it.key to it.value }

    // Create a fragment seed from modifiers like "alias+ios".
    fun Modifiers.convertToSeed(): FragmentSeed {
        val (areAliases, nonAliases) = partition { usedAliases.contains(it.value) }
        val (arePlatforms, nonPlatforms) = nonAliases.partition { Platform.contains(it.value) }
        val declaredPlatforms = arePlatforms.map { Platform[it.value]!! }.toSet()
        val usedPlatforms = declaredPlatforms.flatMap { it.leafChildren }.toSet() +
                areAliases.flatMap { usedAliases[it.value] ?: emptyList() }
        // TODO Report nonPlatforms
        return FragmentSeed(
            aliases = areAliases.map { it.value }.toSet(),
            rootPlatforms = declaredPlatforms,
            platforms = usedPlatforms,
        )
    }

    // TODO Add modifiers from file system. How?
    val allUsedModifiers = settings.modifiers +
            `test-settings`.modifiers +
            dependencies.modifiers +
            `test-dependencies`.modifiers

    // We will certainly create fragments for these.
    val modifiersSeeds = allUsedModifiers.map { it.convertToSeed() }
    val productPlatformSeeds = productPlatforms.map { FragmentSeed(it.leafChildren, rootPlatforms = setOf(it)) }
    val aliasesSeeds = usedAliases.entries
        .map { (key, value) -> FragmentSeed(value.flatMap { it.leafChildren }.toSet(), aliases = setOf(key)) }
    val naturalHierarchySeeds = applicableNaturalHierarchy
        .map { (parent, children) -> FragmentSeed(children, rootPlatforms = setOf(parent)) }
    val leafSeeds = combinedLeafPlatforms
        .map { FragmentSeed(setOf(it), rootPlatforms = setOf(it)) }

    val requiredSeeds = buildSet {
        addAll(modifiersSeeds)
        addAll(productPlatformSeeds)
        addAll(aliasesSeeds)
        addAll(naturalHierarchySeeds)
        addAll(leafSeeds)
        // And add common fragment always. // TODO Check if we need common fragment always.
        add(FragmentSeed(combinedLeafPlatforms, rootPlatforms = setOf(Platform.COMMON)))
    }

    // Set up dependencies following platform hierarchy.
    requiredSeeds.forEach { fragmentSeed ->
        fragmentSeed.dependency = requiredSeeds
            .filter { it.platforms.containsAll(fragmentSeed.platforms) }
            // TODO Handle case, when alias is the same as natural hierarchy.
            .minByOrNull { it.platforms.size }
    }

    return requiredSeeds
}

// Convenient function to extract modifiers.
private val ValueBase<out Map<Modifiers, *>>.modifiers get() = value.keys