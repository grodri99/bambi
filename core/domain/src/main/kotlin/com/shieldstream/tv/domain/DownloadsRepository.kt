package com.shieldstream.tv.domain

import com.shieldstream.tv.model.Download
import com.shieldstream.tv.model.StorageOption
import kotlinx.coroutines.flow.StateFlow

sealed interface DownloadEnqueueResult {
    data object Started : DownloadEnqueueResult
    data object AlreadyDownloaded : DownloadEnqueueResult
    data object AlreadyRunning : DownloadEnqueueResult
    data object NoStorage : DownloadEnqueueResult
    data class NoSpace(val neededBytes: Long, val freeBytes: Long) : DownloadEnqueueResult
}

interface DownloadsRepository {
    val downloads: StateFlow<List<Download>>

    /** Carpeta elegida en Ajustes (o la primera disponible si la elegida ya no está, p. ej. un USB quitado). */
    val storagePath: StateFlow<String?>

    /** Memoria interna y USB/tarjetas montados ahora mismo, con su espacio libre. */
    fun storageOptions(): List<StorageOption>

    fun selectStorage(path: String)

    /**
     * Empieza a descargar [download] (url y metadatos ya resueltos) en la carpeta elegida. [download.filePath] solo
     * lleva el nombre del fichero; el repositorio decide la carpeta.
     */
    fun enqueue(download: Download, expectedBytes: Long): DownloadEnqueueResult

    fun find(id: String): Download?

    /** Fichero local reproducible de una descarga terminada, o null. */
    fun localFileFor(id: String): String?

    fun pause(id: String)
    fun resume(id: String)

    /** Cancela si está en marcha y borra el fichero. */
    fun delete(id: String)

    /** Uso interno del proceso de descarga. */
    fun update(id: String, transform: (Download) -> Download)
}
