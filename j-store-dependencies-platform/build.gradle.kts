plugins { `java-platform` }

repositories { mavenCentral() }

javaPlatform { allowDependencies() }

dependencies {
    api(platform(libs.spring.boot.dependencies))
    api(platform(libs.junit.bom))
    api(platform(libs.open.telemetry.bom))
    api(platform(libs.jackson.bom))
    api(platform(libs.netty.bom))
    api(platform(libs.log4j.bom))

    constraints {
        api(libs.postgresql)
        api(libs.commons.lang3)
    }
}
