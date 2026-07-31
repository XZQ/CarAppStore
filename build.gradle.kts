import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.security.KeyStore

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
}

fun productionConfigValue(name: String): String =
    (providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull).orEmpty()

fun validateHttpsUrl(name: String, value: String, baseUrl: Boolean): String? {
    val uri = runCatching { URI(value) }.getOrElse { return "$name is not a valid URL" }
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
        return "$name must be an HTTPS URL with a valid host"
    }
    if (uri.userInfo != null || uri.fragment != null) {
        return "$name must not contain user info or a fragment"
    }
    if (baseUrl && uri.query != null) {
        return "$name must not contain a query when used as the CDN base URL"
    }
    val host = uri.host.lowercase()
    val placeholderHosts = listOf(
        "localhost",
        "127.0.0.1",
        "10.0.2.2",
        "example.com",
        "example.org",
        "example.net",
    )
    val reservedSuffixes = listOf(".invalid", ".test", ".localhost")
    if (
        placeholderHosts.any { host == it || host.endsWith(".$it") } ||
        reservedSuffixes.any(host::endsWith) ||
        host.contains("your-company")
    ) {
        return "$name still points to a local or example host"
    }
    return null
}

fun validateReleaseKeyStore(
    storeFile: File,
    storePassword: String,
    keyAlias: String,
    keyPassword: String,
): String? {
    if (!storeFile.isFile || !storeFile.canRead()) {
        return "CARAPPSTORE_RELEASE_STORE_FILE does not exist or is not readable"
    }
    var keyStore: KeyStore? = null
    for (type in listOf("PKCS12", "JKS")) {
        val candidate = runCatching {
            KeyStore.getInstance(type).apply {
                FileInputStream(storeFile).use { input ->
                    load(input, storePassword.toCharArray())
                }
            }
        }.getOrNull()
        if (candidate != null) {
            keyStore = candidate
            break
        }
    }
    val loadedKeyStore = keyStore ?: return "Release keystore cannot be read with the configured store password"
    if (!loadedKeyStore.containsAlias(keyAlias)) {
        return "Release keystore does not contain the configured key alias"
    }
    val key = runCatching {
        loadedKeyStore.getKey(keyAlias, keyPassword.toCharArray())
    }.getOrNull()
    return if (key == null) "Release key password cannot unlock the configured alias" else null
}

