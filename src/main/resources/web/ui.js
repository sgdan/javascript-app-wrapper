// called from worker thread
function workDone(msg, seconds, thread) {
    var ta = document.getElementById('messages')
    ta.value = msg + " (" + seconds + "s, " + thread + ")\n" + ta.value
}

// this gets logged to the WebView dev tools console
function doLog() {
    console.log('Logging message: ' + document.getElementById('input-id').value)
}

function doWork() {
    send("doWork", parseInt(document.getElementById('seconds').value))
}