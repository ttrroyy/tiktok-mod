package app.morphe.extension.tiktok.download;

import static org.junit.Assert.*;
import android.app.Activity;
import android.widget.*;
import android.view.View;
import app.morphe.extension.tiktok.SettingsContextRule;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class PhotoVideoPickerTest {
    @Rule public final SettingsContextRule settings = new SettingsContextRule();
    static final class Fixture {
        final LinearLayout root;
        final RelativeLayout footer, row;
        final CheckBox selectAll, check;
        final TextView text;
        final Button download;
        Fixture(Activity a, Object post) {
            a.getApplicationInfo().flags |= android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL;
            root=new LinearLayout(a); root.setOrientation(LinearLayout.VERTICAL);
            footer=new RelativeLayout(a); selectAll=new CheckBox(a); selectAll.setId(View.generateViewId());
            footer.addView(selectAll); TextView all=new TextView(a); all.setText("Select all"); footer.addView(all);
            download=new Button(a); download.setText("Download"); footer.addView(download); root.addView(footer);
            row=new RelativeLayout(a); check=new CheckBox(a); check.setId(selectAll.getId()); row.addView(check);
            text=new TextView(a); RelativeLayout.LayoutParams label=new RelativeLayout.LayoutParams(-2,-2);
            label.addRule(RelativeLayout.END_OF,check.getId()); row.addView(text,label);
            PhotoVideoPicker.attach(post,selectAll,row,check,text);
        }
    }
    @Test public void checkboxIsAboveSelectAllAndDoesNotChangeNativeControls() {
        try(var owner=Robolectric.buildActivity(Activity.class).setup().visible()) {
            Object post=new Object(); Fixture ui=new Fixture(owner.get(),post);
            assertSame(ui.row,ui.root.getChildAt(0)); assertSame(ui.footer,ui.root.getChildAt(1));
            assertEquals("Save as video",ui.text.getText().toString()); assertFalse(ui.check.isChecked());
            assertSame(ui.footer,ui.download.getParent()); assertEquals(2,ui.row.getChildCount());
            RelativeLayout.LayoutParams label=(RelativeLayout.LayoutParams)ui.text.getLayoutParams();
            label.resolveLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            assertEquals(ui.check.getId(),label.getRule(RelativeLayout.RIGHT_OF));
            label.resolveLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            assertEquals(ui.check.getId(),label.getRule(RelativeLayout.LEFT_OF));
            ui.text.performClick(); assertTrue(ui.check.isChecked()); assertFalse(ui.selectAll.isChecked());
            ui.selectAll.setChecked(true); assertTrue(ui.check.isChecked()); ui.row.performClick(); assertFalse(ui.check.isChecked());
            PhotoVideoPicker.attach(post,ui.selectAll,new RelativeLayout(owner.get()),new CheckBox(owner.get()),new TextView(owner.get()));
            assertEquals("Rebinding cannot duplicate the checkbox",2,ui.root.getChildCount());
        }
    }
}
