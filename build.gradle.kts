import dev.kordex.gradle.plugins.kordex.DataCollection
import dev.kordex.i18n.files.YamlFormat

plugins {
	// https://github.com/jetbrains/kotlin/releases
	var kotlinVersion = "2.4.10"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion

	// https://plugins.gradle.org/plugin/org.springframework.boot
    id("org.springframework.boot") version "4.1.0"
	// https://plugins.gradle.org/plugin/io.spring.dependency-management
    id("io.spring.dependency-management") version "1.1.7"

	// https://github.com/Kord-Extensions/gradle-plugins/releases
	id("dev.kordex.gradle.kordex") version "1.9.2"
	id("dev.kordex.gradle.i18n") version "1.1.1"
}

group = "me.brightspark"
version = "0.3.0"

kotlin {
	jvmToolchain(21)
}

repositories {
    mavenCentral()
    maven {
        name = "Sonatype Snapshots"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0")

    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    runtimeOnly("com.h2database:h2")
    // https://mvnrepository.com/artifact/org.xerial/sqlite-jdbc
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    // https://mvnrepository.com/artifact/org.hibernate.orm/hibernate-community-dialects
    implementation("org.hibernate.orm:hibernate-community-dialects")

    // https://mvnrepository.com/artifact/com.github.ben-manes.caffeine/caffeine
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")
    // https://mvnrepository.com/artifact/io.github.oshai/kotlin-logging-jvm
    // Removed for now, as Kord includes a version already
//    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
}

kordEx {
	// Using 2.6.0 for now as it was the last version which can still be used comfortably with Spring Boot
	// See this support thread for example: https://discord.com/channels/1121419906995458098/1554223646892097586
	kordExVersion = "2.6.0-SNAPSHOT"
	bot {
		mainClass = "me.brightspark.mdcbot.MdcBotApplicationKt"
		voice = false
		dataCollection(DataCollection.Standard)
	}
}

i18n {
	defaults {
		fileFormat = YamlFormat
	}

	bundle("mdcbot.strings", "i18n")
}
