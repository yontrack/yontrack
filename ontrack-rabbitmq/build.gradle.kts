plugins {
    `java-library`
}

dependencies {
    api("org.springframework.amqp:spring-amqp")
    api("org.springframework.amqp:spring-rabbit")

    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework:spring-messaging")
    implementation(project(":ontrack-model"))
    implementation(project(":ontrack-job"))
}