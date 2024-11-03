package com.sigxcpu.frcproj

import com.beust.klaxon.Klaxon
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

enum class ProjectType { Template, Example }
data class ProjectConfig(
    val name: String,
    val projectType: ProjectType,
    val gradleBase: String,
    val extraVendordeps: List<String>,
    val hasUnitTests: Boolean
)

data class ProjectJson(
    val name: String,
    val description: String,
    val tags: ArrayList<String>,
    val foldername: String,
    val gradlebase: String,
    val hasunittests: Boolean = false,
    val mainclass: String,
    val commandversion: Int = 2,
    val extravendordeps: ArrayList<String> = arrayListOf(),
)

fun main() {
    val resourcesPath = chooseResourcesPath()
    val destinationPath = chooseDestinationPath()
    val templateConfig = chooseTemplate(resourcesPath)
    val teamNumber = chooseTeamNumber()
    createProject(resourcesPath, destinationPath, templateConfig, teamNumber)
    println("project successfully created")
}

private fun tildeExpand(s: String): String {
    return if (s.startsWith("~"))
        s.replaceFirst("~", System.getProperty("user.home"))
    else s
}

private fun chooseResourcesPath(): Path {
    val defaultPaths = mutableListOf<Path>()
    val wpilib = Path.of(System.getProperty("user.home"), "wpilib")
    if (wpilib.isDirectory()) {
        for (subdir in Files.newDirectoryStream(wpilib, Path::isDirectory)) {
            val y: Int? = subdir.fileName.toString().toIntOrNull()
            if (y == null)
                continue
            val path = subdir.resolve(
                Path.of("utility", "resources", "app", "resources")
            )
            if (path.exists())
                defaultPaths.add(path)
        }
    }
    fun pathLooksValid(p: Path): Boolean {
        if (!p.exists()) {
            println("that path doesn't exist!")
            return false
        }
        if (!p.isDirectory()) {
            println("that path isn't a directory")
            return false
        }
        if (!p.resolve(Path.of("gradle", "version.txt")).exists()) {
            println("that doesn't look valid (no gradle/version.txt)")
            return false
        }
        return true
    }
    if (defaultPaths.size > 0) {
        println("the following resource paths were detected:")
        for (idx in defaultPaths.indices)
            println("  [${idx+1}] ${defaultPaths[idx]}")
        while (true) {
            print("what path would you like to use? (enter one of the numbers above or your own path) ")
            val response = tildeExpand(readln())
            var num = response.toIntOrNull()
            if (response == "") num = 1
            if (num != null) {
                if (num - 1 in defaultPaths.indices)
                    return defaultPaths[num - 1]
                println("number out of range, try again")
                continue
            }
            val path = Path.of(response)
            if (pathLooksValid(path))
                return path
        }
    } else {
        while (true) {
            print("what path would you like to use? ")
            val path = Path.of(tildeExpand(readln()))
            if (pathLooksValid(path))
                return path
        }
    }
}

private fun chooseTemplate(resourcesPath: Path): ProjectConfig {
    val jsonText = resourcesPath.resolve(
        Path.of("java", "src", "templates", "templates.json")
    ).readText()
    val jsons = Klaxon().parseArray<ProjectJson>(jsonText)!!
        .sortedBy { it.foldername }
    println("the following templates are available:")
    for (j in jsons) {
        println("  [${j.foldername}]: ${j.description}")
    }
    lateinit var json: ProjectJson
    while (true) {
        try {
            print("which template would you like to use? ")
            val template = readln()
            json = jsons.first { it.foldername.startsWith(template) }
            if (json.foldername != template)
                println("using template ${json.foldername}")
            break
        } catch (e: NoSuchElementException) {
            println("invalid name, try again")
        }
    }
    // Expectations for the template files; these are true in the current
    // wpilib repository, but there's code to handle other situations in
    // VSCode that aren't implemented here.
    require(json.mainclass == "Main" && json.commandversion == 2)
    return ProjectConfig(
        name = json.foldername,
        projectType = ProjectType.Template,
        gradleBase = json.gradlebase,
        extraVendordeps = json.extravendordeps,
        hasUnitTests = json.hasunittests
    )
}

private fun chooseDestinationPath(): Path {
    while (true) {
        print("which path would you like to create the project in? ")
        val path = Path.of(tildeExpand(readln()))
        if (!path.exists()) {
            path.createDirectory()
            return path
        }
        if (!path.isDirectory()) {
            println("that path isn't a directory!")
            continue
        }
        if (Files.list(path).findAny().isPresent) {
            println("that path contains files already!")
            continue
        }
        return path
    }
}

private fun chooseTeamNumber(): Int {
    while (true) {
        try {
            print("what is your team number? ")
            return readln().toInt()
        } catch (e: NumberFormatException) {
            println("invalid number")
        }
    }
}
