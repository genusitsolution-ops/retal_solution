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
rootProject.name = "PRMSApp"
include(":domain", ":data", ":app")
// Rental Solution Admin (Super Admin only, not on Play) — separate app, own package id, talks to api/v1/admin
include(":admin")
