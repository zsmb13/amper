@file:OptIn(ExperimentalPathApi::class)

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliIntegrationTest {
    @Test
    @Ignore("not ready yet")
    fun compileHelloWorldProject(@TempDir tempDir: Path) {
        Assumptions.assumeTrue(OS.current() != OS.WINDOWS, "Not ready for Windows yet, TODO")
        val moduleRoot = Path.of(System.getProperty("user.dir"))

        val cli = moduleRoot.resolve("../cli/scripts/amper")
        assertTrue { cli.isExecutable() }

        val projectPath = moduleRoot.resolve("testData/projects/cli-jvm-hello-world")
        assertTrue { projectPath.isDirectory() }

        projectPath.copyToRecursively(tempDir, followLinks = false, overwrite = false)

        val process = ProcessBuilder()
            .directory(tempDir.toFile())
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectErrorStream(true)
            .command(cli.pathString, "--from-sources")
            .start()

        process.outputStream.close()
        val processOutput = process.inputStream.readAllBytes().decodeToString()
        val rc = process.waitFor()

        assertEquals(0, rc, "Exit code must be 0. Process output:\n$processOutput")

        val expectedFile = tempDir.resolve("build/distributions/cli-jvm-hello-world-SNAPSHOT-1.0.zip")
        assertTrue("Expected output file to exist: $expectedFile. Process output:\n$processOutput") {
            expectedFile.exists()
        }
    }
}