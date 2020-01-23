plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.61"
    id("org.jetbrains.kotlin.plugin.spring") version "1.3.61"
    id("org.jlleitschuh.gradle.ktlint") version "9.1.1"
    id("org.sonarqube") version "2.8"
    id("org.springframework.boot") version "2.2.4.RELEASE"
    id("org.asciidoctor.convert") version "2.3.0"

    id("com.gorylenko.gradle-git-properties") version "2.2.0"
    id("com.github.ben-manes.versions") version "0.27.0"
    id("se.patrikerdes.use-latest-versions") version "0.2.13"
    id("com.adarshr.test-logger") version "2.0.0"

    // TODO: want to exclude the starter here.
    id("no.skatteetaten.gradle.aurora") version "3.2.0"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.3.3")
    implementation("com.fkorotkov:kubernetes-dsl:3.0")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("io.projectreactor.addons:reactor-extra:3.3.2.RELEASE")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.3.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.19")
    testImplementation("no.skatteetaten.aurora:mockmvc-extensions-kotlin:1.0.4")
    testImplementation("io.projectreactor:reactor-test:3.3.2.RELEASE")
    testImplementation("org.awaitility:awaitility-kotlin:4.0.2")
    testImplementation("com.ninja-squad:springmockk:2.0.0")
}
