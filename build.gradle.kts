import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the

plugins {
    id("java")
    id("checkstyle")
    id("pmd")
    jacoco
    id("com.github.spotbugs") version "6.4.8"
    id("com.diffplug.spotless") version "8.3.0"
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
// Javadocs

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
    destinationDir =
        layout.buildDirectory
            .dir("docs/javadoc-test")
            .get()
            .asFile

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

// Spotbugz
spotbugs {
    ignoreFailures.set(false)
    showStackTraces.set(true)
    showProgress.set(true)
    effort.set(Effort.DEFAULT)
    reportLevel.set(Confidence.DEFAULT)
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.create("html") {
        required.set(true)
        outputLocation.set(layout.buildDirectory.file("reports/spotbugs/$name.html"))
    }
    reports.create("xml") {
        required.set(false)
    }
}

// Spotless
spotless {
    java {
        target("src/main/java/**/*.java", "src/test/java/**/*.java")
        googleJavaFormat()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

// Checkstyle
checkstyle {
    toolVersion = "10.12.4"
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(false)
        html.required.set(true)
    }
}

// PMD
pmd {
    isConsoleOutput = true
    toolVersion = "7.16.0"
    rulesMinimumPriority = 5
    ruleSets =
        listOf(
            "category/java/errorprone.xml",
            "category/java/bestpractices.xml",
        )
}

tasks.withType<Pmd>().configureEach {
    reports {
        xml.required.set(false)
        html.required.set(true)
    }
}
tasks.named<Pmd>("pmdTest") {
    ruleSets = emptyList()
    ruleSetConfig = resources.text.fromFile("config/pmd/pmd-test.xml")
}
