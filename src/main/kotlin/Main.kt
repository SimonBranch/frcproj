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
    val hasunittests: Boolean = false, // this is only enabled for the UnitTesting example
    val mainclass: String,
    val commandversion: Int = 2,
    val extravendordeps: ArrayList<String> = arrayListOf(),
)

fun main() {
    val resourcesPath = chooseResourcesPath()
    val destinationPath = chooseDestinationPath()
    val projectType = chooseProjectType()
    val templateConfig = chooseProject(resourcesPath, projectType)
    val teamNumber = chooseTeamNumber()
    val teamPackage = chooseTeamPackage()
    createProject(resourcesPath, destinationPath, templateConfig, teamNumber, teamPackage)
    println("project successfully created")
}

private fun tildeExpand(s: String): String {
    return if (s.startsWith("~"))
        s.replaceFirst("~", System.getProperty("user.home"))
    else s
}

private fun chooseResourcesPath(): Path {
    // Find directories of the form ~/wpilib/yearNumber, e.g., ~/wpilib/2024.
    var suggestionPaths = mutableListOf<Path>()
    val wpilib = Path.of(System.getProperty("user.home"), "wpilib")
    if (wpilib.isDirectory()) {
        for (subdir in Files.newDirectoryStream(wpilib, Path::isDirectory)) {
            subdir.fileName.toString().toIntOrNull() ?: continue
            val path = subdir.resolve(
                Path.of("utility", "resources", "app", "resources")
            )
            if (path.exists())
                suggestionPaths.add(path)
        }
    }
    // As a hack, reorder so that later year numbers come before earlier ones.
    // This way, the default (first suggestion) will be the newest wpilib version.
    suggestionPaths = suggestionPaths.sortedDescending().toMutableList()

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

    if (suggestionPaths.size == 0) {
        while (true) {
            print("what resource path would you like to use? ") // this message isn't very friendly...
            val path = Path.of(tildeExpand(readln()))
            if (pathLooksValid(path))
                return path
        }
    } else {
        println("the following resource paths were detected:")
        for (idx in suggestionPaths.indices)
            println("  [${idx+1}] ${suggestionPaths[idx]}")
        while (true) {
            print("which path would you like to use? (number or your own path) [1] ")
            val response = tildeExpand(readln())
            var num = response.toIntOrNull()
            if (response == "") num = 1 // default to first suggestion
            if (num != null) { // if number given, use corresponding suggestion
                if (num - 1 in suggestionPaths.indices)
                    return suggestionPaths[num - 1]
                println("number out of range, try again")
                continue
            }
            val path = Path.of(response)
            if (pathLooksValid(path))
                return path
        }
    }
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

private fun chooseProjectType(): ProjectType {
    while (true) {
        print("would you like to use a template or an example? [template] ")
        val type = readln()
        if (type == "" || type.startsWith("t"))
            return ProjectType.Template
        else if (type.startsWith("e"))
            return ProjectType.Example
        println("huh?")
    }
}

private fun chooseProject(resourcesPath: Path, projectType: ProjectType): ProjectConfig {
    // Code is essentially duplicated for templates and examples.  For example,
    // the list of templates is in java/src/templates/templates.json, while
    // examples in java/src/examples/examples.json.  These two variables just
    // serve as a kind of "localization" to choose template or example when
    // necessary.
    val project = when(projectType) {
        ProjectType.Example -> "example"
        ProjectType.Template -> "template"
    }
    val projects = when(projectType) {
        ProjectType.Example -> "examples"
        ProjectType.Template -> "templates"
    }

    // Parse list of projects and print to user.
    val jsonText = resourcesPath.resolve(
        Path.of("java", "src", projects, "${projects}.json")
    ).readText()
    val jsons = Klaxon().parseArray<ProjectJson>(jsonText)!!
        .sortedBy { it.foldername }
    println("the following ${projects} are available:")
    for (j in jsons) {
        println("  [${j.foldername}]: ${j.description}")
    }

    // Choose which project to install.
    lateinit var json: ProjectJson
    while (true) {
        try {
            print("which ${project} would you like to use? ")
            val projectName = readln()
            json = jsons.first { it.foldername.startsWith(projectName) }
            if (json.foldername != projectName)
                println("using template ${json.foldername}")
            break
        } catch (e: NoSuchElementException) {
            println("invalid name, try again")
        }
    }

    // Verify some expectations for project files which are true in today's wpilib.
    require(json.mainclass == "Main" && json.commandversion == 2)

    return ProjectConfig(
        name = json.foldername,
        projectType = projectType,
        gradleBase = json.gradlebase,
        extraVendordeps = json.extravendordeps,
        hasUnitTests = json.hasunittests
    )
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

private fun chooseTeamPackage(): String {
    while (true) {
        print("what package path would you like to use? [frc.robot] ")
        val s = readln()
        if (s == "") {
            return "frc.robot"
        } else if (s.matches(Regex("""^[a-zA-Z0-9_.]+$"""))) {
            return s
        } else {
            println("invalid package")
        }
    }
}