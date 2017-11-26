var Thread = java.lang.Thread

function doWork(seconds) {
    var s = parseInt(seconds)
    if (isNaN(s) || s < 0 || s > 15) {
        ui.send("workDone", "Invalid work time", s, Thread.currentThread().name)
    } else {
        console.log("working for " + seconds + " seconds")
        var start = new Date().getTime()
        var finish = start + parseInt(seconds) * 1000
        var result = 0
        var finished = false
        while(!finished) {
            result += Math.random() * Math.random()
            finished = new Date().getTime() > finish
        }
        ui.send("workDone", "work finished", seconds, Thread.currentThread().name)
    }
}

/*
 * Return this script object to the framework which will redirect
 * method calls from the UI to functions declared above via the
 * background thread pool.
 */
this