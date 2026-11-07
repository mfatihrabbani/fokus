package com.mfatihrabbani.fokus;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Stores the blocked websites and apps. */
final class Prefs {
    private static final String FILE = "fokus";
    private static final String DOMAINS = "domains";
    private static final String APPS = "apps";
    private static final String WEBSITE_ON = "website_on";

    private Prefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static Set<String> domains(Context context) {
        return new HashSet<>(prefs(context).getStringSet(DOMAINS, new HashSet<>()));
    }

    /** Saves one domain per line, accepting full URLs such as https://www.youtube.com/watch. */
    static void saveDomains(Context context, String text) {
        Set<String> domains = new HashSet<>();
        for (String line : text.split("[\\s,]+")) {
            String domain = line.trim().toLowerCase(Locale.ROOT)
                    .replaceFirst("^[a-z]+://", "")
                    .replaceFirst("[/:?#].*$", "")
                    .replaceFirst("^www\\.", "");
            if (!domain.isEmpty()) domains.add(domain);
        }
        prefs(context).edit().putStringSet(DOMAINS, domains).apply();
    }

    static Set<String> apps(Context context) {
        return new HashSet<>(prefs(context).getStringSet(APPS, new HashSet<>()));
    }

    static void setAppBlocked(Context context, String packageName, boolean blocked) {
        Set<String> apps = apps(context);
        if (blocked) apps.add(packageName); else apps.remove(packageName);
        prefs(context).edit().putStringSet(APPS, apps).apply();
    }

    static boolean websiteOn(Context context) {
        return prefs(context).getBoolean(WEBSITE_ON, false);
    }

    static void setWebsiteOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(WEBSITE_ON, on).apply();
    }
}
