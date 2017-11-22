package com.github.sgdan

import javafx.application.Application
import javafx.scene.web.WebView
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import netscape.javascript.JSObject
import tornadofx.*
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds.*
import java.util.concurrent.LinkedBlockingQueue
import javax.script.ScriptEngineManager
import kotlinx.coroutines.experimental.javafx.JavaFx as UI

class WebAppWrapper : App(WebAppView::class)

/**
 * For worker threads to log to System.out, simulates console.log()
 */
class Console {
    fun log(vararg data: Any?) = println(data.joinToString())
}

/** Task to be executed by worker thread */
data class Task(val name: String, val args: Array<Any>)

class WebAppView : View() {
    private val nashorn = ScriptEngineManager().getEngineByName("nashorn")
    private val console = Console() // redirect messages from workers to console

    /** Tasks to be processed by the workers */
    private val tasks = LinkedBlockingQueue<Task>()

    private val web = WebView()
    override val root = web

    fun devFolder() = File("web")

    fun inDevMode() = devFolder().exists()

    fun window() = web.engine.executeScript("window") as JSObject

    /** For workers to send messages to the UI */
    private val ui = object {
        fun send(name: String, args: Array<Any>) {
            // call named method in JavaFX UI thread
            launch(UI) { window().call(name, args) }
        }
    }

    fun toArray(jso: JSObject): Array<Any> {
        val len = jso.getMember("length")
        return if (len is Int) Array(len) { i -> jso.getSlot(i) }
        else emptyArray()
    }

    /** For UI to create tasks */
    private val tasksHook = object {
        fun add(name: String, args: JSObject) {
            tasks.add(Task(name, toArray(args)))
        }
    }

    /**
     * Check both classpath and current folder for a "web" folder resource
     */
    fun findResource(name: String): URL =
            // from dev folder
            if (inDevMode()) File(devFolder(), name).toURI().toURL()

            // from classpath (i.e. contained within executable jar
            else WebAppWrapper::class.java.classLoader.getResource("web/$name")

    init {
        val frontEndHook = checkNotNull(findResource("ui.html")) {
            "Must provide front end hook: web/ui.html"
        }
        val backEndHook = checkNotNull(findResource("worker.js")) {
            "Must provide back end hook: web/worker.js"
        }

        // load worker threads, leave one core for the UI
        val nWorkers = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1)
        val pool = newFixedThreadPoolContext(nWorkers, "worker-pool")
        (1..nWorkers).forEach {
            launch(pool) {
                // bindings aren't thread safe, so one for each thread
                val bindings = nashorn.createBindings()
                bindings.put("console", console) // support console.log for workers
                bindings.put("tasks", tasks)
                bindings.put("ui", ui)
                nashorn.eval(backEndHook.readText(), bindings)
            }
        }

        // load front end
        window().setMember("tasks", tasksHook)
        web.engine.load(frontEndHook.toExternalForm())

        // watch folder for changes
        if (inDevMode()) {
            val watcher = FileSystems.getDefault().newWatchService()
            val path = Paths.get(devFolder().toURI())
            path.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
            launch {
                while (true) {
                    val key = watcher.take()
                    key.pollEvents().forEach {
                        println("event: ${it.kind()}")
                    }
                    launch(UI) { web.engine.reload() }
                    key.reset()
                }
            }
            println("In dev mode. Watching ${devFolder().absolutePath}")
        } else println("Not in dev mode")
    }
}

fun sourceFile() = File(WebAppWrapper::class.java.protectionDomain.codeSource.location.path)

fun usage(jar: String) {
    println("""|usage:
         |  java -jar $jar [package <target>|unpackage]
         |  Examples
         |    Package "web" folder and contents into new executable jar:
         |    java -jar $jar package MyPackagedCode.jar
         |
         |    Unpackage "web" folder and contents from jar into current directory
         |    java -jar $jar unpackage
         |
         |    Run the application (dev mode if "web" folder present)
         |    java -jar $jar
         |""".trimMargin("|"))
    System.exit(1)
}

fun doPackage(target: String) {
    // copy the current jar
    val web = File("web")
    if (!web.isDirectory) throw Exception("No 'web' folder to package")
    val newJar = File(target)
    if (newJar.exists()) newJar.delete()
    sourceFile().copyTo(newJar)

    // remove "web" folder and contents from new jar
    val zipUri = URI.create("jar:${newJar.toURI()}")
    val zfs = FileSystems.newFileSystem(zipUri, mapOf("create" to "false"))
    val webPath = zfs.getPath("web")
    Files.walk(webPath).forEach {
        if (!Files.isDirectory(it)) {
            Files.delete(it)
            println("Deleted: $it")
        }
    }

    // add files from external "web" folder
    web.walk().forEach {
        if (it.isFile) {
            val newFilePath = webPath.resolve(it.relativeTo(web).path)
            println("Added: $newFilePath")
            Files.copy(it.toPath(), newFilePath)
        }
    }

    // flush changes to zip
    zfs.close()
    println("Packaged: ${File(target)}")
}

fun doUnpackage() {
    val web = File("web")
    if (web.exists()) throw Exception("There's already a 'web' folder")

    // unpack web folder externally
    val zipUri = URI.create("jar:${sourceFile().toURI()}")
    val zfs = FileSystems.newFileSystem(zipUri, mapOf("create" to "false"))
    val webPath = zfs.getPath("/web")
    Files.walk(webPath).forEach {
        if (!Files.isDirectory(it)) {
            val target = File(web, webPath.relativize(it).toString())
            target.parentFile.mkdirs()
            Files.copy(it, target.toPath())
            println("Created: $target")
        }
    }
}

fun main(args: Array<String>) {
    val jar = sourceFile().name
    when {
        args.isEmpty() -> Application.launch(WebAppWrapper::class.java)
        !jar.endsWith(".jar") -> {
            System.err.println("Commands package|unpackage only supported when running from jar")
            System.exit(1)
        }
        args.size == 2 && args[0] == "package" -> doPackage(args[1])
        args.size == 1 && args[0] == "unpackage" -> doUnpackage()
        else -> usage(jar)
    }
}