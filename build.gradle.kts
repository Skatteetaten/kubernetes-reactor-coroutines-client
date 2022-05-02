plugins {
    id("java-library")
    id("idea")
    id("no.skatteetaten.gradle.aurora") version("4.4.16")
}

dependencies {
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.6.1")
    api("com.fkorotkov:kubernetes-dsl:2.8.1")
    api("org.springframework.boot:spring-boot-starter-webflux")
    api("io.github.microutils:kotlin-logging-jvm:2.1.21")

    testImplementation("org.junit-pioneer:junit-pioneer:1.6.2")
    testImplementation("no.skatteetaten.aurora:mockwebserver-extensions-kotlin:1.3.1")
    testImplementation("io.mockk:mockk:1.12.3")
    testImplementation("com.ninja-squad:springmockk:3.1.1")
}

aurora {
    useLibDefaults
    useKotlinDefaults
    useSpringBootDefaults

    features {
        auroraStarters = false
    }
}

java {
    withSourcesJar()
}
