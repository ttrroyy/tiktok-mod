package app.morphe.patches.tiktok.interaction.sharesheet

import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.addInstructions
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.*
import org.junit.Assert.*
import org.junit.Test

class SingleShareSheetPatchTest {
    @Test fun `native paths retain the original owner even when parameter registers are reused`() {
        for (registers in listOf(8, 32, 64)) {
            val method = fixture(registers)
            val owner = MutableClass(ImmutableClassDef(method.definingClass, AccessFlags.PUBLIC.value,
                "Ljava/lang/Object;", null, null, null, null, listOf(method)))
            val guarded = protectShareOpening(method, owner)
            assertEquals(registers + 4, guarded.implementation!!.registerCount)
            val code = guarded.implementation!!.instructions.toList()
            val begin = code[0] as RegisterRangeInstruction
            assertEquals(registers + 1, begin.startRegister)
            assertEquals("begin", reference(code[0]).name)
            assertEquals(Opcode.RETURN_OBJECT, code[4].opcode)
            assertEquals("duplicate early return must not clear the first request", 0,
                (code[4] as OneRegisterInstruction).registerA)
            // The admitted branch starts at the parameter-copy prefix, not the native body.
            assertEquals(Opcode.MOVE_OBJECT_FROM16, code[5].opcode)
            val copiedThis = code[5] as TwoRegisterInstruction
            assertEquals(registers - 4, copiedThis.registerA)
            assertEquals(registers, copiedThis.registerB)
            val entryOffset = code.take(5).sumOf { it.codeUnits }
            val branchAddress = code.take(2).sumOf { it.codeUnits }
            assertEquals(entryOffset, branchAddress + (code[2] as OffsetInstruction).codeOffset)
            val nativeBranch = code.indexOfFirst { it.opcode == Opcode.IF_EQZ }
            val nativeTarget = code.take(nativeBranch).sumOf { it.codeUnits } +
                (code[nativeBranch] as OffsetInstruction).codeOffset
            val target = code.indices.single { code.take(it).sumOf { instruction -> instruction.codeUnits } == nativeTarget }
            assertEquals("a native branch directly to return must still run the hook",
                Opcode.MOVE_OBJECT_FROM16, code[target].opcode)
            assertEquals(1, (code[target] as TwoRegisterInstruction).registerA)

            val hooks = code.indices.filter { code[it] is ReferenceInstruction &&
                (code[it] as ReferenceInstruction).reference is MethodReference && reference(code[it]).name == "completed" }
            assertEquals("both native returns, including null, release or record the sheet", 2, hooks.size)
            hooks.forEach { index ->
                val saveResult = code[index - 2] as TwoRegisterInstruction
                assertEquals(1, saveResult.registerA)
                val saveOwner = code[index - 1] as TwoRegisterInstruction
                assertEquals(0, saveOwner.registerA)
                assertEquals("p1 belongs to the preserved parameter frame", registers + 1, saveOwner.registerB)
                val call = code[index] as FiveRegisterInstruction
                assertEquals(listOf(0, 1), listOf(call.registerC, call.registerD))
                assertEquals(listOf("Landroid/app/Activity;", "Ljava/lang/Object;"), reference(code[index]).parameterTypes.map { it.toString() })
                assertEquals(Opcode.RETURN_OBJECT, code[index + 1].opcode)
                assertEquals(1, (code[index + 1] as OneRegisterInstruction).registerA)
            }
            assertEquals("one protected implementation replaces the native one", 1, owner.methods.size)
        }
    }

    private fun reference(instruction: Instruction) = (instruction as ReferenceInstruction).reference as MethodReference

    private fun fixture(registers: Int) = MutableMethod(ImmutableMethod("LX/ShareHost;", "show",
        listOf("Landroid/app/Activity;", "LX/ShareConfig;", "Landroid/os/Bundle;")
            .map { ImmutableMethodParameter(it, null, null) }, TUX_SHEET, AccessFlags.PUBLIC.value,
        null, null, ImmutableMethodImplementation(registers, emptyList(), null, null))).apply {
        addInstructions(0, """
            const/4 v1, 0x0
            if-eqz p1, :failed
            const/4 v0, 0x0
            move-object/from16 p1, v0
            return-object v0
            :failed
            return-object v1
        """)
    }
}
