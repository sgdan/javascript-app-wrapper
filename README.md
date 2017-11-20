# javascript-app-wrapper

An executable JAR which can be run as a desktop app. App is implemented in JavaScript divided into:
- UI code running in JavaFX WebView window (Webkit JS engine)
- Worker code running in background threads for processor intensive work (Nashorn JS engine)
- Small Kotlin main class linking everything

To run:
```
 ./gradlew clean shadowJar
 java -jar build/libs/javascript-app-wrapper-all.jar
```