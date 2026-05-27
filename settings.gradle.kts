pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri("https://androidsdk.insta360.com/repository/maven-public/")
            isAllowInsecureProtocol = true
            credentials {
                username = providers.gradleProperty("INSTA360_MAVEN_USER")
                    .orElse(providers.environmentVariable("INSTA360_MAVEN_USER"))
                    .orNull
                password = providers.gradleProperty("INSTA360_MAVEN_PASSWORD")
                    .orElse(providers.environmentVariable("INSTA360_MAVEN_PASSWORD"))
                    .orNull
            }
        }
    }
}

rootProject.name = "OmniEye"
include(":app")
