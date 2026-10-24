package app.morphe.extension.tiktok.inbox;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.AlertDialog;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.SettingsContextRule;
import app.morphe.extension.tiktok.SignedInUser;
import app.morphe.extension.tiktok.settings.Settings;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class StreakRecipientsTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();
    @After public void clear() { SignedInUser.idForTests = null; }
    private void contact(Activity a, String self, String uid, String name, String handle) {
        var file = a.getDatabasePath("db_im_contact-" + self); file.getParentFile().mkdirs();
        try (var db = SQLiteDatabase.openOrCreateDatabase(file, null)) {
            db.execSQL("CREATE TABLE IF NOT EXISTS IM_USER_BASE_INFO (UID TEXT, NICK_NAME TEXT, UNIQUE_ID TEXT)");
            db.execSQL("INSERT INTO IM_USER_BASE_INFO VALUES (?, ?, ?)", new Object[]{uid,name,handle});
        }
    }
    @Test public void pickerIsAccountScopedDeduplicatedAndDoesNotSend() throws Exception {
        try (var owner = Robolectric.buildActivity(Activity.class).setup().visible()) {
            Activity a=owner.get(); Utils.setContext(a); SignedInUser.idForTests="100";
            contact(a,"100","200","Alice","alice"); contact(a,"100","200","Alice duplicate","ALICE");
            contact(a,"100","100","Self","self"); contact(a,"100","300","Invalid","bad/name");
            contact(a,"999","400","Other account","bob");
            var contacts = StreakMessenger.selectableContacts(a,"100");
            assertEquals(1,contacts.size()); assertEquals("alice",contacts.get(0)[0]);
            assertTrue(StreakMessenger.selectableContacts(a,"../999").isEmpty());
            Settings.AUTO_STREAK_RECIPIENT.save("");
            String state = Settings.AUTO_STREAK_STATE.savedValue();
            var row = new StreakRecipientsPreference(a); row.onClick();
            AlertDialog dialog=awaitDialog(); dialog.getListView().setItemChecked(0,true);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("alice",Settings.AUTO_STREAK_RECIPIENT.savedValue());
            assertEquals("Selecting a recipient must not dispatch a message",state,Settings.AUTO_STREAK_STATE.savedValue());
        }
    }
    @Test public void accountSwitchWhileChoosingDoesNotSave() throws Exception {
        try (var owner = Robolectric.buildActivity(Activity.class).setup().visible()) {
            Activity a=owner.get(); Utils.setContext(a); SignedInUser.idForTests="100";
            contact(a,"100","200","Alice","alice"); Settings.AUTO_STREAK_RECIPIENT.save("");
            new StreakRecipientsPreference(a).onClick(); AlertDialog dialog=awaitDialog();
            dialog.getListView().setItemChecked(0,true);
            SignedInUser.idForTests="999"; dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals("",Settings.AUTO_STREAK_RECIPIENT.savedValue());
        }
    }
    private AlertDialog awaitDialog() throws Exception {
        long until=System.currentTimeMillis()+5000;
        while(System.currentTimeMillis()<until) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            var d=ShadowAlertDialog.getLatestAlertDialog(); if(d!=null && d.isShowing())return d;
            Thread.sleep(10);
        }
        throw new AssertionError("Contact picker did not open");
    }
}
