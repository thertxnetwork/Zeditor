pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")

        maven("https://repo.eclipse.org/content/groups/releases/")
        // Fallback mirrors for restricted network environments
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
    plugins {
        kotlin("jvm") version "2.2.20"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")

        maven("https://repo.eclipse.org/content/groups/releases/")
        // Fallback mirrors for restricted network environments
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "Zeditor"
include(":app")
include(":core:main")

include(":core:components")
include(":core:resources")
include(":core:extension")

val soraX = file("soraX")

if (!(soraX.exists() && soraX.listFiles()?.isNotEmpty() == true)) {
    throw GradleException(
        """
        The 'soraX' submodule is missing or empty.
        
        Please run:
        
            git submodule update --init --recursive
        """.trimIndent()
    )
}



include(":editor")
project(":editor").projectDir = file("soraX/editor")

include(":oniguruma-native")
project(":oniguruma-native").projectDir = file("soraX/oniguruma-native")

include(":editor-lsp")
project(":editor-lsp").projectDir = file("soraX/editor-lsp")

include(":language-textmate")
project(":language-textmate").projectDir = file("soraX/language-textmate")

include(":baselineprofile")
include(":benchmark")
include(":benchmark2")

// Termux terminal emulator libraries
val termuxLibs = file("termux-libs")
if (termuxLibs.exists()) {
    include(":terminal-emulator")
    project(":terminal-emulator").projectDir = file("termux-libs/terminal-emulator")
    
    include(":terminal-view")
    project(":terminal-view").projectDir = file("termux-libs/terminal-view")
}
