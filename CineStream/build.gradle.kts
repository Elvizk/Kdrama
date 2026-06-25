import org.jetbrains.kotlin.konan.properties.Properties

version = 454
android {
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "SIMKL_API", "\"${properties.getProperty("SIMKL_API")}\"")
        buildConfigField("String", "TMDB_KEY", "\"${properties.getProperty("TMDB_KEY")}\"")
        buildConfigField("String", "CC_COOKIE", "\"${properties.getProperty("CC_COOKIE")}\"")
    }
}

cloudstream {
    name = "Kdrama v5"
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
