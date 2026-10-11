package com.rdm.android;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

/** Initializes the native FreeRDP runtime and keeps the app and session interface in Russian. */
public final class RdmApplication extends com.freerdp.freerdpcore.application.GlobalApp {
    @Override public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru"));
    }
}
