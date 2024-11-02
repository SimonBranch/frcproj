package com.sigxcpu.frcproj

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.*

var lineChangeCount = 0

enum class Blueprint(val codeDirectory: String, val testsDirectory: String) {
    Template("templates", "templates_test"),
    Example("examples", "examples_test")
}

fun main() {
    createProject(
        resourcesPath = Path.of("/home/simon/ext/vscode-wpilib/vscode-wpilib/resources"),
        destinationPath = Path.of("/tmp/test"),
        name = "romicommandbased",
        blueprint = Blueprint.Template,
        gradleBase = "javaromi",
        extraVendordeps = arrayOf("romi"),
        hasUnitTests = false
    )
    println("Patched ${lineChangeCount} of package declarations.")
}

fun createProject(resourcesPath: Path, destinationPath: Path,
                  name: String,
                  blueprint: Blueprint,
                  gradleBase: String,
                  extraVendordeps: Array<String>,
                  hasUnitTests: Boolean) {

    // See vscode-wpilib/src/shared/exampletemplateapi.ts
    // vscode-wpilib/src/shared/generator.ts
    // vscode-wpilib/src/shared/templates.ts

    fun source(first: String, vararg rest: String) = resourcesPath.resolve(
        Path.of(first, *rest)
    )
    fun destination(first: String, vararg rest: String) = destinationPath.resolve(
        Path.of(first, *rest)
    )

    copyCodeTree(
        source("java", "src", blueprint.codeDirectory, name),
        destination("src", "main", "java", "frc", "robot")
    )

    if (hasUnitTests) {
        copyCodeTree(
            source("java", "src", blueprint.testsDirectory, name),
            destination("src", "test", "java", "frc", "robot")
        )
    }

    copyGradleTree(
        source("gradle", gradleBase),
        destinationPath
    )

    copyGradleTree(
        source("gradle", "shared"),
        destinationPath
    )

    setExecutableBit(destination("gradlew"))

    patchGradleBuild(
        source("gradle", "version.txt").readText().trim(),
        destination("build.gradle")
    )

    createDeployDirectory(destination("src", "main", "deploy"))

    copyVendordeps(
        source("vendordeps"),
        destination("vendordeps"),
        depFiles = listOf("WPILibNewCommands.json")
                + extraVendordeps.map(vendordepNamesToFiles::getValue)
    )

    setTeamNumber(destination(".wpilib", "wpilib_preferences.json"), 1778)
}

fun copyCodeTree(sourceDir: Path, destDir: Path) {
    Files.walk(sourceDir).forEach { sourcePath ->
        val destinationPath = destDir.resolve(sourceDir.relativize(sourcePath))
        if (sourcePath.isDirectory()) {
            destinationPath.createDirectories()
        } else if (sourcePath.isRegularFile()) {
            if (sourcePath.name.endsWith(".gradle") || sourcePath.name.endsWith(".java")) {
                copyAndProcessCodeFile(sourcePath, destinationPath)
            } else {
                Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

val packageRegex = Regex("""edu\.wpi\.first\.wpilibj\.(?:examples|templates)\.[^.;]+""")
val packageReplacement = "frc.robot"

fun copyAndProcessCodeFile(sourcePath: Path, destinationPath: Path) {
    sourcePath.toFile().bufferedReader().use { reader ->
        destinationPath.toFile().bufferedWriter().use { writer ->
            reader.lineSequence().forEach { line ->
                // Replace edu.wpi.first... package declarations with the default
                // frc.robot ones.
                val processedLine = line.replace(packageRegex, packageReplacement)
                if (processedLine != line)
                    lineChangeCount++
                writer.write(processedLine)
                writer.newLine()
            }
        }
    }
}

fun copyGradleTree(sourceDir: Path, destinationDir: Path) {
    // Java's Files.walk() doesn't have the ability to prune subdirectories, so
    // we use Kotlin's File.walk() instead.  Note that it creates File objects
    // instead of Paths.
    sourceDir.toFile().walk().onEnter { file ->
        // Skip over "bin" and ".project" files.  Technically, in the original code,
        // "bin" is only skipped at the top level.  But no WPILib template should
        // have a nested "bin" folder inside a gradle folder that they expect to
        // be copied over.  Also, I'm not sure whether the ".project" matching
        // (which uses .indexOf() on the full path) was intended to match files like
        // "choreo.project", but I'm reproducing their behavior.
        file.name != "bin" && !file.name.contains(".project")
    }.forEach { sourceFile ->
        val sourcePath = sourceFile.toPath()
        val destinationPath = destinationDir.resolve(sourceDir.relativize(sourcePath))
        if (sourcePath.isDirectory()) {
            destinationPath.createDirectories()
        } else if (sourcePath.isRegularFile()) {
            Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

fun setExecutableBit(path: Path) {
    val permissions = Files.getPosixFilePermissions(path).toMutableSet()
    permissions.addAll(PosixFilePermissions.fromString("--x--x--x"))
    Files.setPosixFilePermissions(path, permissions)
}

val mainClassRegex = "###ROBOTCLASSREPLACE###"
val mainClassReplacement = "frc.robot.Main"
val gradleVersionRegex = "###GRADLERIOREPLACE###"

fun patchGradleBuild(gradleVersion: String, gradleBuild: Path) {
    val text = gradleBuild.toFile().readText()
    val patchedText = text
        .replace(mainClassRegex, mainClassReplacement)
        .replace(gradleVersionRegex, gradleVersion)
    gradleBuild.toFile().writeText(patchedText)
}

val exampleDeployText = """
Files placed in this directory will be deployed to the RoboRIO into the
'deploy' directory in the home folder. Use the 'Filesystem.getDeployDirectory' wpilib function
to get a proper path relative to the deploy directory.
""".trimIndent()

fun createDeployDirectory(destinationDir: Path) {
    destinationDir.createDirectories()
    destinationDir.resolve("example.txt").writeText(exampleDeployText)
}

val vendordepNamesToFiles = mapOf(
    "romi" to "RomiVendordep.json",
    "xrp" to "XRPVendordep.json",
)

fun copyVendordeps(sourceDir: Path, destinationDir: Path, depFiles: List<String>) {
    destinationDir.createDirectories()
    for (dep in depFiles) {
        Files.copy(sourceDir.resolve(dep), destinationDir.resolve(dep),
            StandardCopyOption.REPLACE_EXISTING)
    }
}

val teamNumberRegex = Regex("-1$", options = setOf(RegexOption.MULTILINE))

fun setTeamNumber(destinationPath: Path, teamNumber: Int) {
    val text = destinationPath.readText()
        .replace(teamNumberRegex, teamNumber.toString())
    destinationPath.writeText(text)
}