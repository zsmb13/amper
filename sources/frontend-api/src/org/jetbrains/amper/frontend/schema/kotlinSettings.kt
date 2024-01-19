/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schema

import org.jetbrains.amper.frontend.EnumMap
import org.jetbrains.amper.frontend.SchemaEnum
import org.jetbrains.amper.frontend.api.SchemaNode


enum class KotlinVersion(override val schemaValue: String, override val outdated: Boolean = false) : SchemaEnum {
    Kotlin20("2.0"),
    Kotlin19("1.9"),
    Kotlin18("1.8"),
    Kotlin17("1.7"),
    Kotlin16("1.6"),
    Kotlin15("1.5"),
    Kotlin14("1.4"),
    Kotlin13("1.3"),
    Kotlin12("1.2"),
    Kotlin11("1.1"),
    Kotlin10("1.0");

    override fun toString(): String = schemaValue
    companion object Index : EnumMap<KotlinVersion, String>(KotlinVersion::values, KotlinVersion::schemaValue)
}

class KotlinSettings : SchemaNode() {
    var serialization by nullableValue<SerializationSettings>()
    var languageVersion by value(KotlinVersion.Kotlin19)
    var apiVersion by value { languageVersion }
    var allWarningsAsErrors by value(false)
    var freeCompilerArgs by nullableValue<List<String>>()
    var suppressWarnings by value(false)
    var verbose by value(false)
    var linkerOpts by nullableValue<List<String>>()
    var debug by value(false)
    var progressiveMode by value(false)
    // TODO Replace with enum
    var languageFeatures by nullableValue<List<String>>()
    // TODO Replace with enum
    var optIns by nullableValue<List<String>>()
}