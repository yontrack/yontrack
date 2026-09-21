plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":ontrack-extension-api"))
    api(project(":ontrack-job"))
    api(project(":ontrack-ui-support"))
    api("org.springframework:spring-context")
    api("org.springframework:spring-tx")
    api("org.springframework:spring-web")
    api("org.springframework.boot:spring-boot")
    api("org.springframework.boot:spring-boot-actuator")
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-health")
    api("org.springframework.boot:spring-boot-restclient")
    api("org.slf4j:slf4j-api")

    implementation("org.apache.commons:commons-lang3")

    // Make sure the following libraries are available for the extension when they need them
    runtimeOnly(project(":ontrack-git"))
    runtimeOnly(project(":ontrack-tx"))

    testFixturesImplementation("org.springframework.boot:spring-boot-starter-test")
}
