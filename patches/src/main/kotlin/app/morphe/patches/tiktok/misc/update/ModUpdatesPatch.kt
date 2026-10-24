/* Copyright 2026 ttrroyy. https://github.com/ttrroyy/tiktok-mod */
package app.morphe.patches.tiktok.misc.update

import app.morphe.patcher.patch.resourcePatch

@Suppress("unused")
val modUpdatesManifestPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { xml ->
            val manifest = xml.documentElement
            val permission = xml.createElement("uses-permission")
            permission.setAttribute("android:name", "android.permission.REQUEST_INSTALL_PACKAGES")
            manifest.appendChild(permission)
            val provider = xml.createElement("provider")
            provider.setAttribute("android:name", "app.morphe.extension.tiktok.update.ModUpdateProvider")
            provider.setAttribute("android:authorities", manifest.getAttribute("package") + ".modupdates")
            provider.setAttribute("android:exported", "false")
            provider.setAttribute("android:grantUriPermissions", "true")
            manifest.getElementsByTagName("application").item(0).appendChild(provider)
        }
    }
}
