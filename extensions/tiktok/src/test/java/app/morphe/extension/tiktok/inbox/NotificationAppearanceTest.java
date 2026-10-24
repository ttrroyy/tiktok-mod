package app.morphe.extension.tiktok.inbox;

import static org.junit.Assert.*;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class NotificationAppearanceTest {
    @Test public void systemColorsPreserveTheMessageReplyAndChannel() {
        var context=RuntimeEnvironment.getApplication();
        PendingIntent reply=PendingIntent.getBroadcast(context,1,new Intent("test.reply"),PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(context,"im_push")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Alice").setContentText("Hello")
                .setColor(Color.RED).setColorized(true).setContentIntent(reply)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_send,"Reply",reply).build()).build();
        Notification.Action[] actions=n.actions;
        SuggestedVideoPushBlock.useSystemAppearance(n);
        assertEquals(Notification.COLOR_DEFAULT,n.color); assertFalse(n.extras.getBoolean("android.colorized"));
        assertEquals("Alice",n.extras.getCharSequence(Notification.EXTRA_TITLE));
        assertEquals("Hello",n.extras.getCharSequence(Notification.EXTRA_TEXT));
        assertEquals("im_push",n.getChannelId()); assertSame(reply,n.contentIntent); assertSame(actions,n.actions);
        SuggestedVideoPushBlock.useSystemAppearance(null);
    }
}
