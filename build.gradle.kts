plugins {
    id("org.jetbrains.kotlin.jvm") version "1.3.50"
    id("org.jetbrains.kotlin.plugin.spring") version "1.3.50"
    id("org.jlleitschuh.gradle.ktlint") version "9.1.1"
    id("org.sonarqube") version "2.8"
    id("org.springframework.boot") version "2.2.1.RELEASE"
    id("org.asciidoctor.convert") version "2.3.0"

    id("com.gorylenko.gradle-git-properties") version "2.2.0"
    id("com.github.ben-manes.versions") version "0.27.0"
    id("se.patrikerdes.use-latest-versions") version "0.2.13"

    //TODO: want to exclude the starter here.
    id("no.skatteetaten.gradle.aurora") version "3.2.0"
}

dependencies {
    implementation("com.fkorotkov:kubernetes-dsl:2.6")
    implementation("io.fabric8:kubernetes-model:4.6.3")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    //implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.0.0.RELEASE")
    implementation("io.projectreactor.addons:reactor-extra:3.3.0.RELEASE")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.19")
    testImplementation("com.nhaarman:mockito-kotlin:1.6.0")
    testImplementation("no.skatteetaten.aurora:mockmvc-extensions-kotlin:1.0.2")
    testImplementation("io.projectreactor:reactor-test:3.3.0.RELEASE")
    testImplementation("org.awaitility:awaitility-kotlin:4.0.1")
}
