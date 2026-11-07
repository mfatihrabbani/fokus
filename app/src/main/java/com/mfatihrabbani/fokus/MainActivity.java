package com.mfatihrabbani.fokus;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.VpnService;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQUEST_VPN = 1;

    private EditText domains;
    private Button websiteToggle;
    private Button accessibility;
    private LinearLayout apps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        domains = findViewById(R.id.domains);
        websiteToggle = findViewById(R.id.website_toggle);
        accessibility = findViewById(R.id.accessibility);
        apps = findViewById(R.id.apps);

        domains.setText(TextUtils.join("\n", sorted(Prefs.domains(this))));
        websiteToggle.setOnClickListener(v -> toggleWebsiteBlock());
        accessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        loadApps();

        // Resume the website block after a restart if it was on.
        if (Prefs.websiteOn(this) && VpnService.prepare(this) == null) {
            WebsiteBlockService.start(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        websiteToggle.setText(Prefs.websiteOn(this)
                ? "Matikan blokir website"
                : "Simpan & blokir website");
        accessibility.setText(appBlockEnabled()
                ? "Blokir aplikasi aktif ✓"
                : "Aktifkan blokir aplikasi (Aksesibilitas → Fokus)");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Prefs.saveDomains(this, domains.getText().toString());
    }

    private void toggleWebsiteBlock() {
        Prefs.saveDomains(this, domains.getText().toString());
        domains.setText(TextUtils.join("\n", sorted(Prefs.domains(this))));
        if (Prefs.websiteOn(this)) {
            Prefs.setWebsiteOn(this, false);
            WebsiteBlockService.stop(this);
            onResume();
            return;
        }
        Intent consent = VpnService.prepare(this);
        if (consent != null) {
            startActivityForResult(consent, REQUEST_VPN);
        } else {
            onActivityResult(REQUEST_VPN, RESULT_OK, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_VPN && resultCode == RESULT_OK) {
            Prefs.setWebsiteOn(this, true);
            WebsiteBlockService.start(this);
            onResume();
        }
    }

    private boolean appBlockEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String self = new ComponentName(this, AppBlockService.class).flattenToString();
        return enabled != null && enabled.contains(self);
    }

    /** Lists launchable apps with a checkbox each; checking one blocks it. */
    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> found = pm.queryIntentActivities(launcher, 0);
            List<String[]> rows = new ArrayList<>();
            for (ResolveInfo info : found) {
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                rows.add(new String[] {info.loadLabel(pm).toString(), pkg});
            }
            rows.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
            runOnUiThread(() -> showApps(rows));
        }).start();
    }

    private void showApps(List<String[]> rows) {
        Set<String> blocked = Prefs.apps(this);
        Set<String> shown = new HashSet<>();
        for (String[] row : rows) {
            String pkg = row[1];
            if (!shown.add(pkg)) continue; // one row per app
            CheckBox box = new CheckBox(this);
            box.setText(row[0]);
            box.setChecked(blocked.contains(pkg));
            box.setPadding(0, 16, 0, 16);
            box.setOnCheckedChangeListener((b, checked) -> Prefs.setAppBlocked(this, pkg, checked));
            apps.addView(box);
        }
    }

    private static List<String> sorted(Set<String> values) {
        List<String> list = new ArrayList<>(values);
        Collections.sort(list);
        return list;
    }
}
