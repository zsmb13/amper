/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.builders.DocBuilder.Companion.buildDoc
import org.jetbrains.amper.frontend.schema.Module
import java.io.StringWriter
import java.nio.file.Path
import kotlin.test.Test

class BuildDocTest : FileExpectTest(".expected", forceInput = false) {

    override fun getActualContent(input: Path): String =
        StringWriter().apply { buildDoc(Module::class, this) }.toString()

    @Test
    fun `build doc test`() = test("doc-test")

}