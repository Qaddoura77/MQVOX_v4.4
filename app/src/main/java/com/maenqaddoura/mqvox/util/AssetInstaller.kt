package com.maenqaddoura.mqvox.util

import android.content.Context
import java.io.File

object AssetInstaller {
    fun installTree(context: Context, assetRoot: String, dest: File) {
        if (dest.exists() && File(dest, ".complete").isFile) return
        if (dest.exists()) dest.deleteRecursively()
        dest.mkdirs()
        copy(context, assetRoot, dest)
        File(dest, ".complete").writeText("MQVOX bundled asset installation complete\n")
    }
    private fun copy(context: Context, path: String, dest: File) {
        val children = context.assets.list(path) ?: emptyArray()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            context.assets.open(path).use { input -> dest.outputStream().use { input.copyTo(it, 1024 * 1024) } }
        } else {
            dest.mkdirs()
            for (name in children) copy(context, "$path/$name", File(dest, name))
        }
    }
}
