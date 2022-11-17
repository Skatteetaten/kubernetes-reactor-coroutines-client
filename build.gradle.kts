plugins {
    id("java-library")
    id("idea")
    id("no.skatteetaten.gradle.aurora") version("4.5.11")
}

dependencies {
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.6.4")
    api("com.fkorotkov:kubernetes-dsl:2.8.1")
    api("org.springframework.boot:spring-boot-starter-webflux")

    testImplementation("org.junit-pioneer:junit-pioneer:1.6.2")
    testImplementation("no.skatteetaten.aurora:mockwebserver-extensions-kotlin:1.4.0")
    testImplementation("io.mockk:mockk:1.13.2")
    testImplementation("com.ninja-squad:springmockk:3.1.1")
}

aurora {
    useLibDefaults
    useKotlinDefaults
    useSpringBootDefaults

    features {
        auroraStarters = false
    }

    versions {
        javaSourceCompatibility = "1.8"
    }
}

java {
    withSourcesJar()
}
