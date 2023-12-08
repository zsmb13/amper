/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.api

/**
 * A class, that every enum, participating in
 * schema building should inherit.
 */
interface SchemaEnum {
    val schemaValue: String
}

/**
 * Class to collect all values registered within it.
 */
abstract class SchemaNode : Traceable() {
    private val allValues = mutableListOf<ValueBase<*>>()

    /**
     * Register a value.
     */
    fun <T : Any> value(
        default: T? = null,
        doc: String? = null
    ) = SchemaValue<T>().apply { this.default = default }.also { allValues.add(it) }

    /**
     * Register a nullable value.
     */
    fun <T : Any> nullableValue(
        default: T? = null,
        doc: String? = null
    ) = NullableSchemaValue<T>().apply { this.default = default }.also { allValues.add(it) }
}

/**
 * Abstract value that can have a default value.
 */
sealed class ValueBase<T> : Traceable() {
    var default: T? = null

    internal var myValue: T? = null
    val orNull: T? get() = myValue

    /**
     * Overwrite current value, if provided value is not null.
     */
    operator fun invoke(newValue: T?) {
        if (newValue != null) myValue = newValue
    }
}

/**
 * Required (non-null) schema value.
 */
class SchemaValue<T : Any> : ValueBase<T>() {
    val value: T
        get() = myValue ?: default ?: error("No value")

    /**
     * Overwrite current value, if provided value is not null.
     * Invoke [onNull] if it is.
     */
    operator fun invoke(newValue: T?, onNull: () -> Unit) {
        if (newValue == null) onNull() else myValue = newValue
    }
}

/**
 * Optional (nullable) schema value.
 */
class NullableSchemaValue<T : Any> : ValueBase<T>() {
    val value: T? get() = myValue ?: default
}