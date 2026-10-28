import dev.kordex.gradle.plugins.kordex.DataCollection
import dev.kordex.i18n.files.YamlFormat

plugins {
	kotlin("jvm") version libs.versions.kotlin.asProvider()
	alias(libs.plugins.kordex)
	alias(libs.plugins.kordex.i18n)
}

group = "me.brightspark"
version = "1.0.0"

kotlin {
	jvmToolchain(21)
}

repositories {
    mavenCentral()
    maven {
        name = "Sonatype Snapshots"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
	maven {
		name = "SparkConfig"
		url = uri("https://gitlab.com/api/v4/projects/77382141/packages/maven")
	}
}

dependencies {
	implementation(libs.kotlin.stdlib)
	implementation(libs.kotlin.reflect)
	implementation(libs.kotlin.coroutines)
	implementation(libs.logback)
//	implementation(libs.caffeine)
	implementation(libs.exposed.core)
	implementation(libs.exposed.jdbc)
	implementation(libs.exposed.dao)
	implementation(libs.sqlite)
	implementation(libs.sparkconfig)
}

kordEx {
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
