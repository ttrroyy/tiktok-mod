/* Copyright 2026 Hushfeed contributors. https://github.com/SysAdminDoc/hushfeed */
package app.morphe.patches.tiktok.misc.inbox

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.util.classesCalling
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import app.morphe.util.addInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// Separate from named patches depending on Settings, to avoid a top-level initializer cycle.
internal val notificationFilterPatch = bytecodePatch {
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok())
    execute {
        val callers = classesCalling(listOf("Landroid/app/NotificationManager;"))
        val found = callers.mapNotNull(::classDefByOrNull)
        val targets = notifyingMethods(found)
        if (targets.none { (classDef, _) -> classDef.type == PUSH_HANDLER }) {
            throw PatchException("Notification filter: TikTok's push handler no longer posts a notification.")
        }
        targets.forEach { (classDef, method) ->
            mutableClassDefBy(classDef).findMutableMethodOf(method).postThroughSuggestedVideoFilter()
        }
        // Builder results also cover foreground/media notifications posted by Android's Service.
        var normalized = 0
        classesCalling(listOf("Landroid/app/Notification\$Builder;")).mapNotNull(::classDefByOrNull).forEach { cls ->
            cls.methods.forEach methodLoop@ { method ->
                val code = method.implementation?.instructions?.toList() ?: return@methodLoop
                val sites = code.withIndex().filter { (at, instruction) ->
                    val call = instruction.getReference<MethodReference>()
                    call?.definingClass == "Landroid/app/Notification\$Builder;" &&
                        call.name in setOf("build", "getNotification") && call.parameterTypes.isEmpty() &&
                        call.returnType == "Landroid/app/Notification;" &&
                        code.getOrNull(at + 1)?.opcode == Opcode.MOVE_RESULT_OBJECT
                }
                if (sites.isEmpty()) return@methodLoop
                val mutable = mutableClassDefBy(cls).findMutableMethodOf(method)
                sites.asReversed().forEach { (at, _) ->
                    val register = (code[at + 1] as OneRegisterInstruction).registerA
                    mutable.addInstruction(at + 2, "invoke-static/range { v$register .. v$register }, " +
                        "Lapp/morphe/extension/tiktok/inbox/SuggestedVideoPushBlock;->useSystemAppearance(Landroid/app/Notification;)V")
                    normalized++
                }
            }
        }
        check(normalized > 0) { "Notification filter: no native notification builder returns found" }
        println("[Notification appearance] Normalized $normalized native notification builder results.")
    }
}
