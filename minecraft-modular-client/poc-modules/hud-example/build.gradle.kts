plugins { java }
dependencies { compileOnly(project(":poc-api")) }   // jamais 'implementation' : l'API vient du shell
tasks.jar { archiveFileName.set("hud-example.jar") }
