package net.corda.gradle.jarfilter

import org.assertj.core.api.Assertions.*
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.*
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.*
import java.util.Calendar.FEBRUARY
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class JarFilterTimestampTest {
    companion object {
        private val testProjectDir = TemporaryFolder()
        private val sourceJar = DummyJar(testProjectDir, JarFilterTimestampTest::class.java, "timestamps")

        private val CONSTANT_TIME: FileTime = FileTime.fromMillis(
            GregorianCalendar(1980, FEBRUARY, 1).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.timeInMillis
        )

        private lateinit var filteredJar: Path
        private lateinit var output: String

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(sourceJar)
            .around(createTestProject())

        private fun createTestProject() = TestRule { base, _ ->
            object : Statement() {
                override fun evaluate() {
                    testProjectDir.installResources("gradle.properties", "settings.gradle")
                    testProjectDir.newFile("build.gradle").writeText("""
                        |plugins {
                        |    id 'net.corda.plugins.jar-filter'
                        |}
                        |
                        |import net.corda.gradle.jarfilter.JarFilterTask
                        |task jarFilter(type: JarFilterTask) {
                        |    jars file("${sourceJar.path.toUri()}")
                        |    preserveTimestamps = false
                        |}
                        |""".trimMargin())
                    val result = GradleRunner.create()
                        .withProjectDir(testProjectDir.root)
                        .withArguments(getGradleArgsForTasks("jarFilter"))
                        .withPluginClasspath()
                        .build()
                    output = result.output
                    println(output)

                    val jarFilter = result.task(":jarFilter")
                        ?: throw AssertionError("No outcome for jarFilter task")
                    assertEquals(SUCCESS, jarFilter.outcome)

                    filteredJar = testProjectDir.pathOf("build", "filtered-libs", "timestamps-filtered.jar")
                    assertThat(filteredJar).isRegularFile()

                    base.evaluate()
                }
            }
        }

        private val ZipEntry.methodName: String get() = if (method == ZipEntry.STORED) "Stored" else "Deflated"
    }

    @Test
    fun fileTimestampsAreRemoved() {
        var directoryCount = 0
        var classCount = 0
        var otherCount = 0

        ZipFile(filteredJar.toFile()).use { jar ->
            for (entry in jar.entries()) {
                println("Entry: ${entry.name}")
                println("- ${entry.methodName} (${entry.size} size / ${entry.compressedSize} compressed) bytes")
                assertThat(entry.lastModifiedTime).isEqualTo(CONSTANT_TIME)
                assertThat(entry.lastAccessTime).isNull()
                assertThat(entry.creationTime).isNull()

                when {
                    entry.isDirectory -> ++directoryCount
                    entry.name.endsWith(".class") -> ++classCount
                    else -> ++otherCount
                }
            }
        }

        assertThat(directoryCount).isGreaterThan(0)
        assertThat(classCount).isGreaterThan(0)
        assertThat(otherCount).isGreaterThan(0)
    }
}