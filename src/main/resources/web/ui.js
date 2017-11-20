// called from worker thread
function workDone(args) {
    var ta = document.getElementById('messages')
    ta.value = args + '\n' + ta.value
}

// this gets logged to the WebView dev tools console
function doLog() {
    console.log('Logging message: ' + document.getElementById('input-id').value)
}

function doWork() {
    tasks.add("doWork", [document.getElementById('seconds').value])
}