plugins {
    `java-library`
}

dependencies {
    api(project(":ontrack-model"))
    api(project(":ontrack-extension-api"))
    api("org.springframework:spring-web")
    api("org.springframework:spring-webmvc")

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.apache.commons:commons-lang3")
    implementation("org.apache.commons:commons-text")

}
