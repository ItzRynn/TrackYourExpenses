package com.example.TrackYourExpenses2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.identity.BeginSignInRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.auth.api.identity.SignInCredential;
import com.google.android.gms.common.api.ApiException;

/**
 * Activity that prompts the user to sign in with Google or skip login.
 * Uses Google One Tap sign-in and stores user email in SharedPreferences.
 */
public class LoginPromptActivity extends AppCompatActivity {

    // Google Sign-In components
    private SignInClient oneTapClient;             // One Tap client instance
    private BeginSignInRequest signInRequest;      // Configuration for sign-in request

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_prompt);

        // UI buttons
        Button btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        Button btnSkip = findViewById(R.id.btnSkip);

        // Initialize Google One Tap sign-in
        oneTapClient = Identity.getSignInClient(this);

        // Configure request for Google ID token
        signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(
                        BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                                .setSupported(true) // Enable ID token support
                                .setServerClientId(getString(R.string.default_web_client_id)) // From Google Cloud
                                .setFilterByAuthorizedAccounts(false) // Show all accounts
                                .build())
                .setAutoSelectEnabled(true) // Automatically select account if only one
                .build();

        // Handle Google Sign-In button click
        btnGoogleSignIn.setOnClickListener(v -> {
            oneTapClient.beginSignIn(signInRequest)
                    .addOnSuccessListener(result -> {
                        try {
                            // Launch the One Tap sign-in UI
                            startIntentSenderForResult(
                                    result.getPendingIntent().getIntentSender(),
                                    1001, null, 0, 0, 0, null);
                        } catch (Exception e) {
                            Toast.makeText(this, "Error launching sign-in", Toast.LENGTH_SHORT).show();
                            Log.e("LoginPrompt", "IntentSender error", e);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show();
                        Log.e("LoginPrompt", "Sign-In Error", e);
                    });
        });

        // Handle Skip button click — proceed as guest
        btnSkip.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)); // Navigate to main app
            finish(); // Close login prompt
        });
    }

    /**
     * Handles result from Google One Tap sign-in UI.
     * Saves user email and navigates to main screen.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1001 && data != null) {
            try {
                // Extract user credentials
                SignInCredential credential = oneTapClient.getSignInCredentialFromIntent(data);
                String email = credential.getId(); // Get user email

                Toast.makeText(this, "Signed in as: " + email, Toast.LENGTH_SHORT).show();

                // Save user email to SharedPreferences for session tracking
                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                prefs.edit().putString("user_email", email).apply();

                // Continue to main app
                startActivity(new Intent(this, MainActivity.class));
                finish();

            } catch (ApiException e) {
                Log.e("LoginPrompt", "Sign-In Failed", e);
                Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
