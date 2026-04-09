pluginManagement {
    repositories {
        google()
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

rootProject.name = "CarAppStore"
include(":app")
include(":common")
include(":core")
include(":data")
include(":business")
include(":feature-home")
include(":feature-detail")
include(":feature-myapp")
include(":feature-search")
include(":feature-downloadmanager")
include(":feature-upgrade")
include(":feature-installcenter")
include(":feature-debug")
