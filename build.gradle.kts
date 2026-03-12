import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the

plugins {
    id("java")
    jacoco
}

group = "org.example"
version = "1.0-SNAPSHOT"

jacoco {
    toolVersion = "0.8.14"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}


tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.10".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
//Javadocs

java {
    withJavadocJar()
}

val sourceSets = the<SourceSetContainer>()

tasks.named<Javadoc>("javadoc") {
    description = "Generates Javadoc HTML for main sources."
    group = "documentation"

    source = sourceSets["main"].allJava
    classpath = sourceSets["main"].compileClasspath + sourceSets["main"].output

    options.encoding = "UTF-8"

    (options as StandardJavadocDocletOptions).apply {
        author(true)
        version(true)
        links("https://docs.oracle.com/en/java/javase/17/docs/api/")
    }
}

val testJavadoc by tasks.registering(Javadoc::class) {
    description = "Generates Javadoc HTML for test sources."
    group = "documentation"

    source = sourceSets["test"].allJava
    classpath = sourceSets["test"].compileClasspath + sourceSets["test"].output
    destinationDir = layout.buildDirectory.dir("docs/javadoc-test").get().asFile

    options.encoding = "UTF-8"


    (options as StandardJavadocDocletOptions).apply {
        memberLevel = JavadocMemberLevel.PRIVATE
        author(true)
        version(true)
        links("https://docs.oracle.com/en/java/javase/17/docs/api/")
    }
}

val testJavadocJar by tasks.registering(Jar::class) {
    dependsOn(testJavadoc)
    archiveClassifier.set("test-javadoc")
    from(layout.buildDirectory.dir("docs/javadoc-test"))
}