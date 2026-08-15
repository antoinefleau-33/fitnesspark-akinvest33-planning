plugins { java }
dependencies { compileOnly(project(":poc-api")) }
tasks.jar { archiveFileName.set("be-diagnostics.jar") }
