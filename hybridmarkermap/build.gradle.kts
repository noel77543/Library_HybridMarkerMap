import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.maven.publish)
}

android {
    namespace = "com.noelsung.hybridmarkermap"
    // 避免與使用端的資源名稱衝突
    resourcePrefix = "hmm_"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // android-maps-utils 4.x requires API 23+
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Exposed as api: the public API uses GoogleMap / Marker / LatLng and ClusterItem
    api(libs.play.services.maps)
    api(libs.maps.utils)
    testImplementation(libs.junit)
}

// 發布至 Maven Central，帳號與簽章金鑰設定於 ~/.gradle/gradle.properties，見 README「發布」
mavenPublishing {
    configure(
        AndroidSingleVariantLibrary(
            javadocJar = JavadocJar.Javadoc(),
            sourcesJar = SourcesJar.Sources(),
            variant = "release",
        )
    )
    publishToMavenCentral()
    // 有設定簽章金鑰時才簽章，讓本機 publishToMavenLocal 不需要金鑰；Maven Central 會拒絕未簽章的發布
    if (providers.gradleProperty("signingInMemoryKey").isPresent || providers.gradleProperty("signing.keyId").isPresent) {
        signAllPublications()
    }

    coordinates("io.github.noel77543", "hybridmarkermap", "0.1.0")

    pom {
        name.set("HybridMarkerMap")
        description.set("Manage clusterable and viewport-culled standalone markers together on one Google Map, with unified click handling, selection highlight and built-in map styles.")
        inceptionYear.set("2026")
        url.set("https://github.com/noel77543/Library_HybridMarkerMap")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("noel77543")
                name.set("Noel Sung")
                url.set("https://github.com/noel77543")
            }
        }
        scm {
            url.set("https://github.com/noel77543/Library_HybridMarkerMap")
            connection.set("scm:git:git://github.com/noel77543/Library_HybridMarkerMap.git")
            developerConnection.set("scm:git:ssh://git@github.com/noel77543/Library_HybridMarkerMap.git")
        }
    }
}
