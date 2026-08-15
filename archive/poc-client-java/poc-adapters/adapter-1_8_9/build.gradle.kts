plugins { java }
dependencies { compileOnly(project(":poc-api")) }
// Cet adaptateur nécessite en plus la passe de réécriture LWJGL2 → LWJGL3 (voir docs/03).
