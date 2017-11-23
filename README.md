# javascript-app-wrapper

An executable JAR which can be run as a desktop app
- UI is JavaScript/HTML/CSS running in JavaFX WebView window (Webkit JS engine)
- Javascript worker threads for processor intensive work and system calls (Nashorn JS engine)
- Small Kotlin program linking UI and workers
- Dev mode to watch JavaScript/HTML/CSS and reload app on changes
- Firebug lite example for debugging UI

### Build and run
```
 ./gradlew clean shadowJar
 java -jar build/libs/javascript-app-wrapper-all.jar
```

### Todo
- Main window size/resize/transparency configuration and example?