val verifyProductionConfig = tasks.register("verifyProductionConfig") {
    group = "verification"
    description = "Validates production catalog, CDN, authentication, and release signing configuration."

    doLast {
        val requiredKeys = listOf(
            "CARAPPSTORE_CATALOG_PROD_URL",
            "CARAPPSTORE_DOWNLOAD_PROD_BASE_URL",
            "CARAPPSTORE_CATALOG_AUTH_HEADER",
            "CARAPPSTORE_CATALOG_AUTH_VALUE",
            "CARAPPSTORE_DOWNLOAD_AUTH_MODE",
            "CARAPPSTORE_RELEASE_STORE_FILE",
            "CARAPPSTORE_RELEASE_STORE_PASSWORD",
            "CARAPPSTORE_RELEASE_KEY_ALIAS",
            "CARAPPSTORE_RELEASE_KEY_PASSWORD",
        )
        val values = requiredKeys.associateWith(::productionConfigValue)
        val errors = mutableListOf<String>()
        requiredKeys.filter { values.getValue(it).isBlank() }.forEach { key ->
            errors += "$key is not configured"
        }

        values["CARAPPSTORE_CATALOG_PROD_URL"]
            ?.takeIf(String::isNotBlank)
            ?.trim()
            ?.let { validateHttpsUrl("CARAPPSTORE_CATALOG_PROD_URL", it, baseUrl = false) }
            ?.let(errors::add)
        values["CARAPPSTORE_DOWNLOAD_PROD_BASE_URL"]
            ?.takeIf(String::isNotBlank)
            ?.trim()
            ?.let { validateHttpsUrl("CARAPPSTORE_DOWNLOAD_PROD_BASE_URL", it, baseUrl = true) }
            ?.let(errors::add)

        val headerNamePattern = Regex("""^[!#$%&'*+.^_`|~0-9A-Za-z-]+$""")
        val catalogAuthHeader = values["CARAPPSTORE_CATALOG_AUTH_HEADER"].orEmpty().trim()
        val catalogAuthValue = values["CARAPPSTORE_CATALOG_AUTH_VALUE"].orEmpty().trim()
        if (catalogAuthHeader.isNotBlank() && !headerNamePattern.matches(catalogAuthHeader)) {
            errors += "CARAPPSTORE_CATALOG_AUTH_HEADER is not a valid HTTP header name"
        }
        if (catalogAuthValue.contains('\r') || catalogAuthValue.contains('\n')) {
            errors += "CARAPPSTORE_CATALOG_AUTH_VALUE must not contain line breaks"
        }

        val downloadAuthMode = values["CARAPPSTORE_DOWNLOAD_AUTH_MODE"].orEmpty().trim().uppercase()
        val downloadAuthHeader = productionConfigValue("CARAPPSTORE_DOWNLOAD_AUTH_HEADER").trim()
        val downloadAuthValue = productionConfigValue("CARAPPSTORE_DOWNLOAD_AUTH_VALUE").trim()
        when (downloadAuthMode) {
            "HEADER" -> {
                if (downloadAuthHeader.isBlank()) errors += "HEADER mode requires CARAPPSTORE_DOWNLOAD_AUTH_HEADER"
                if (downloadAuthValue.isBlank()) errors += "HEADER mode requires CARAPPSTORE_DOWNLOAD_AUTH_VALUE"
            }
            "SIGNED_URL" -> {
                if (downloadAuthHeader.isNotBlank() || downloadAuthValue.isNotBlank()) {
                    errors += "SIGNED_URL mode must not configure fixed CDN authentication headers"
                }
            }
            "" -> Unit
            else -> errors += "CARAPPSTORE_DOWNLOAD_AUTH_MODE must be HEADER or SIGNED_URL"
        }
        if (downloadAuthHeader.isNotBlank() && !headerNamePattern.matches(downloadAuthHeader)) {
            errors += "CARAPPSTORE_DOWNLOAD_AUTH_HEADER is not a valid HTTP header name"
        }
        if (downloadAuthValue.contains('\r') || downloadAuthValue.contains('\n')) {
            errors += "CARAPPSTORE_DOWNLOAD_AUTH_VALUE must not contain line breaks"
        }

        val placeholderValues = setOf("***", "change-me", "changeme", "placeholder")
        mapOf(
            "CARAPPSTORE_CATALOG_AUTH_VALUE" to catalogAuthValue,
            "CARAPPSTORE_DOWNLOAD_AUTH_VALUE" to downloadAuthValue,
        ).forEach { (name, value) ->
            if (value.lowercase() in placeholderValues) {
                errors += "$name is still a placeholder"
            }
        }

        val signingKeys = listOf(
            "CARAPPSTORE_RELEASE_STORE_FILE",
            "CARAPPSTORE_RELEASE_STORE_PASSWORD",
            "CARAPPSTORE_RELEASE_KEY_ALIAS",
            "CARAPPSTORE_RELEASE_KEY_PASSWORD",
        )
        if (signingKeys.all { values.getValue(it).isNotBlank() }) {
            validateReleaseKeyStore(
                storeFile = file(values.getValue("CARAPPSTORE_RELEASE_STORE_FILE")),
                storePassword = values.getValue("CARAPPSTORE_RELEASE_STORE_PASSWORD"),
                keyAlias = values.getValue("CARAPPSTORE_RELEASE_KEY_ALIAS"),
                keyPassword = values.getValue("CARAPPSTORE_RELEASE_KEY_PASSWORD"),
            )?.let(errors::add)
        }

        if (errors.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Production configuration validation failed:")
                    errors.distinct().forEach { appendLine("- $it") }
                }.trimEnd(),
            )
        }
        logger.lifecycle("Production configuration validation passed (values redacted).")
    }
}

tasks.register("assembleProductionRelease") {
    group = "build"
    description = "Validates production configuration, then builds the signed release APK."
    dependsOn(verifyProductionConfig, ":app:assembleRelease")
}

project(":app") {
    tasks.matching { it.name == "assembleRelease" }.configureEach {
        mustRunAfter(verifyProductionConfig)
    }
}
