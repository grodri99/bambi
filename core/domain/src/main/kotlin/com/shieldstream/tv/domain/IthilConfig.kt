package com.shieldstream.tv.domain

data class IthilConfig(
    val metadataUrl: String,
    val linkSecret: String,
    val archivePrefix: String,
) {
    val isConfigured: Boolean
        get() = metadataUrl.isNotBlank() && linkSecret.isNotBlank() && archivePrefix.isNotBlank()
}
