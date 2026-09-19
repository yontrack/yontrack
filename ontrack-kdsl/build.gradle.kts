plugins {
    `java-library`
    id("com.apollographql.apollo").version("5.2.0")
}

dependencies {
    api(project(":ontrack-json"))
    api("org.springframework.boot:spring-boot-starter-web")

    implementation("com.apollographql.apollo:apollo-runtime:5.2.0")
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("org.apache.httpcomponents.core5:httpcore5")
}

apollo {
    service("kdsl") {
        packageName = "net.nemerosa.ontrack.kdsl.connector.graphql.schema"
        schemaFile.set(file("ontrack.graphql"))
        mapScalar("LocalDateTime", "java.time.LocalDateTime")
        mapScalar("UUID", "java.util.UUID")
        mapScalar("JSON", "com.fasterxml.jackson.databind.JsonNode")
        mapScalar("Long", "kotlin.Long")
    }
}
