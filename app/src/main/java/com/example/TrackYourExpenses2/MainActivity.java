package com.example.TrackYourExpenses2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Main entry point of the application.
 * Hosts the primary fragments and manages navigation and Firebase sync.
 */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retrieve app-wide shared preferences
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);

        // First-launch check: show login screen only once
        boolean firstLaunch = prefs.getBoolean("firstLaunch", true);

        if (firstLaunch) {
            prefs.edit().putBoolean("firstLaunch", false).apply();

            // Launch login prompt for Google Sign-In or guest access
            startActivity(new Intent(this, LoginPromptActivity.class));
            finish(); // Prevent back navigation to this screen
            return;
        }

        // Load main UI
        setContentView(R.layout.activity_main);

        // Initialize the bottom navigation view
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // Load HomeFragment by default on app start
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new HomeFragment())
                .commit();

        // Firebase synchronization (only for logged-in users)
        String userEmail = prefs.getString("user_email", null);

        if (userEmail != null) {
            FirebaseSyncHelper syncHelper = new FirebaseSyncHelper(this);

            // Sync data between Firebase and local database
            syncHelper.syncFirebaseToLocal();
            syncHelper.syncLocalToFirebase();

            // Also fetch user's monthly budget from the cloud
            FirebaseSyncHelper.syncBudgetFromFirebase(this);
        }

        // Set up listener to handle bottom nav item clicks
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment selectedFragment = null;

            // Determine which fragment to load based on selected tab
            if (id == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (id == R.id.nav_analytics) {
                selectedFragment = new AnalyticsFragment();
            } else if (id == R.id.nav_history) {
                selectedFragment = new HistoryFragment();
            } else if (id == R.id.nav_settings) {
                selectedFragment = new SettingsFragment();
            }

            // Replace current fragment with the selected one
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }

            return true; // Navigation handled
        });
    }
}
