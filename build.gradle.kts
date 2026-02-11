plugins {
    java
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.11.1"
}

group = "com.noetic"
version = property("appVersion") as String

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Official MCP Java SDK (STDIO, SSE, Streamable-HTTP transports)
    implementation("io.modelcontextprotocol.sdk:mcp:${property("mcpSdkVersion")}")

    // Web scraping
    implementation("org.jsoup:jsoup:${property("jsoupVersion")}")

    // Dynamic content fetching (headless Chromium via CDP)
    implementation("io.github.fanyong920:jvppeteer:${property("jvppeteerVersion")}")

    // Vector search
    implementation("org.apache.lucene:lucene-core:${property("luceneVersion")}")
    implementation("org.apache.lucene:lucene-analysis-common:${property("luceneVersion")}")
    implementation("org.apache.lucene:lucene-queryparser:${property("luceneVersion")}")

    // DJL + ONNX Runtime (manual translator approach for local embeddings)
    // Tokenization is handled by pure Java BertWordPieceTokenizer (no Rust JNI)
    implementation("ai.djl:api:${property("djlVersion")}")
    implementation("ai.djl.onnxruntime:onnxruntime-engine:${property("djlVersion")}") {
        exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime")
    }
    implementation("com.microsoft.onnxruntime:onnxruntime:${property("onnxruntimeVersion")}")

    // PDF parsing
    implementation("org.apache.pdfbox:pdfbox:3.0.6")

    // CAPTCHA solving (optional, only used when websearch.captcha.active != none)
    implementation("com.github.2captcha:2captcha-java:1.3.1")

    // CLI
    implementation("info.picocli:picocli:${property("picocliVersion")}")
    annotationProcessor("info.picocli:picocli-codegen:${property("picocliVersion")}")

    // Jackson (for JSON serialization)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Generate version.properties from gradle version — single source of truth
tasks.register("generateVersionProperties") {
    val outputDir = layout.buildDirectory.dir("generated/resources")
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.resolve("version")
        dir.mkdirs()
        dir.resolve("version.properties").writeText("version=${project.version}\n")
    }
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated/resources"))
        }
    }
}

tasks.named("processResources") {
    dependsOn("generateVersionProperties")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--enable-preview"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}

tasks.withType<JavaExec> {
    jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED")
}

graalvmNative {
    binaries {
        named("main") {
            imageName = "noetic"
            mainClass = "com.noetic.websearch.WebSearchApplication"
            buildArgs.addAll(
                "--enable-preview",
                "--no-fallback",
                "-H:+ReportExceptionStackTraces",
                "--enable-native-access=ALL-UNNAMED",
                "--enable-url-protocols=http,https",
                "-H:+UnlockExperimentalVMOptions",
                "-H:+SharedArenaSupport",
                "-H:+ForeignAPISupport",
                "--initialize-at-build-time=org.slf4j",
                "--initialize-at-build-time=ch.qos.logback",
                "--initialize-at-run-time=ai.onnxruntime",
                "--initialize-at-run-time=com.microsoft.onnxruntime",
                "--initialize-at-run-time=ai.djl.onnxruntime.engine",
                "--initialize-at-run-time=ai.djl.pytorch",
                "--initialize-at-run-time=ai.djl.pytorch.jni",
                "--initialize-at-run-time=ai.djl.engine.rust"
            )
        }
    }
}
