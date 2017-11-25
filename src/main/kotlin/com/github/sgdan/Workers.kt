package com.github.sgdan

import jdk.nashorn.api.scripting.JSObject
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import java.net.URL
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
    private val nWorkers = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1)
    private val pool = newFixedThreadPoolContext(nWorkers, "worker-pool")
    private val console = Console() // redirect messages from workers to console
    private val worker: JSObject

    init {
        val bindings = engine.createBindings()
        bindings.put("console", console) // support console.log for workers
        bindings.put("ui", ui)
        val bound = engine.eval(script.readText(), bindings)
        when (bound) {
            is JSObject -> worker = bound
            else -> throw Exception("Unable to read script")
        }
    }

    fun stop() {
        pool.close()
    }

    fun send(args: List<Any?>) {
        if (args.isEmpty()) throw Exception("Arguments needed")
        val name = args.first().toString()
        // call named method in worker thread
        launch(pool) {
            val fn = worker.getMember(name)
            when (fn) {
                is JSObject -> fn.call(fn, *args.drop(1).toTypedArray())
                else -> throw Exception("Function not found: $name")
            }
        }
    }
}