package com.github.sgdan

import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import netscape.javascript.JSObject
import java.net.URL
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.SECONDS
import javax.script.ScriptEngine
import kotlinx.coroutines.experimental.javafx.JavaFx as UI

/**
 * For worker threads to log to System.out, simulates console.log()
 */
class Console {
    fun log(vararg data: Any?) = println(data.joinToString())
}

/**
 * A pool of background worker threads to perform tasks asynchronously
 */
class Workers(engine: ScriptEngine, script: URL, ui: Any) {
    val nWorkers = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1)
    val pool = newFixedThreadPoolContext(nWorkers, "worker-pool")
    private val console = Console() // redirect messages from workers to console

    /** Tasks to be processed by the workers */
    private val tasks = LinkedBlockingQueue<Task>()
    private var running = true

    init {
        (1..nWorkers).forEach {
            launch(pool) {
                // bindings aren't thread safe, so one for each thread
                val bindings = engine.createBindings()
                bindings.put("console", console) // support console.log for workers
                bindings.put("tasks", workerTake)
                bindings.put("ui", ui)
                engine.eval(script.readText(), bindings)
            }
        }
    }

    fun stop() {
        running = false
        pool.close()
        tasks.clear()
    }

    fun add(name: String, args: JSObject) {
        tasks.add(Task(name, toArray(args)))
    }

    /**
     * Convert JSObject to java array
     */
    private fun toArray(jso: JSObject): Array<Any> {
        val len = jso.getMember("length")
        return if (len is Int) Array(len) { i -> jso.getSlot(i) }
        else emptyArray()
    }

    private val workerTake = object {
        fun take(): Task? {
            while (running) {
                val task = tasks.poll(5, SECONDS)
                if (task != null) return task
            }
            return null
        }
    }
}