package com.sigxcpu.frcproj

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.*

fun createProject(resourcesPath: Path, destinationPath: Path,
                  config: ProjectConfig,
                  teamNumber: Int) {

    // See vscode-wpilib/src/shared/exampletemplateapi.ts
    // vscode-wpilib/src/shared/generator.ts
    // vscode-wpilib/src/shared/templates.ts

    // Helpers for constructing Paths relative to the resources directory
    // and the destination directory.
    fun resource(first: String, vararg rest: String) =
        resourcesPath.resolve(Path.of(first, *rest))
    fun destination(first: String, vararg rest: String) =
        destinationPath.resolve(Path.of(first, *rest))

    copyCodeTree(
        resource("java", "src", when (config.projectType) {
            ProjectType.Template -> "templates"
            ProjectType.Example -> "examples"
        }, config.name),
        destination("src", "main", "java", "frc", "robot")
    )

    if (config.hasUnitTests)
        copyCodeTree(
            resource("java", "src", when (config.projectType) {
                ProjectType.Template -> "templates_test"
                ProjectType.Example -> "examples_test"
            }, config.name),
            destination("src", "test", "java", "frc", "robot")
        )

    copyGradleTree(
        resource("gradle", config.gradleBase),
        destinationPath
    )

    copyGradleTree(
        resource("gradle", "shared"),
        destinationPath
    )

    setExecutableBit(destination("gradlew"))

    patchGradleBuild(
        resource("gradle", "version.txt"),
        destination("build.gradle")
    )

    createDeployDirectory(destination("src", "main", "deploy"))

    copyVendordeps(
        resource("vendordeps"),
        destination("vendordeps"),
        extraVendordeps = config.extraVendordeps
    )

    setTeamNumber(destination(".wpilib", "wpilib_preferences.json"),
        teamNumber)
}

private fun copyCodeTree(sourceDir: Path, destinationDir: Path) {
    Files.walk(sourceDir).forEach { sourcePath ->
        val destinationPath = destinationDir.resolve(sourceDir.relativize(sourcePath))
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

private val packageRegex = Regex("""edu\.wpi\.first\.wpilibj\.(?:examples|templates)\.[^.;]+""")
private val packageReplacement = "frc.robot"

private fun copyAndProcessCodeFile(sourcePath: Path, destinationPath: Path) {
    sourcePath.toFile().bufferedReader().use { reader ->
        destinationPath.toFile().bufferedWriter().use { writer ->
            reader.lineSequence().forEach { line ->
                // Replace edu.wpi.first... package declarations with the default
                // frc.robot ones.
                writer.write(line.replace(packageRegex, packageReplacement))
                writer.newLine()
            }
        }
    }
}

private fun copyGradleTree(sourceDir: Path, destinationDir: Path) {
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

private fun setExecutableBit(path: Path) {
    val permissions = Files.getPosixFilePermissions(path).toMutableSet()
    permissions.addAll(PosixFilePermissions.fromString("--x--x--x"))
    Files.setPosixFilePermissions(path, permissions)
}

private val mainClassRegex = "###ROBOTCLASSREPLACE###"
private val mainClassReplacement = "frc.robot.Main"
private val gradleVersionRegex = "###GRADLERIOREPLACE###"

private fun patchGradleBuild(gradleVersionPath: Path, gradleBuild: Path) {
    val version = gradleVersionPath.readText().trim()
    val text = gradleBuild.readText()
        .replace(mainClassRegex, mainClassReplacement)
        .replace(gradleVersionRegex, version)
    gradleBuild.writeText(text)
}

private fun createDeployDirectory(destinationDir: Path) {
    destinationDir.createDirectories()
    destinationDir.resolve("example.txt").writeText(
        """
        Files placed in this directory will be deployed to the RoboRIO into the
        'deploy' directory in the home folder. Use the 'Filesystem.getDeployDirectory' wpilib function
        to get a proper path relative to the deploy directory.
        """.trimIndent()
    )
}

private val vendordepNamesToFiles = mapOf(
    "romi" to "RomiVendordep.json",
    "xrp" to "XRPVendordep.json",
)

private fun copyVendordeps(sourceDir: Path, destinationDir: Path,
                           extraVendordeps: List<String>) {
    destinationDir.createDirectories()
    for (dep in listOf("WPILibNewCommands.json")
            + extraVendordeps.map(vendordepNamesToFiles::getValue))
    {
        Files.copy(sourceDir.resolve(dep), destinationDir.resolve(dep),
            StandardCopyOption.REPLACE_EXISTING)
    }
}

private val teamNumberRegex = Regex("-1$", options = setOf(RegexOption.MULTILINE))

private fun setTeamNumber(destinationPath: Path, teamNumber: Int) {
    val text = destinationPath.readText()
        .replace(teamNumberRegex, teamNumber.toString())
    destinationPath.writeText(text)
}