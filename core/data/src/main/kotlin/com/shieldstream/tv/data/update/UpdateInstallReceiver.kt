package com.shieldstream.tv.data.update

import com.shieldstream.tv.strings.R as StringsR
import com.shieldstream.tv.strings.ResourceTexts
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val texts = ResourceTexts(context)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onFailure { onFailure?.invoke(PackageInstaller.STATUS_FAILURE, texts.get(StringsR.string.data_couldn_t_open_the_installer)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i("Update", "Actualización instalada")
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w("Update", "Instalación fallida ($status): $message")
                onFailure?.invoke(
                    status,
                    if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
                        texts.get(StringsR.string.data_installation_cancelled)
                    } else {
                        texts.get(StringsR.string.data_couldn_t_install_the_update)
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
