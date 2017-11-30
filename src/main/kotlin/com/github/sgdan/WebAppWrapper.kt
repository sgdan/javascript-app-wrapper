package com.github.sgdan

import javafx.application.Application
import javafx.concurrent.Worker
import javafx.scene.web.WebView
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import netscape.javascript.JSObject
import tornadofx.*
import java.io.File
import java.lang.Thread.sleep
import java.net.URI
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds.*
import javax.script.ScriptEngineManager
import jdk.nashorn.api.scripting.JSObject as NasObject
import kotlinx.coroutines.experimental.javafx.JavaFx as UI

class WebAppWrapper : App(WebAppView::class)

class WebAppView : View() {
    private val web = WebView()
    override val root = web

    private val nWorkers = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1)
    private val pool = newFixedThreadPoolContext(nWorkers, "worker-pool")
    private var workerScript: NasObject? = null

    fun devFolder() = File("web")

    fun inDevMode() = devFolder().exists()

    /** For workers to send messages to the UI */
    private val ui = object {
        fun send(vararg args: Any?) {
            if (args.isEmpty()) throw Exception("Arguments needed")
            val name = args[0].toString()
            // call named method in JavaFX UI thread
            launch(UI) {
                val window = web.engine.executeScript("window") as JSObject
                window.call(name, *args.drop(1).toTypedArray())
            }
        }
    }

    /**
     * Check both classpath and current folder for a "web" folder resource
     */
    fun findResource(name: String): URL =
            // from dev folder
            if (inDevMode()) File(devFolder(), name).toURI().toURL()

            // from classpath (i.e. contained within executable jar
            else this::class.java.classLoader.getResource("web/$name")

    /** For UI to send messages to the worker */
    val worker = object {
        fun sendHook(args: JSObject) {
            val len = args.getMember("length")
            if (len !is Int || len == 0) throw Exception("Arguments needed")
            val name = args.getSlot(0).toString()
            val params = 1.until(len).map { args.getSlot(it) }.toTypedArray()
            launch(pool) {
                val fn = workerScript?.getMember(name) as? NasObject
                        ?: throw Exception("Function not found: $name")
                fn.call(fn, *params)
            }
        }
    }

    fun loadWorker() {
        val workerHook = checkNotNull(findResource("worker.js")) {
            "Must provide worker hook: web/worker.js"
        }
        val nashorn = ScriptEngineManager().getEngineByName("nashorn")
        val bindings = nashorn.createBindings()
        bindings.put("console", Console())
        bindings.put("ui", ui)
        bindings.put("wrapper", this)
        workerScript = nashorn.eval(workerHook.readText(), bindings) as? NasObject
                ?: throw Exception("Unable to load worker script $workerHook")
    }

    init {
        loadWorker()

        // UI
        val uiHook = checkNotNull(findResource("ui.html")) {
            "Must provide UI hook: web/ui.html"
        }
        web.engine.loadWorker.stateProperty().addListener { _, _, newValue ->
            if (newValue == Worker.State.SUCCEEDED) {
                val window = web.engine.executeScript("window") as JSObject
                // WebView doesn't support varargs, so marshal args with this function
                window.eval("function send() { worker.sendHook(arguments) }")
                window.setMember("worker", worker)
            }
        }
        web.engine.load(uiHook.toExternalForm())

        // watch folder for changes
        if (inDevMode()) {
            val watcher = FileSystems.getDefault().newWatchService()
            val path = Paths.get(devFolder().toURI())
            path.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
            launch {
                while (true) {
                    val key = watcher.take()
                    sleep(50) // wait a bit, in case there are multiple events
                    key.pollEvents()
                    launch(UI) { web.engine.reload() } // reload the UI
                    loadWorker() // reload worker
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
    if (!Files.exists(webPath)) Files.createDirectories(webPath)
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
            Files.createDirectories(newFilePath.parent)
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

/**
 * For worker threads to log to System.out, simulates console.log()
 */
class Console {
    fun log(vararg data: Any?) = println(data.joinToString())
}
