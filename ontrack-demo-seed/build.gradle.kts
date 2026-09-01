plugins {
    application
}

description = "Seeds and resets the demo environment through the Yontrack API."

dependencies {
    implementation(project(":ontrack-kdsl"))
    implementation(project(":ontrack-json"))
    implementation("org.slf4j:slf4j-api")
    runtimeOnly("ch.qos.logback:logback-classic")
}

application {
    mainClass.set("net.nemerosa.ontrack.demo.seed.MainKt")
}
