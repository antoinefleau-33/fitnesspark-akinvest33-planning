plugins { java }
dependencies {
    compileOnly(project(":poc-api"))
    // compileOnly(files("$rootDir/versions/1.20.1/client-poc.jar"))  // client remappé
}
