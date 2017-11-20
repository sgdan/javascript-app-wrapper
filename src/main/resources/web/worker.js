var jobs = {
    doWork: function(args) {
        var seconds = args[0]
        console.log("working for " + seconds + " seconds")
        var start = new Date().getTime()
        var result = 0
        var finished = false
        while(!finished) {
            result += Math.random() * Math.random()
            finished = new Date().getTime() > start + (seconds * 1000)
        }
        ui.send("workDone", [seconds + "s of work done"])
    }
}

while (true) {
    var task = tasks.take()
    jobs[task.name](task.args)
}

