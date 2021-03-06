package net.corda.plugins

import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

import java.nio.file.StandardCopyOption.REPLACE_EXISTING

private val classLoader: ClassLoader = object {}.javaClass.classLoader

@Throws(IOException::class)
fun installResource(folder: TemporaryFolder, resourceName: String): Long {
    val buildFile = folder.newFile(resourceName.substring(1 + resourceName.lastIndexOf('/')))
    return copyResourceTo(resourceName, buildFile)
}

@Throws(IOException::class)
fun copyResourceTo(resourceName: String, target: Path): Long {
    classLoader.getResourceAsStream(resourceName).use { input -> return Files.copy(input, target, REPLACE_EXISTING) }
}

@Throws(IOException::class)
fun copyResourceTo(resourceName: String, target: File): Long {
    return copyResourceTo(resourceName, target.toPath())
}