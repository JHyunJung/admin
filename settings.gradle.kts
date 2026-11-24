// 폐쇄망 빌드: Gradle 속성 mavenRepoUrl 을 주면 플러그인을 그 저장소(Nexus 의 Maven Central 프록시)에서만 받는다.
// 속성이 없으면 지금처럼 Gradle Plugin Portal 을 쓴다. 라이브러리 쪽은 build.gradle.kts 의 repositories 가 같은 속성을 본다.
pluginManagement {
    val mavenRepoUrl = providers.gradleProperty("mavenRepoUrl").orNull
    if (mavenRepoUrl != null) {
        repositories {
            maven {
                url = uri(mavenRepoUrl)
                isAllowInsecureProtocol = mavenRepoUrl.startsWith("http://")
            }
        }
    }
}

rootProject.name = "fido-admin"
