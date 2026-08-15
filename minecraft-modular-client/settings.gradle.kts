rootProject.name = "modular-client-poc"

include(
    "poc-api",      // Contrat partagé, chargé UNIQUEMENT par le classloader racine
    "poc-core",     // Shell : modules, input, versions. Zéro dépendance externe.
    "poc-ui",       // Rendu LWJGL 3 / OpenGL 3.3 core
    "poc-adapters:adapter-1_8_9",
    "poc-adapters:adapter-1_20_1",
    "poc-modules:hud-example",
)
