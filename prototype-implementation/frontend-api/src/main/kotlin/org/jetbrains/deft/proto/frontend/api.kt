package org.jetbrains.deft.proto.frontend


/**
 * Dependency between fragments.
 * Can differ by type (refines dependency, test on sources dependency, etc.).
 */
interface FragmentDependency {
    val target: Fragment

    // TODO Think about type: enum?
    val type: String
}

/**
 * Some part of module, that supports "single resolve context" invariant for
 * every source and resource file, that is included.
 */
interface Fragment {
    val dependsOn: FragmentDependency?
}

/**
 * Some resulting artifact that is built from several fragments.
 */
interface Artifact {
    // TODO Think about type: enum?
    val type: String
    val fragments: List<Fragment>

    // TODO Think about type: enum?
    val platforms: List<String>
}

/**
 * Just an aggregator for fragments and artifacts.
 */
interface PotatoModule {
    val fragments: List<Fragment>
    val artifacts: List<Artifact>
}

interface Model {
    val modules: List<PotatoModule>
    // TODO add get by path
}