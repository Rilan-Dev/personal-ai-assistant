package dev.rilandev.personalaiassistant;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Synthetic WhatsApp-shaped AndroidTest activity. It runs in an isolated
 * instrumentation-package process, which cannot load the target APK's Kotlin
 * runtime. Java-only fixtures keep that process independent of the app under
 * test. No real messaging provider, recipient, or outbound request exists.
 */
public final class NexaChatListQaActivity extends Activity {
    public EditText messageInput;
    private boolean duplicateContact;
    private boolean overlappingContacts;
    private int chatRowClicks;
    private int sendClicks;
    private boolean opened;
    private String qaRunId = "";

    private LinearLayout base() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(22, 24, 22, 24);
        return root;
    }

    private TextView label(String value, float size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        return text;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        duplicateContact = getIntent().getBooleanExtra("duplicate", false);
        overlappingContacts = getIntent().getBooleanExtra("qa_overlap", false);
        String provided = getIntent().getStringExtra("qa_run");
        qaRunId = provided == null ? "" : provided;
        chatRowClicks = 0;
        sendClicks = 0;
        opened = false;
        showChatList();
    }

    private LinearLayout makeRow(String name) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(name);
        row.setOnClickListener(v -> {
            chatRowClicks++;
            opened = true;
            showConversation();
        });
        TextView title = label(name, 19f);
        title.setContentDescription(name);
        title.setPadding(0, 25, 0, 25);
        row.addView(title);
        return row;
    }

    private TextView status() {
        TextView status = label("QA run: " + qaRunId
            + " · Duplicate contacts: " + duplicateContact
            + " · Chat row clicks: " + chatRowClicks
            + " · Send clicks: " + sendClicks
            + " · Opened: " + opened, 14f);
        status.setContentDescription(status.getText());
        return status;
    }

    private void showChatList() {
        LinearLayout area = base();
        TextView header = label("WhatsApp - Synthetic QA only", 18f);
        header.setContentDescription("WhatsApp - Synthetic QA only");
        header.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        area.addView(header);
        area.addView(status());
        area.addView(label("Ask Meta AI or Search", 14f));

        if (duplicateContact && overlappingContacts) {
            // Two distinct native row identities with substantially overlapping
            // bounds; a geometric-only matcher would incorrectly merge them.
            FrameLayout layer = new FrameLayout(this);
            FrameLayout.LayoutParams first = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 140);
            layer.addView(makeRow("Mah Forever 🖤"), first);
            FrameLayout.LayoutParams second = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 140);
            second.topMargin = 55;
            layer.addView(makeRow("Mah Forever 🖤"), second);
            area.addView(layer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 195));
        } else {
            area.addView(makeRow("Mah Forever 🖤"));
            if (duplicateContact) area.addView(makeRow("Mah Forever 🖤"));
        }
        area.addView(label("Other contact", 14f));
        setContentView(area);
    }

    private void showConversation() {
        LinearLayout area = base();
        TextView marker = label("WhatsApp - Synthetic QA only", 14f);
        marker.setContentDescription("WhatsApp - Synthetic QA only");
        marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        area.addView(marker);
        area.addView(status());
        TextView header = label("Mah Forever 🖤", 19f);
        header.setContentDescription("Mah Forever 🖤");
        area.addView(header);

        messageInput = new EditText(this);
        messageInput.setHint("Type a message");
        messageInput.setContentDescription("Type a message");
        messageInput.setInputType(InputType.TYPE_CLASS_TEXT);
        area.addView(messageInput);

        Button send = new Button(this);
        send.setText("Send");
        send.setOnClickListener(v -> {
            sendClicks++;
            showConversation();
        });
        area.addView(send);
        setContentView(area);
    }
}
