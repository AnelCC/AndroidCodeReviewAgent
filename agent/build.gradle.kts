plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.anelcc.agent.MainKt")
}

// Resolve the default samples folder from the repository root.
tasks.named<JavaExec>("run") {
    workingDir(rootProject.projectDir)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test-junit"))
}
