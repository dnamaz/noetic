plugins {
    java
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.11.1"
}

group = "com.dnamaz"
version = "0.1.0-SNAPSHOT"

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

    // Spring AI MCP Server (STDIO transport)
    implementation("org.springframework.ai:spring-ai-starter-mcp-server:1.1.2")
    // Spring AI MCP Server (WebMVC transport for REST + SSE)
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc:1.1.2")

    // Web scraping
    implementation("org.jsoup:jsoup:${property("jsoupVersion")}")

    // Dynamic content fetching (headless Chromium via CDP)
    implementation("io.github.fanyong920:jvppeteer:${property("jvppeteerVersion")}")

    // Vector search
    implementation("org.apache.lucene:lucene-core:${property("luceneVersion")}")
    implementation("org.apache.lucene:lucene-analysis-common:${property("luceneVersion")}")
    implementation("org.apache.lucene:lucene-queryparser:${property("luceneVersion")}")

    // Local embeddings via Spring AI Transformers (ONNX + tokenizer)
    implementation("org.springframework.ai:spring-ai-starter-model-transformers:1.1.2")
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

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("--enable-preview"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}

tasks.withType<JavaExec> {
    jvmArgs("--enable-preview")
}

graalvmNative {
    binaries {
        named("main") {
            imageName = "noetic"
            mainClass = "com.dnamaz.websearch.WebSearchApplication"
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
                "--initialize-at-run-time=ai.djl.huggingface.tokenizers.jni",
                "--initialize-at-run-time=ai.djl.pytorch",
                "--initialize-at-run-time=ai.djl.pytorch.jni",
                "--initialize-at-run-time=ai.djl.engine.rust"
            )
        }
    }
}
