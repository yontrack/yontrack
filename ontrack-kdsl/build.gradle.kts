plugins {
    `java-library`
    id("com.apollographql.apollo").version("4.1.1")
}

dependencies {
    api(project(":ontrack-json"))
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-restclient")

    implementation("com.apollographql.apollo:apollo-runtime:4.1.1")
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("org.apache.httpcomponents.core5:httpcore5")
}

apollo {
    service("kdsl") {
        packageName = "net.nemerosa.ontrack.kdsl.connector.graphql.schema"
        schemaFile.set(file("ontrack.graphql"))
        mapScalar("LocalDateTime", "java.time.LocalDateTime")
        mapScalar("UUID", "java.util.UUID")
        mapScalar("JSON", "tools.jackson.databind.JsonNode")
        mapScalar("Long", "kotlin.Long")
    }
}
