package com.rdm.android;

import android.os.Bundle;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.security.GeneralSecurityException;

public final class ConnectionEditorActivity extends AppCompatActivity {
    static final String EXTRA_PROFILE_ID = "com.rdm.android.PROFILE_ID";

    private ConnectionRepository repository;
    private ConnectionProfile profile;
    private EditText nameField;
    private EditText hostnameField;
    private EditText portField;
    private EditText usernameField;
    private EditText domainField;
    private EditText passwordField;
    private CheckBox favoriteField;
    private CheckBox rememberPasswordField;
    private TextView passwordVisibility;
    private TextView vaultNotice;
    private boolean passwordVisible;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_editor);
        repository = new ConnectionRepository(this);

        nameField = findViewById(R.id.nameField);
        hostnameField = findViewById(R.id.hostnameField);
        portField = findViewById(R.id.portField);
        usernameField = findViewById(R.id.usernameField);
        domainField = findViewById(R.id.domainField);
        passwordField = findViewById(R.id.passwordField);
        favoriteField = findViewById(R.id.favoriteField);
        rememberPasswordField = findViewById(R.id.rememberPasswordField);
        passwordVisibility = findViewById(R.id.passwordVisibility);
        vaultNotice = findViewById(R.id.vaultNotice);

        long id = getIntent().getLongExtra(EXTRA_PROFILE_ID, 0);
        if (id > 0) {
            loadExistingProfile(id);
            ((TextView) findViewById(R.id.editorTitle)).setText("Edit connection");
        } else {
            profile = new ConnectionProfile();
        }

        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        findViewById(R.id.saveConnection).setOnClickListener(view -> saveProfile());
        passwordVisibility.setOnClickListener(view -> togglePasswordVisibility());
        populateFields();
    }

    private void loadExistingProfile(long id) {
        try {
            profile = repository.get(id, true);
            if (profile == null) {
                Toast.makeText(this, "Connection not found.", Toast.LENGTH_SHORT).show();
                finish();
            }
        } catch (GeneralSecurityException | IOException e) {
            try {
                profile = repository.get(id, false);
                if (profile == null) {
                    finish();
                    return;
                }
                profile.password = "";
                profile.passwordSaved = false;
                vaultNotice.setText("The saved password could not be unlocked. Enter it again to save a new one.");
            } catch (GeneralSecurityException | IOException ignored) {
                Toast.makeText(this, "Could not read this connection.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void populateFields() {
        if (profile == null) return;
        nameField.setText(profile.name);
        hostnameField.setText(profile.hostname);
        portField.setText(String.valueOf(profile.port));
        usernameField.setText(profile.username);
        domainField.setText(profile.domain);
        passwordField.setText(profile.password);
        favoriteField.setChecked(profile.favorite);
        rememberPasswordField.setChecked(profile.passwordSaved);
    }

    private void togglePasswordVisibility() {
        int selection = passwordField.getSelectionStart();
        passwordVisible = !passwordVisible;
        passwordField.setInputType(passwordVisible
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordField.setTypeface(android.graphics.Typeface.DEFAULT);
        passwordField.setSelection(Math.max(0, Math.min(selection, passwordField.length())));
        passwordVisibility.setText(passwordVisible ? "Hide" : "Show");
        passwordVisibility.setContentDescription(passwordVisible ? "Hide password" : "Show password");
    }

    private void saveProfile() {
        String name = text(nameField).trim();
        if (name.isEmpty()) {
            nameField.setError("Give this connection a name.");
            nameField.requestFocus();
            return;
        }

        String host;
        try {
            host = ConnectionValidator.normalizeHost(text(hostnameField));
        } catch (IllegalArgumentException e) {
            hostnameField.setError(e.getMessage());
            hostnameField.requestFocus();
            return;
        }

        int port;
        try {
            port = ConnectionValidator.parsePort(text(portField));
        } catch (IllegalArgumentException e) {
            portField.setError(e.getMessage());
            portField.requestFocus();
            return;
        }

        profile.name = name;
        profile.hostname = host;
        profile.port = port;
        profile.username = text(usernameField).trim();
        profile.domain = text(domainField).trim();
        profile.password = text(passwordField);
        profile.favorite = favoriteField.isChecked();

        boolean remember = rememberPasswordField.isChecked() && !profile.password.isEmpty();
        if (rememberPasswordField.isChecked() && profile.password.isEmpty()) {
            Toast.makeText(this, "No password entered; FreeRDP will ask when you connect.",
                    Toast.LENGTH_SHORT).show();
        } else if (!rememberPasswordField.isChecked() && !profile.password.isEmpty()) {
            Toast.makeText(this, "Password not saved. FreeRDP will ask when you connect.",
                    Toast.LENGTH_SHORT).show();
        }

        try {
            repository.save(profile, remember);
            setResult(RESULT_OK);
            finish();
        } catch (GeneralSecurityException | IOException e) {
            Toast.makeText(this, "Could not securely save the password. Try again or leave it blank.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private static String text(EditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }
}
