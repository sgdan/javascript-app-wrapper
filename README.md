# javascript-app-wrapper

An executable JAR which can be run as a desktop app
- UI is JavaScript/HTML/CSS running in JavaFX WebView window (Webkit JS engine)
- Javascript worker threads for processor intensive work and system calls (Nashorn JS engine)
- Small Kotlin program linking UI and workers
- Dev mode to watch JavaScript/HTML/CSS and reload app on changes
- Firebug lite example for debugging UI

### Quick start

- Make sure you have Java JRE installed so you can run executable JAR files (I used JDK 9 to test)
- Download release [javascript-app-wrapper-0.0.1.jar](https://github.com/sgdan/javascript-app-wrapper/releases/download/v0.0.1/javascript-app-wrapper-0.0.1.jar)
- Double-click the jar to run the demo app:

![Demo App Screenshot](https://raw.githubusercontent.com/sgdan/javascript-app-wrapper/master/docs/images/demo.png "Demo App")
    
- Click "Log" to log a message to the Firebug console
- Click "Work" to do some work in a background thread. Click it a few times and watch your CPU go to 75% for a while.
- Exit the demo then extract the demo app files:
    ```
    C:\test>java -jar javascript-app-wrapper-0.0.1.jar unpackage
    Created: web\worker.js
    Created: web\ui.js
    Created: web\ui.html
    Created: web\ui.css
    ```
- Run the app from the command line in "dev" mode
    ```
    C:\test>java -jar javascript-app-wrapper-0.0.1.jar
    Nov. 23, 2017 8:08:07 PM tornadofx.Stylesheet$Companion detectAndInstallUrlHandler
    INFO: Installing CSS url handler, since it was not picked up automatically
    In dev mode. Watching C:\test\web
    ```
- Make some changes:
  - In the `web` folder edit `ui.css` and change the background to brown.
  - Edit `ui.html` and change the header to `<h1>My Custom App!</h1>`
  - Remove the debug lines from `ui.html`:
    ```
    <!-- Show debug tools -->
    <script type='text/javascript' src='http://getfirebug.com/releases/lite/1.2/firebug-lite-compressed.js'></script>
    ```
  - Notice that the app reloads as each change is saved:
  
  ![Custom App Screenshot](https://raw.githubusercontent.com/sgdan/javascript-app-wrapper/master/docs/images/modified.png "Custom App")

- Exit the modified app package the changes into a new executable jar:
    ```
    C:\test>java -jar javascript-app-wrapper-0.0.1.jar package MyCustomApp.jar
    Deleted: /web/worker.js
    Deleted: /web/ui.js
    Deleted: /web/ui.html
    Deleted: /web/ui.css
    Added: web/ui.css
    Added: web/ui.html
    Added: web/ui.js
    Added: web/worker.js
    Packaged: MyCustomApp.jar
    ```
- Double-click `MyCustomApp.jar` to run the customised code
  
  
### Build and run from source
```
 ./gradlew clean shadowJar
 java -jar build/libs/javascript-app-wrapper-all.jar
```
To test dev mode unpack first...
```
java -jar build/libs/javascript-app-wrapper-all.jar unpackage
```
...then rebuild and run
```
./gradlew shadowJar && java -jar build/libs/javascript-app-wrapper-all.jar
```