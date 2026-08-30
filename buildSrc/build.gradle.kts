plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("ontrackVersioning") {
            id = "net.nemerosa.ontrack.versioning"
            implementationClass = "net.nemerosa.ontrack.build.OntrackVersioningPlugin"
            displayName = "Ontrack Versioning Plugin"
            description = "Computes project version from git & VERSION file and registers writeVersion task"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
