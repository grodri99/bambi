package com.shieldstream.tv.ui.component

import android.content.Context
import android.content.Intent

fun restartApp(context: Context) {
    val pm = context.packageManager
    val intent = (pm.getLeanbackLaunchIntentForPackage(context.packageName) ?: pm.getLaunchIntentForPackage(context.packageName))
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ?: return
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
