plugins {
    `java-library`
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation(project(":ontrack-model"))
    implementation(project(":ontrack-repository"))
    implementation(project(":ontrack-extension-api"))
    implementation(project(":ontrack-extension-support"))
    implementation(project(":ontrack-job"))
    implementation("org.springframework.security:spring-security-core")
    implementation("org.springframework.security:spring-security-config")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.slf4j:slf4j-api")
    implementation("commons-io:commons-io")
    implementation("org.apache.commons:commons-lang3")
    implementation("org.jgrapht:jgrapht-core")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.flywaydb:flyway-core")
    implementation("org.springframework.boot:spring-boot-flyway")

    runtimeOnly("org.hibernate.validator:hibernate-validator")

    testImplementation(testFixtures(project(":ontrack-it-utils")))
    testImplementation(testFixtures(project(":ontrack-common")))
    testImplementation(testFixtures(project(":ontrack-model")))
    testImplementation(testFixtures(project(":ontrack-extension-api")))

    testRuntimeOnly(project(":ontrack-repository-impl"))
    testRuntimeOnly("io.micrometer:micrometer-registry-prometheus")

}
