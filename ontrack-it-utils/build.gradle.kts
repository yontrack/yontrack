plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    testFixturesApi("org.springframework:spring-test")
    testFixturesApi(project(":ontrack-common"))
    testFixturesApi(project(":ontrack-model"))
    testFixturesApi(project(":ontrack-job"))
    testFixturesApi(project(":ontrack-test-utils"))
    testFixturesApi("org.springframework:spring-jdbc")
    testFixturesApi("org.springframework.security:spring-security-core")
    testFixturesApi("org.springframework.security:spring-security-oauth2-jose")
    testFixturesApi("org.jetbrains.kotlin:kotlin-test")

    testFixturesImplementation(project(":ontrack-repository"))
    testFixturesImplementation(project(":ontrack-extension-support"))
    testFixturesImplementation(project(":ontrack-ui-support"))
    // ITDevLicenseKeyConfiguration: the licence extension, when a module's tests have it, gets every
    // licensed feature without a development key (#2107). Compile-only, so that it-utils does not
    // put the extension into the tests of the modules which do not use it.
    testFixturesCompileOnly(project(":ontrack-extension-license"))
    testFixturesImplementation("org.springframework:spring-context")
    testFixturesImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testFixturesImplementation("org.slf4j:slf4j-api")
    testFixturesImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testFixturesImplementation("org.springframework.boot:spring-boot-starter-test")
    testFixturesImplementation("io.micrometer:micrometer-core")
    testFixturesImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    testFixturesImplementation("net.ttddyy:datasource-proxy")

    testFixturesImplementation("io.mockk:mockk")
    testFixturesImplementation("io.mockk:mockk-jvm")
    testFixturesImplementation("io.mockk:mockk-dsl")
    testFixturesImplementation("io.mockk:mockk-dsl-jvm")

    testFixturesRuntimeOnly("org.postgresql:postgresql")
    testFixturesRuntimeOnly("org.flywaydb:flyway-core")
}