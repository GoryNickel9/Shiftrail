package it.turni.core

data class ReleaseUpdate(
    val versionCode: Int,
    val versionName: String,
    val sha256: String,
    val minSdk: Int,
) {
    init {
        require(versionCode > 0)
        require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-beta\\.[1-9][0-9]*)?").matches(versionName))
        require(Regex("[0-9a-f]{64}").matches(sha256))
        require(minSdk >= 26)
    }

    val tag
        get() = "v$versionName"

    val apkName
        get() = "Shiftrail-$versionName.apk"

    val isBeta
        get() = versionName.contains("-beta.")

    fun isCompatibleUpdate(installedVersion: Long, sdk: Int) =
        versionCode > installedVersion && minSdk <= sdk
}
