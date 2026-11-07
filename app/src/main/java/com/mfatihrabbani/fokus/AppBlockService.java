package com.mfatihrabbani.fokus;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/** Sends the user back to the home screen whenever a blocked app comes to the front. */
public class AppBlockService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !Prefs.apps(this).contains(pkg.toString())) return;

        performGlobalAction(GLOBAL_ACTION_HOME);
        Toast.makeText(this, "Aplikasi ini sedang diblokir. Tetap fokus!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onInterrupt() {}
}
