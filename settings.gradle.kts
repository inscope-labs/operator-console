// operator-xconsole — Operator Console (Android) scaffold.
//
// The Cloudflare gateway that this app talks to is a SEPARATE project with its own
// repository, wrangler.toml and deploy gate. Nothing in this tree implements enforcement.
//
// See docs/architecture.md for the package → design-review section mapping.

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

rootProject.name = "operator-xconsole"

include(":app")
