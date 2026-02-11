plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("maven-publish")
    signing
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
        publishLibraryVariants("release")
    }

    js(IR) {
        browser()
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.tencent.kuikly-open:core:${Version.getKuiklyVersion()}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

group = "io.github.wwwcg"
version = "1.1.0"

// ==================== Maven Central Publishing ====================

// 为所有 Kotlin Multiplatform 的 publication 添加 javadoc JAR（Maven Central 要求）
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications.withType<MavenPublication> {
        artifact(javadocJar)

        pom {
            name.set("KuiklyWidgetGrid")
            description.set("A drag-and-sort widget grid component for KuiklyUI, similar to the iPhone widget screen.")
            url.set("https://github.com/wwwcg/KuiklyWidgetGrid")

            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }

            developers {
                developer {
                    id.set("wwwcg")
                    name.set("wwwcg")
                    url.set("https://github.com/wwwcg")
                }
            }

            scm {
                url.set("https://github.com/wwwcg/KuiklyWidgetGrid")
                connection.set("scm:git:git://github.com/wwwcg/KuiklyWidgetGrid.git")
                developerConnection.set("scm:git:ssh://git@github.com/wwwcg/KuiklyWidgetGrid.git")
            }
        }
    }

    repositories {
        maven {
            name = "LocalStaging"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

signing {
    useInMemoryPgpKeys(
        findProperty("signing.keyId") as String?,
        findProperty("signing.key") as String?,
        findProperty("signing.password") as String?
    )
    sign(publishing.publications)
}

// Workaround: KMP + signing 的 Gradle 任务依赖冲突
tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(tasks.withType<Sign>())
}

// ==================== Android ====================

android {
    namespace = "com.wwwcg.kuikly.widgetgrid"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
}
