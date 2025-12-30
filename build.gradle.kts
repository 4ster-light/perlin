plugins {
    kotlin("jvm") version "2.2.21"
    application
}

group = "dev.aster"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
}

kotlin {
    jvmToolchain(24)
}


application {
    mainClass.set("dev.aster.MainKt")
}
