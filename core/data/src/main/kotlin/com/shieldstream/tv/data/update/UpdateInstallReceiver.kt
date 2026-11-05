package com.shieldstream.tv.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.shieldstream.tv.model.tr

class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onFailure { onFailure?.invoke(PackageInstaller.STATUS_FAILURE, tr("No se pudo abrir el instalador.", "Couldn't open the installer.")) }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i("Update", "Actualización instalada")
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w("Update", "Instalación fallida ($status): $message")
                onFailure?.invoke(
                    status,
                    if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
                        tr("Instalación cancelada.", "Installation cancelled.")
                    } else {
                        tr("No se pudo instalar la actualización.", "Couldn't install the update.")
                    },
                )
            }
        }
    }

    companion object {
        @Volatile
        var onFailure: ((Int, String) -> Unit)? = null
    }
}
