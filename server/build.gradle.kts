import org.jetbrains.intellij.tasks.RunIdeTask

plugins {
  id("java")
  // id("org.jetbrains.kotlin.jvm") version "1.7.0"
  id("org.jetbrains.intellij") version "1.17.2"
}

group = "org.rri.ideals.server"
version = System.getenv("IDEALS_VERSION") ?: "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.17.0")
  implementation("io.github.furstenheim:copy_down:1.1")
  testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
  testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.8.2")
}

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
  version.set("2023.1")
  type.set("IC") // Target IDE Platform
  pluginsRepositories {
    marketplace()
  }
  plugins.set(listOf("Kotlin", "java", "PythonCore:231.8109.144"))
}

tasks.register<RunIdeTask>("plainIdea") {
  maxHeapSize = "4G"
  jvmArgs = listOf(
    "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
    "--add-exports=java.desktop/sun.awt.windows=ALL-UNNAMED",
    "--add-exports=java.desktop/sun.awt.X11=ALL-UNNAMED",
  )
}

tasks {
  test {
    jvmArgs = listOf(
      "--add-opens=java.base/java.lang=ALL-UNNAMED",
      "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
      "--add-opens=java.base/java.io=ALL-UNNAMED",
      "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
      "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
      "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
      "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
      "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
      "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
      "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
      "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
      "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
      "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
      "--add-exports=java.desktop/sun.awt.X11=ALL-UNNAMED",
      "--add-exports=java.desktop/sun.awt.windows=ALL-UNNAMED",
      "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED",
      "--add-exports=java.desktop/sun.font=ALL-UNNAMED",
      "--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED",
      "--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED",
      "-Djdk.module.illegalAccess.silent=true"
    )

    useJUnitPlatform {
      includeEngines("junit-jupiter", "junit-vintage")
    }
  }

  getByName<RunIdeTask>("runIde") {
    maxHeapSize = "4G"
    args = listOf("lsp-server", "tcp", "8989")
    systemProperty("java.awt.headless", true)
  }

  // Set the JVM compatibility versions
  withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
  }

  patchPluginXml {
    version.set(System.getenv("IDEALS_VERSION"))
    sinceBuild.set("231")
    untilBuild.set("231.*")
  }

  signPlugin {
    certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
    privateKey.set(System.getenv("PRIVATE_KEY"))
    password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
  }

  publishPlugin {
    token.set(System.getenv("PUBLISH_TOKEN"))
  }
}
