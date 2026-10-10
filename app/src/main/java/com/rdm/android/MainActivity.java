package com.rdm.android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.freerdp.freerdpcore.presentation.SessionActivity;

import java.security.GeneralSecurityException;
import java.util.List;

public final class MainActivity extends AppCompatActivity {
    private static final int MENU_EDIT = 1;
    private static final int MENU_DELETE = 2;

    private ConnectionRepository repository;
    private LinearLayout connectionList;
    private EditText searchField;
    private EditText quickHostField;
    private TextView countView;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyMessage;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        repository = new ConnectionRepository(this);

        connectionList = findViewById(R.id.connectionList);
        searchField = findViewById(R.id.searchConnections);
        quickHostField = findViewById(R.id.quickHost);
        countView = findViewById(R.id.connectionCount);
        emptyState = findViewById(R.id.emptyState);
        emptyTitle = findViewById(R.id.emptyTitle);
        emptyMessage = findViewById(R.id.emptyMessage);

        findViewById(R.id.addConnection).setOnClickListener(view -> openEditor(0));
        findViewById(R.id.quickConnect).setOnClickListener(view -> quickConnect());
        quickHostField.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                quickConnect();
                return true;
            }
            return false;
        });
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderConnections();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        renderConnections();
    }

    private void renderConnections() {
        if (connectionList == null || repository == null) return;
        String filter = searchField.getText() == null ? "" : searchField.getText().toString();
        List<ConnectionProfile> all = repository.list("");
        List<ConnectionProfile> shown = repository.list(filter);

        countView.setText(all.size() == 1 ? "1 connection" : all.size() + " connections");
        connectionList.removeAllViews();
        for (ConnectionProfile profile : shown) {
            connectionList.addView(createConnectionCard(profile));
        }

        boolean empty = shown.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            if (all.isEmpty()) {
                emptyTitle.setText("Your workspace is ready");
                emptyMessage.setText("Add your first desktop to keep all your RDP connections in one place.");
            } else {
                emptyTitle.setText("No matching desktops");
                emptyMessage.setText("Try another name, host, or username.");
            }
        }
    }

    private View createConnectionCard(ConnectionProfile profile) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(15), dp(15), dp(14));
        card.setBackgroundResource(R.drawable.bg_card);
        card.setElevation(dp(1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView deviceIcon = new TextView(this);
        deviceIcon.setGravity(Gravity.CENTER);
        deviceIcon.setText("▣");
        deviceIcon.setTextColor(getColor(R.color.primary));
        deviceIcon.setTextSize(21);
        deviceIcon.setBackgroundResource(R.drawable.bg_icon_tile);
        top.addView(deviceIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(11), 0, dp(4), 0);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        top.addView(details, detailsParams);

        TextView name = new TextView(this);
        name.setText(profile.name);
        name.setTextColor(getColor(R.color.ink));
        name.setTextSize(15);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(name);

        TextView endpoint = new TextView(this);
        endpoint.setText(profile.endpoint());
        endpoint.setTextColor(getColor(R.color.muted));
        endpoint.setTextSize(12);
        endpoint.setSingleLine(true);
        endpoint.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams endpointParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        endpointParams.topMargin = dp(4);
        details.addView(endpoint, endpointParams);

        TextView favorite = new TextView(this);
        favorite.setGravity(Gravity.CENTER);
        favorite.setText(profile.favorite ? "★" : "☆");
        favorite.setContentDescription(profile.favorite ? "Remove from favourites" : "Add to favourites");
        favorite.setTextColor(getColor(profile.favorite ? R.color.primary : R.color.muted));
        favorite.setTextSize(22);
        top.addView(favorite, new LinearLayout.LayoutParams(dp(38), dp(42)));
        favorite.setOnClickListener(view -> {
            repository.setFavorite(profile.id, !profile.favorite);
            renderConnections();
        });

        TextView more = new TextView(this);
        more.setGravity(Gravity.CENTER);
        more.setText("⋮");
        more.setContentDescription("Connection options");
        more.setTextColor(getColor(R.color.muted));
        more.setTextSize(23);
        top.addView(more, new LinearLayout.LayoutParams(dp(30), dp(42)));
        more.setOnClickListener(view -> showProfileMenu(more, profile));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(13);
        card.addView(statusRow, statusParams);

        TextView status = new TextView(this);
        status.setText(profile.lastConnectedAt == 0
                ? "Ready to connect"
                : "Last launched " + DateUtils.getRelativeTimeSpanString(profile.lastConnectedAt,
                        System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        status.setTextColor(getColor(R.color.teal));
        status.setTextSize(11);
        statusRow.addView(status);

        Space spacer = new Space(this);
        statusRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView account = new TextView(this);
        account.setText(profile.accountLabel());
        account.setTextColor(getColor(R.color.muted));
        account.setTextSize(11);
        account.setSingleLine(true);
        account.setEllipsize(TextUtils.TruncateAt.END);
        account.setGravity(Gravity.END);
        statusRow.addView(account, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button connect = new Button(this);
        connect.setText("Connect to desktop");
        connect.setAllCaps(false);
        connect.setTextSize(13);
        connect.setTypeface(connect.getTypeface(), android.graphics.Typeface.BOLD);
        connect.setTextColor(getColor(R.color.surface));
        connect.setBackgroundResource(R.drawable.bg_primary_button);
        connect.setMinHeight(0);
        connect.setMinimumHeight(0);
        connect.setMinWidth(0);
        connect.setMinimumWidth(0);
        connect.setStateListAnimator(null);
        connect.setPadding(dp(12), 0, dp(12), 0);
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        connectParams.topMargin = dp(13);
        card.addView(connect, connectParams);
        connect.setOnClickListener(view -> connectProfile(profile));
        return card;
    }

    private void showProfileMenu(View anchor, ConnectionProfile profile) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, MENU_EDIT, 0, "Edit connection");
        menu.getMenu().add(0, MENU_DELETE, 1, "Delete connection");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_EDIT) {
                openEditor(profile.id);
                return true;
            }
            if (item.getItemId() == MENU_DELETE) {
                confirmDelete(profile);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void confirmDelete(ConnectionProfile profile) {
        new AlertDialog.Builder(this)
                .setTitle("Delete connection?")
                .setMessage("“" + profile.name + "” will be removed from this device.")
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.delete(profile.id);
                    renderConnections();
                })
                .show();
    }

    private void openEditor(long profileId) {
        Intent intent = new Intent(this, ConnectionEditorActivity.class);
        if (profileId > 0) intent.putExtra(ConnectionEditorActivity.EXTRA_PROFILE_ID, profileId);
        startActivity(intent);
    }

    private void quickConnect() {
        String input = quickHostField.getText() == null ? "" : quickHostField.getText().toString();
        try {
            ConnectionValidator.HostAndPort endpoint = ConnectionValidator.parseQuickConnect(input);
            ConnectionProfile temporary = new ConnectionProfile();
            temporary.name = endpoint.host;
            temporary.hostname = endpoint.host;
            temporary.port = endpoint.port;
            hideKeyboard(quickHostField);
            startFreeRdpSession(temporary);
            quickHostField.setError(null);
        } catch (IllegalArgumentException e) {
            quickHostField.setError(e.getMessage());
            quickHostField.requestFocus();
        }
    }

    private void connectProfile(ConnectionProfile summary) {
        try {
            ConnectionProfile profile = repository.get(summary.id, true);
            if (profile == null) {
                Toast.makeText(this, "This connection no longer exists.", Toast.LENGTH_SHORT).show();
                renderConnections();
                return;
            }
            repository.markConnected(profile.id);
            startFreeRdpSession(profile);
        } catch (GeneralSecurityException | java.io.IOException e) {
            Toast.makeText(this,
                    "Could not unlock the saved password. Edit the connection and enter it again.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void startFreeRdpSession(ConnectionProfile profile) {
        Uri uri = ConnectionUriFactory.build(profile);
        Intent session = new Intent(Intent.ACTION_VIEW, uri, this, SessionActivity.class);
        session.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try {
            startActivity(session);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Could not open the FreeRDP session.", Toast.LENGTH_LONG).show();
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
