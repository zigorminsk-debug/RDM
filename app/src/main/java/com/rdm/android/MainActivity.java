package com.rdm.android;

import android.content.ActivityNotFoundException;
import android.content.Context;
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
import android.widget.Button;
import android.widget.CheckBox;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class MainActivity extends AppCompatActivity {
    private static final int MENU_EDIT = 1;
    private static final int MENU_DELETE = 2;
    private static final long AVAILABILITY_CACHE_MS = 60_000L;

    private ConnectionRepository repository;
    private LinearLayout connectionList;
    private EditText searchField;
    private EditText quickHostField;
    private CheckBox quickMouseMode;
    private TextView countView;
    private View emptyState;
    private TextView emptyTitle;
    private TextView emptyMessage;
    private final Map<Long, AvailabilityState> availabilityStates = new HashMap<>();
    private final Map<Long, Long> availabilityCheckedAt = new HashMap<>();
    private final Set<Long> availabilityInFlight = new HashSet<>();
    private final Map<Long, TextView> availabilityLabels = new HashMap<>();

    private enum AvailabilityState { CHECKING, AVAILABLE, UNAVAILABLE }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        repository = new ConnectionRepository(this);

        connectionList = findViewById(R.id.connectionList);
        searchField = findViewById(R.id.searchConnections);
        quickHostField = findViewById(R.id.quickHost);
        quickMouseMode = findViewById(R.id.quickMouseMode);
        countView = findViewById(R.id.connectionCount);
        emptyState = findViewById(R.id.emptyState);
        emptyTitle = findViewById(R.id.emptyTitle);
        emptyMessage = findViewById(R.id.emptyMessage);

        findViewById(R.id.addConnection).setOnClickListener(view -> openEditor(0));
        findViewById(R.id.quickConnect).setOnClickListener(view -> quickConnect());
        findViewById(R.id.checkUpdates).setOnClickListener(
                view -> UpdateManager.checkForUpdate(this, true));
        TextView developerEmail = findViewById(R.id.developerEmail);
        developerEmail.setPaintFlags(developerEmail.getPaintFlags()
                | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        developerEmail.setOnClickListener(view -> openDeveloperEmail());
        TextView developerPhone = findViewById(R.id.developerPhone);
        developerPhone.setPaintFlags(developerPhone.getPaintFlags()
                | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        developerPhone.setOnClickListener(view -> openDeveloperPhone());
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

        UpdateManager.checkForUpdate(this, false);
    }

    @Override protected void onResume() {
        super.onResume();
        UpdateManager.installPendingIfAllowed(this);
        renderConnections();
    }

    private void renderConnections() {
        if (connectionList == null || repository == null) return;
        String filter = searchField.getText() == null ? "" : searchField.getText().toString();
        List<ConnectionProfile> all = repository.list("");
        List<ConnectionProfile> shown = repository.list(filter);

        TreeSet<String> companyNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ConnectionProfile profile : all) {
            String company = profile.company == null ? "" : profile.company.trim();
            if (!company.isEmpty()) companyNames.add(company);
        }
        String connectionCount = getResources().getQuantityString(
                R.plurals.saved_connection_count, all.size(), all.size());
        String companyCount = getResources().getQuantityString(
                R.plurals.company_group_count, companyNames.size(), companyNames.size());
        countView.setText(getString(R.string.connection_and_company_count,
                connectionCount, companyCount));

        connectionList.removeAllViews();
        availabilityLabels.clear();
        Map<String, List<ConnectionProfile>> grouped = new TreeMap<>((left, right) -> {
            if (left.isEmpty()) return right.isEmpty() ? 0 : 1;
            if (right.isEmpty()) return -1;
            return left.compareToIgnoreCase(right);
        });
        for (ConnectionProfile profile : shown) {
            String company = profile.company == null ? "" : profile.company.trim();
            grouped.computeIfAbsent(company, ignored -> new ArrayList<>()).add(profile);
        }

        boolean firstGroup = true;
        for (Map.Entry<String, List<ConnectionProfile>> group : grouped.entrySet()) {
            String title = group.getKey().isEmpty()
                    ? getString(R.string.company_not_set) : group.getKey();
            connectionList.addView(createCompanyHeader(title, firstGroup));
            firstGroup = false;
            for (ConnectionProfile profile : group.getValue()) {
                connectionList.addView(createConnectionCard(profile));
                requestAvailability(profile, false);
            }
        }

        boolean empty = shown.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            if (all.isEmpty()) {
                emptyTitle.setText(R.string.empty_title);
                emptyMessage.setText(R.string.empty_message);
            } else {
                emptyTitle.setText(R.string.empty_search_title);
                emptyMessage.setText(R.string.empty_search_message);
            }
        }
    }

    private View createCompanyHeader(String title, boolean first) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(getColor(R.color.ink));
        header.setTextSize(13);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        header.setPadding(dp(3), dp(2), dp(3), dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(first ? 5 : 14);
        params.bottomMargin = dp(5);
        header.setLayoutParams(params);
        return header;
    }

    private View createConnectionCard(ConnectionProfile profile) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(15), dp(15), dp(14));
        card.setBackgroundResource(R.drawable.bg_card);
        card.setElevation(dp(1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView deviceIcon = new TextView(this);
        deviceIcon.setGravity(Gravity.CENTER);
        deviceIcon.setText("▣");
        deviceIcon.setContentDescription(getString(R.string.remote_computer));
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
        favorite.setContentDescription(getString(profile.favorite
                ? R.string.remove_favourite : R.string.add_favourite));
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
        more.setContentDescription(getString(R.string.connection_options));
        more.setTextColor(getColor(R.color.muted));
        more.setTextSize(23);
        top.addView(more, new LinearLayout.LayoutParams(dp(30), dp(42)));
        more.setOnClickListener(view -> showProfileMenu(more, profile));

        LinearLayout availabilityRow = new LinearLayout(this);
        availabilityRow.setGravity(Gravity.CENTER_VERTICAL);
        availabilityRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams availabilityParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        availabilityParams.topMargin = dp(9);
        card.addView(availabilityRow, availabilityParams);

        TextView availability = new TextView(this);
        availability.setTextSize(11);
        availability.setSingleLine(true);
        availability.setEllipsize(TextUtils.TruncateAt.END);
        availabilityRow.addView(availability, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        availabilityLabels.put(profile.id, availability);
        bindAvailability(profile.id);

        TextView retry = new TextView(this);
        retry.setGravity(Gravity.CENTER);
        retry.setText("↻");
        retry.setTextColor(getColor(R.color.primary));
        retry.setTextSize(19);
        retry.setContentDescription(getString(R.string.check_server_availability));
        availabilityRow.addView(retry, new LinearLayout.LayoutParams(dp(38), dp(32)));
        retry.setOnClickListener(view -> requestAvailability(profile, true));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(7);
        card.addView(statusRow, statusParams);

        TextView lastConnected = new TextView(this);
        lastConnected.setText(profile.lastConnectedAt == 0
                ? getString(R.string.ready_to_connect)
                : getString(R.string.last_connected, DateUtils.getRelativeTimeSpanString(
                        profile.lastConnectedAt, System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS)));
        lastConnected.setTextColor(getColor(R.color.teal));
        lastConnected.setTextSize(11);
        lastConnected.setSingleLine(true);
        lastConnected.setEllipsize(TextUtils.TruncateAt.END);
        statusRow.addView(lastConnected, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

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
        connect.setText(R.string.connect_to_desktop);
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

    private void bindAvailability(long profileId) {
        TextView label = availabilityLabels.get(profileId);
        if (label == null) return;
        AvailabilityState state = availabilityStates.get(profileId);
        if (state == null || state == AvailabilityState.CHECKING) {
            label.setText(R.string.availability_checking);
            label.setTextColor(getColor(R.color.muted));
        } else if (state == AvailabilityState.AVAILABLE) {
            label.setText(R.string.availability_available);
            label.setTextColor(getColor(R.color.teal));
        } else {
            label.setText(R.string.availability_unavailable);
            label.setTextColor(getColor(R.color.danger));
        }
    }

    private void requestAvailability(ConnectionProfile profile, boolean force) {
        long profileId = profile.id;
        if (profileId <= 0 || availabilityInFlight.contains(profileId)) return;
        long now = System.currentTimeMillis();
        Long checkedAt = availabilityCheckedAt.get(profileId);
        if (!force && checkedAt != null && now - checkedAt < AVAILABILITY_CACHE_MS) return;

        availabilityInFlight.add(profileId);
        availabilityStates.put(profileId, AvailabilityState.CHECKING);
        bindAvailability(profileId);
        ServerAvailabilityChecker.check(profile.hostname, profile.port, available -> runOnUiThread(() -> {
            availabilityInFlight.remove(profileId);
            availabilityCheckedAt.put(profileId, System.currentTimeMillis());
            availabilityStates.put(profileId,
                    available ? AvailabilityState.AVAILABLE : AvailabilityState.UNAVAILABLE);
            if (!isFinishing() && !isDestroyed()) bindAvailability(profileId);
        }));
    }

    private void showProfileMenu(View anchor, ConnectionProfile profile) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, MENU_EDIT, 0, getString(R.string.edit_connection));
        menu.getMenu().add(0, MENU_DELETE, 1, getString(R.string.delete_connection));
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
                .setTitle(R.string.delete_connection_title)
                .setMessage(getString(R.string.delete_connection_message, profile.name))
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.delete_connection, (dialog, which) -> {
                    repository.delete(profile.id);
                    availabilityStates.remove(profile.id);
                    availabilityCheckedAt.remove(profile.id);
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
            temporary.mouseMode = quickMouseMode.isChecked();
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
                Toast.makeText(this, R.string.connection_no_longer_exists, Toast.LENGTH_SHORT).show();
                renderConnections();
                return;
            }
            repository.markConnected(profile.id);
            startFreeRdpSession(profile);
        } catch (GeneralSecurityException | java.io.IOException e) {
            Toast.makeText(this, R.string.saved_password_unlock_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void startFreeRdpSession(ConnectionProfile profile) {
        Uri uri = ConnectionUriFactory.build(profile);
        Intent session = new Intent(Intent.ACTION_VIEW, uri, this, SessionActivity.class);
        session.putExtra("com.rdm.android.MOUSE_MODE", profile.mouseMode);
        session.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try {
            startActivity(session);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.freerdp_session_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void openDeveloperEmail() {
        Intent email = new Intent(Intent.ACTION_SENDTO,
                Uri.fromParts("mailto", getString(R.string.developer_email), null));
        try {
            startActivity(email);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.email_app_missing, Toast.LENGTH_SHORT).show();
        }
    }

    private void openDeveloperPhone() {
        Intent dial = new Intent(Intent.ACTION_DIAL,
                Uri.fromParts("tel", getString(R.string.developer_phone), null));
        try {
            startActivity(dial);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.phone_app_missing, Toast.LENGTH_SHORT).show();
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
