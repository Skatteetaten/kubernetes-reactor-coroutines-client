plugins {
    id("java-library")
    id("idea")
    id("no.skatteetaten.gradle.aurora") version("4.3.22")
}

dependencies {
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.12.5")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.5.2")
    api("com.fkorotkov:kubernetes-dsl:2.8.1")
    api("org.springframework.boot:spring-boot-starter-webflux:2.5.6")
    api("io.projectreactor.addons:reactor-extra:3.4.5")
    api("io.projectreactor:reactor-core:3.4.11")
    api("io.github.microutils:kotlin-logging-jvm:2.0.11")

    testImplementation("org.junit-pioneer:junit-pioneer:1.4.2")
    testImplementation("no.skatteetaten.aurora:mockwebserver-extensions-kotlin:1.1.8")
    testImplementation("io.mockk:mockk:1.12.0")
    testImplementation("com.ninja-squad:springmockk:3.0.1")
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
