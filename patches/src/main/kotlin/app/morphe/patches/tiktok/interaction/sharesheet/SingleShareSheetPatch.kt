/* Copyright 2026 ttrroyy. https://github.com/ttrroyy/tiktok-mod */
package app.morphe.patches.tiktok.interaction.sharesheet

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.util.addInstruction
import app.morphe.util.addInstructions
import app.morphe.util.addInstructionsWithLabels
import app.morphe.util.cloneMutableAndPreserveParameters
import app.morphe.util.numberOfParameterRegisters
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val GUARD = "Lapp/morphe/extension/tiktok/share/SingleShareSheet;"
internal const val TUX_SHEET = "Lcom/bytedance/tux/sheet/sheet/TuxSheet;"
private const val ACTIVITY = "Landroid/app/Activity;"

internal object NativeShareOpeningFingerprint : Fingerprint(
    returnType = TUX_SHEET,
    strings = listOf("SKELETON_SHARE_FRAGMENT"),
    custom = { method, _ -> method.parameterTypes.size == 3 &&
        method.parameterTypes[0] == ACTIVITY && method.parameterTypes[2] == "Landroid/os/Bundle;" &&
        !AccessFlags.STATIC.isSet(method.accessFlags) },
)

internal fun protectShareOpening(method: Method, owner: MutableClass): MutableMethod {
    check(method.returnType == TUX_SHEET && method.parameterTypes.size == 3 && method.parameterTypes[0] == ACTIVITY)
    check(method.implementation!!.registerCount - method.numberOfParameterRegisters >= 2)
    // The native body reuses parameter registers. Keep a separate, untouched p1 for every return.
    val guarded = method.cloneMutableAndPreserveParameters(owner)
    val returns = guarded.implementation!!.instructions.withIndex()
        .filter { it.value.opcode == Opcode.RETURN_OBJECT }.map { it.index }
    check(returns.isNotEmpty())
    returns.asReversed().forEach { index ->
        val result = (guarded.getInstruction(index) as OneRegisterInstruction).registerA
        // Keep labels on the first hook instruction, including branches directly to a return.
        guarded.replaceInstruction(index, "move-object/from16 v1, v$result")
        guarded.addInstructions(index + 1, """
            move-object/from16 v0, p1
            invoke-static {v0, v1}, $GUARD->completed(${ACTIVITY}Ljava/lang/Object;)V
            return-object v1
        """)
    }
    // Insert last: a rejected duplicate must not run completed(null) and clear the first request.
    guarded.addInstructionsWithLabels(0, """
        invoke-static/range {p1 .. p1}, $GUARD->begin($ACTIVITY)Z
        move-result v0
        if-nez v0, :native_share_open
        const/4 v0, 0x0
        return-object v0
    """, ExternalLabel("native_share_open", guarded.getInstruction(0)))
    return guarded
}

@Suppress("unused")
val singleShareSheetPatch = bytecodePatch(
    name = "Prevent duplicate share sheets",
    description = "Keeps repeated Share presses from opening overlapping copies of the native share sheet.",
) {
    category("Interaction")
    dependsOn(sharedExtensionPatch)
    compatibleWith(*AppCompatibilities.tiktok())
    execute {
        val method = NativeShareOpeningFingerprint.method
        protectShareOpening(method, mutableClassDefBy(method.definingClass))
        val lifecycle = mutableClassDefBy("Landroidx/fragment/app/DialogFragment;")
            .methods.single { it.name == "onStart" && it.parameterTypes.isEmpty() && it.returnType == "V" }
        lifecycle.addInstruction(0, "invoke-static/range {p0 .. p0}, $GUARD->started(Ljava/lang/Object;)V")
    }
}
