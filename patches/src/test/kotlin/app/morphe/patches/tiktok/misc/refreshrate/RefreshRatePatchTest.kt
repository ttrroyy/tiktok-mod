package app.morphe.patches.tiktok.misc.refreshrate

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshRatePatchTest {
    private fun writer(owner: String, field: String = "preferredRefreshRate") = ImmutableMethod(
        owner, "write", emptyList(), "V", AccessFlags.PUBLIC.value, null, null,
        ImmutableMethodImplementation(2, listOf(
            ImmutableInstruction22c(Opcode.IPUT, 0, 1,
                ImmutableFieldReference("Landroid/view/WindowManager\$LayoutParams;", field, "F")),
            ImmutableInstruction10x(Opcode.RETURN_VOID),
        ), null, null),
    )

    @Test fun `native window requests are selected`() {
        assertTrue(writer("Lcom/ss/android/ugc/aweme/feed/controller/PlayerController;").writesHostRefreshRate())
        assertTrue(writer("LX/0Ier;").writesHostRefreshRate())
        assertFalse(writer("LX/0Ier;", "screenBrightness").writesHostRefreshRate())
    }

    @Test fun `extension store is never intercepted recursively`() {
        assertFalse(writer("Lapp/morphe/extension/tiktok/misc/RefreshRate;").writesHostRefreshRate())
        assertFalse(writer("Lapp/morphe/extension/shared/WindowHelper;").writesHostRefreshRate())
    }
}
