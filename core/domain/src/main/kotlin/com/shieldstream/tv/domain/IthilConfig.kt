package com.shieldstream.tv.domain

data class IthilConfig(
    val metadataUrl: String,
    val indexKey: String,
    val archivePrefix: String,
) {
    val isConfigured: Boolean
        get() = metadataUrl.isNotBlank() && indexKey.isNotBlank() && archivePrefix.isNotBlank()
}
