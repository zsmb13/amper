package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.helper.assertThrowsWithErrorMessage
import org.jetbrains.deft.proto.frontend.helper.testParse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotNull

internal class ProductsTest {

    @TempDir
    lateinit var tempDir: Path

    private val buildFile get() = tempDir.resolve("build.yaml")

    @Test
    fun `product with unsupported type `() {
        with(buildFile) {
            val e = assertThrows<Exception>("11") {
                testParse("product-with-unsupported-type")
            }
            val message = e.message
            assertNotNull(message)
            assertTrue(
                message.startsWith("unsupported product type 'foo/bar', supported types:"),
                message)
            assertTrue(
                message.contains("jvm/app"),
                message)

        }
    }


    @Test
    fun `product lib `() {
        with(buildFile) {
            testParse("product-lib")
        }
    }

    @Test
    fun `product lib without platforms full`() {
        with(buildFile) {
            assertThrowsWithErrorMessage<Exception>("product:platforms: should not be empty for 'lib' product type") {
                testParse("product-lib-without-platforms-full")
            }
        }
    }

    @Test
    fun `product lib without platforms inline`() {
        with(buildFile) {
            assertThrowsWithErrorMessage<Exception>("product:platforms: should not be empty for 'lib' product type") {
                testParse("product-lib-without-platforms-inline")
            }
        }
    }

    @Test
    fun `product lib with empty platforms inline`() {
        with(buildFile) {
            assertThrowsWithErrorMessage<Exception>("product:platforms: should not be empty for 'lib' product type") {
                testParse("product-lib-with-empty-platforms")
            }
        }
    }

    @Test
    fun `product app inline`() {
        with(buildFile) {
            testParse("product-app-inline")
        }
    }

    @Test
    fun `product app with platforms`() {
        with(buildFile) {
            testParse("product-app-with-platforms")
        }
    }

    @Test
    fun `product app without platforms`() {
        with(buildFile) {
            testParse("product-app-without-platforms")
        }
    }

    @Test
    fun `product app multiple platforms`() {
        with(buildFile) {
            testParse("product-app-with-multiple-platforms")
        }
    }

    @Test
    fun `product app with non default platforms`() {
        with(buildFile) {
            testParse("product-app-with-non-default-platforms")
        }
    }

    @Test
    fun `product with incompatible platforms`() {
        with(buildFile) {
            assertThrowsWithErrorMessage<Exception>("product type 'jvm/app' doesn't support 'iosArm64' platform") {
                testParse("product-with-incompatible-platforms")
            }
        }
    }

    @Test
    fun `product with single unsupported platform`() {
        with(buildFile) {
            assertThrowsWithErrorMessage<Exception>("product type 'lib' doesn't support 'foo' platform") {
                testParse("product-with-single-unsupported-platform")
            }
        }
    }

    @Test
    fun `product with multiple unsupported platforms`() {
        with(buildFile) {
            assertThrowsWithErrorMessage<Exception>("product type 'lib' doesn't support 'foo', 'bar' platforms") {
                testParse("product-with-multiple-unsupported-platforms")
            }
        }
    }

}