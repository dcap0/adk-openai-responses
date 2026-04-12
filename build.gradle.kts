plugins {
    id("java-library")
    id("maven-publish")
}

group = "org.ddmac"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }

    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    // Source: https://mvnrepository.com/artifact/com.google.adk/google-adk
    compileOnly("com.google.adk:google-adk:1.0.0")
    compileOnly("org.slf4j:slf4j-api:2.0.12")
    // Source: https://mvnrepository.com/artifact/com.fasterxml.jackson.core/jackson-databind
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")


    testImplementation("com.google.adk:google-adk:1.0.0")
    // JUnit 5 Dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("uk.org.webcompere:system-stubs-jupiter:2.1.8")

    testRuntimeOnly("org.slf4j:slf4j-api:2.0.12")

    // Assertions and Mocking
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")

    testImplementation("org.wiremock:wiremock:3.5.4")

}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("adk-openai-responses")
                description.set("A reactive adapter integrating the OpenAI Responses API with the Google Agent Development Kit (ADK) 1.0 for Java.")
                url.set("https://github.com/dcap0/adk-openai-responses") // Update with your actual URL

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("dcap0")
                        name.set("Dennis Capone")
                    }
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}