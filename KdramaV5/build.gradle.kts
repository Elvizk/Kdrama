import org.jetbrains.kotlin.konan.properties.Properties
import java.io.FileInputStream

version = 476

cloudstream {
    language = "en"
    description = "Korean and Chinese Drama streaming plugin for CloudStream"
    authors = listOf("Elvizk")
    status = 1
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "AsianDrama"
    )

    iconUrl = "https://github.com/SaurabhKaperwan/CSX/raw/refs/heads/master/CineStream/icon.png"
}

android {
    buildFeatures.buildConfig = true
    defaultConfig {
        try {
            val properties = Properties()
            properties.load(FileInputStream(project.rootProject.file("local.properties")))
            buildConfigField("String", "TMDB_KEY", "\"${properties.getProperty("TMDB_KEY", "")}\"")
            buildConfigField("String", "TRAKT_CLIENT_ID", "\"${properties.getProperty("TRAKT_CLIENT_ID", "")}\"")
            buildConfigField("String", "CC_COOKIE", "\"${properties.getProperty("CC_COOKIE", "")}\"")
        } catch (_: Exception) {
            buildConfigField("String", "TMDB_KEY", "\"\"")
            buildConfigField("String", "TRAKT_CLIENT_ID", "\"\"")
            buildConfigField("String", "CC_COOKIE", "\"\"")
        }
    }
}
