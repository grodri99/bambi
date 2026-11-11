pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ShieldStream"

include(":app")
include(":core:model")
include(":core:strings")
include(":core:network")
include(":core:data")
include(":core:domain")
include(":core:media")
include(":core:ui")
include(":feature:home")
include(":feature:details")
include(":feature:player")
include(":feature:settings")
