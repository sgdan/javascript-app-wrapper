package com.github.sgdan

import javafx.application.Application
import javafx.scene.web.WebView
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import netscape.javascript.JSObject
import tornadofx.*
import java.io.File
import java.net.URL
import java.nio.file.FileSystems
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

fun main(args: Array<String>) {
    // load front end
    Application.launch(WebAppWrapper::class.java)
    // thread blocks here until application exits, don't add code after this!
    // can't run this in bg thread, will exit too early
}