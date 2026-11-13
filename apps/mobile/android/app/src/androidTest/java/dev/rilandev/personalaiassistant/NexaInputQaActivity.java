package dev.rilandev.personalaiassistant;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Synthetic input/icon AndroidTest activity. Java-only because this test-only
 * package runs a separate process without the target APK's Kotlin classpath.
 * No real user contacts, credentials, messaging or external app access.
 */
public final class NexaInputQaActivity extends Activity {
    private TextView status;
    private String qaRunId = "";
    public EditText searchField;
    public EditText notesField;
    public EditText secretField;
    public static volatile int searchClicks = 0;
    public static volatile int sendClicks = 0;
    public static volatile int unknownClicks = 0;

    private void renderStatus() {
        String value = "QA run: " + qaRunId
            + " · Search clicks: " + searchClicks
            + " · Send clicks: " + sendClicks
            + " · Unknown clicks: " + unknownClicks
            + " · Search text: " + searchField.getText();
        status.setText(value);
        status.setContentDescription(value);
    }

    private EditText field(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setContentDescription(hint);
        input.setId(View.generateViewId());
        input.setInputType(inputType);
        return input;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String provided = getIntent().getStringExtra("qa_run");
        qaRunId = provided == null ? "" : provided;
        searchClicks = 0;
        sendClicks = 0;
        unknownClicks = 0;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(28, 24, 28, 24);
        TextView title = new TextView(this);
        title.setText("Nexa synthetic input and icon fixture");
        title.setTextSize(22f);
        container.addView(title);
        status = new TextView(this);
        container.addView(status);

        ImageButton searchIcon = new ImageButton(this);
        searchIcon.setContentDescription("Search");
        searchIcon.setImageResource(android.R.drawable.ic_menu_search);
        searchIcon.setOnClickListener(v -> {
            searchClicks++;
            renderStatus();
        });
        container.addView(searchIcon, new LinearLayout.LayoutParams(90,90));

        ImageButton unknownIcon = new ImageButton(this);
        unknownIcon.setContentDescription("Unknown widget");
        unknownIcon.setImageResource(android.R.drawable.ic_menu_help);
        unknownIcon.setOnClickListener(v -> {
            unknownClicks++;
            renderStatus();
        });
        container.addView(unknownIcon, new LinearLayout.LayoutParams(90,90));

        searchField = field("Search", InputType.TYPE_CLASS_TEXT);
        container.addView(searchField);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) { renderStatus(); }
        });

        notesField = field("Notes", InputType.TYPE_CLASS_TEXT);
        container.addView(notesField);
        secretField = field("Password",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        container.addView(secretField);

        Button send = new Button(this);
        send.setText("Send");
        send.setContentDescription("Send");
        send.setOnClickListener(v -> {
            sendClicks++;
            renderStatus();
        });
        container.addView(send);

        renderStatus();
        ScrollView scroll = new ScrollView(this);
        scroll.addView(container);
        setContentView(scroll);
    }
}
