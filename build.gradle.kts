plugins {
    id("java-library")
    id("idea")
    id("no.skatteetaten.gradle.aurora") version("4.4.10")
}

dependencies {
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.6.0")
    api("com.fkorotkov:kubernetes-dsl:2.8.1")
    api("org.springframework.boot:spring-boot-starter-webflux:2.6.3")
    api("io.projectreactor.addons:reactor-extra:3.4.6")
    api("io.projectreactor:reactor-core:3.4.14")
    api("io.github.microutils:kotlin-logging-jvm:2.1.21")

    testImplementation("org.junit-pioneer:junit-pioneer:1.5.0")
    testImplementation("no.skatteetaten.aurora:mockwebserver-extensions-kotlin:1.2.0")
    testImplementation("io.mockk:mockk:1.12.2")
    testImplementation("com.ninja-squad:springmockk:3.1.0")
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
