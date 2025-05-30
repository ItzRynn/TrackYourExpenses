package com.example.TrackYourExpenses2;

import android.app.AlertDialog;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.text.InputType;

import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Collections;

/**
 * Fragment that provides app settings like login/logout,
 * budget configuration, and help info.
 */
public class SettingsFragment extends Fragment {

    private static final String PREF_BUDGET = "monthly_budget";

    private SignInClient oneTapClient;
    private BeginSignInRequest signInRequest;
    private SharedPreferences prefs;

    private TextView emailText;
    private Button btnSetBudget, btnHelp, btnLogin, btnLogout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // UI references
        emailText = view.findViewById(R.id.emailText);
        btnSetBudget = view.findViewById(R.id.btnSetBudget);
        btnHelp = view.findViewById(R.id.btnHelp);
        btnLogin = view.findViewById(R.id.btnLogin);
        btnLogout = view.findViewById(R.id.btnLogout);

        // Get shared prefs for app and determine if user is logged in
        SharedPreferences basePrefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String userEmail = basePrefs.getString("user_email", null);
        String prefsName = userEmail != null ? "BudgetPrefs_" + userEmail : "GuestPrefs";
        prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE);

        // Setup Google One Tap Sign-In client
        oneTapClient = Identity.getSignInClient(requireContext());
        signInRequest = BeginSignInRequest.builder()
                .setGoogleIdTokenRequestOptions(
                        BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                                .setSupported(true)
                                .setServerClientId(getString(R.string.default_web_client_id))
                                .setFilterByAuthorizedAccounts(false)
                                .build())
                .setAutoSelectEnabled(true)
                .build();

        // Show user status and appropriate buttons
        if (userEmail != null) {
            emailText.setText("Signed in as:\n" + userEmail);
            btnLogin.setVisibility(View.GONE);
            btnLogout.setVisibility(View.VISIBLE);
        } else {
            emailText.setText("Not signed in");
            btnLogin.setVisibility(View.VISIBLE);
            btnLogout.setVisibility(View.GONE);
        }

        // Button handlers
        btnSetBudget.setOnClickListener(v -> showBudgetDialog());
        btnHelp.setOnClickListener(v -> showHelpDialog());

        // Google Sign-In button action
        btnLogin.setOnClickListener(v -> {
            oneTapClient.beginSignIn(signInRequest)
                    .addOnSuccessListener(result -> {
                        try {
                            startIntentSenderForResult(
                                    result.getPendingIntent().getIntentSender(),
                                    1001, null, 0, 0, 0, null);
                        } catch (Exception e) {
                            Toast.makeText(getContext(), "Login failed", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Login failed", Toast.LENGTH_SHORT).show();
                    });
        });

        // Logout: clear stored email and refresh app
        btnLogout.setOnClickListener(v -> {
            basePrefs.edit().remove("user_email").apply();
            Toast.makeText(getContext(), "Logged out", Toast.LENGTH_SHORT).show();
            requireActivity().recreate();
        });

        return view;
    }

    /**
     * Shows dialog allowing the user to set/update their monthly budget.
     */
    private void showBudgetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Set Monthly Budget");

        final EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("e.g. 1000.00");

        float savedBudget = prefs.getFloat(PREF_BUDGET, 1000f);
        input.setText(String.valueOf(savedBudget));
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String text = input.getText().toString();
            if (!text.isEmpty()) {
                try {
                    float budget = Float.parseFloat(text);
                    prefs.edit().putFloat(PREF_BUDGET, budget).apply();
                    Toast.makeText(getContext(), "Budget saved: $" + budget, Toast.LENGTH_SHORT).show();

                    // Upload to Firestore if user is signed in
                    SharedPreferences basePrefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
                    String userEmail = basePrefs.getString("user_email", null);

                    if (userEmail != null) {
                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(userEmail)
                                .collection("profile")
                                .document("budget")
                                .set(Collections.singletonMap("monthly_budget", budget));
                    }

                } catch (NumberFormatException e) {
                    Toast.makeText(getContext(), "Invalid input", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "Input cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    /**
     * Displays a basic help message dialog.
     */
    private void showHelpDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle("Help")
                .setMessage("This app helps you track expenses, view analytics, and stay within your monthly budget.")
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Handles Google Sign-In result and updates UI and preferences accordingly.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1001 && data != null) {
            try {
                // Retrieve signed-in user's email
                SignInCredential credential = oneTapClient.getSignInCredentialFromIntent(data);
                String email = credential.getId();

                // Store email in shared prefs
                SharedPreferences basePrefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
                basePrefs.edit().putString("user_email", email).apply();

                Toast.makeText(getContext(), "Signed in as: " + email, Toast.LENGTH_SHORT).show();

                // Sync budget and expenses from Firebase
                FirebaseSyncHelper.syncBudgetFromFirebase(requireContext());
                FirebaseSyncHelper helper = new FirebaseSyncHelper(requireContext());
                helper.syncFirebaseToLocal();

                // Reload UI with new login state
                requireActivity().recreate();

            } catch (ApiException e) {
                Toast.makeText(getContext(), "Google Sign-In failed", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
