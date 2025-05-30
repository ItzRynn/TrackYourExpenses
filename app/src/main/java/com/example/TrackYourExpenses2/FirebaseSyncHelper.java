package com.example.TrackYourExpenses2;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.firestore.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class responsible for synchronizing expense data between local SQLite database and Firebase Firestore.
 * Handles uploads, updates, and downloads of user expenses, as well as syncing budget preferences.
 */
public class FirebaseSyncHelper {

    private final FirebaseFirestore firestore;      // Reference to Firestore instance
    private final ExpenseDatabase localDb;          // Local database instance
    private final Context context;                  // Application context
    private final String userEmail;                 // Logged-in user's email from SharedPreferences

    public FirebaseSyncHelper(Context context) {
        this.context = context;
        this.firestore = FirebaseFirestore.getInstance();
        this.localDb = new ExpenseDatabase(context);

        // Get user email from SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        this.userEmail = prefs.getString("user_email", null);
    }

    // Helper method to get Firestore path: users/{userEmail}/expenses
    private CollectionReference getUserExpenseRef() {
        return firestore.collection("users")
                .document(userEmail)
                .collection("expenses");
    }

    /**
     * Uploads all local expenses to Firebase Firestore.
     * Used to initialize cloud data from local storage.
     */
    public void syncLocalToFirebase() {
        if (userEmail == null) return;

        List<Expense> localExpenses = localDb.getAllExpenses();
        for (Expense expense : localExpenses) {
            uploadExpense(expense);
        }
    }

    /**
     * Uploads a single expense object to Firestore.
     * Uses a generated doc ID to uniquely identify each expense.
     */
    public void uploadExpense(Expense expense) {
        if (userEmail == null) return;

        String docId = generateDocId(expense); // Unique ID per expense
        Map<String, Object> data = toFirestoreMap(expense); // Map data for Firestore

        getUserExpenseRef().document(docId).set(data)
                .addOnSuccessListener(unused -> Log.d("FirebaseSync", "Uploaded: " + docId))
                .addOnFailureListener(e -> Log.e("FirebaseSync", "Upload failed: " + docId, e));
    }

    /**
     * Updates an existing expense in Firestore. If the identifying fields changed, delete the old doc.
     */
    public void updateExpenseInFirebase(Expense oldExpense, Expense newExpense) {
        if (userEmail == null) return;

        String oldDocId = generateDocId(oldExpense);
        String newDocId = generateDocId(newExpense);
        CollectionReference expenseRef = getUserExpenseRef();

        if (!oldDocId.equals(newDocId)) {
            // If the ID has changed (due to title/date/amount change), delete the old one
            expenseRef.document(oldDocId).delete()
                    .addOnSuccessListener(unused -> Log.d("FirebaseSync", "Deleted old doc: " + oldDocId))
                    .addOnFailureListener(e -> Log.e("FirebaseSync", "Failed to delete old doc: " + oldDocId, e));
        }

        // Upload the new/updated expense
        Map<String, Object> data = toFirestoreMap(newExpense);
        expenseRef.document(newDocId).set(data)
                .addOnSuccessListener(unused -> Log.d("FirebaseSync", "Updated: " + newDocId))
                .addOnFailureListener(e -> Log.e("FirebaseSync", "Update failed: " + newDocId, e));
    }

    /**
     * Downloads all expenses from Firebase and inserts them into the local database if not already present.
     */
    public void syncFirebaseToLocal() {
        if (userEmail == null) return;

        CollectionReference expenseRef = getUserExpenseRef();
        expenseRef.get().addOnSuccessListener(querySnapshot -> {
            List<Expense> localExpenses = localDb.getAllExpenses();

            for (QueryDocumentSnapshot doc : querySnapshot) {
                // Extract fields from Firestore document
                String title = doc.getString("title");
                Double amount = doc.getDouble("amount");
                String date = doc.getString("date");
                String category = doc.getString("category");
                String imageUrl = doc.getString("imageUrl");

                if (title == null || amount == null || date == null || category == null) continue;

                Expense remoteExpense = new Expense(title, amount, date, category, imageUrl);

                // Check if this expense already exists locally
                boolean exists = false;
                for (Expense local : localExpenses) {
                    if (expensesEqual(remoteExpense, local)) {
                        exists = true;
                        break;
                    }
                }

                // Insert if it's not already in the local DB
                if (!exists) {
                    localDb.insertExpense(title, amount, date, category, imageUrl);
                    Log.d("FirebaseSync", "Inserted: " + title);
                } else {
                    Log.d("FirebaseSync", "Duplicate skipped: " + title);
                }
            }
        }).addOnFailureListener(e -> Log.e("FirebaseSync", "Download error", e));
    }

    // Compares two expenses based on title, amount, date, and category
    private boolean expensesEqual(Expense a, Expense b) {
        return a.getTitle().equals(b.getTitle())
                && a.getAmount() == b.getAmount()
                && a.getDate().equals(b.getDate())
                && a.getCategory().equals(b.getCategory());
    }

    // Converts an Expense object into a Firestore-compatible map
    private Map<String, Object> toFirestoreMap(Expense e) {
        Map<String, Object> data = new HashMap<>();
        data.put("title", e.getTitle());
        data.put("amount", e.getAmount());
        data.put("date", e.getDate());
        data.put("category", e.getCategory());
        data.put("imageUrl", e.getImageUrl());
        return data;
    }

    // Generates a sanitized document ID using title, amount, and date
    private String generateDocId(Expense e) {
        return (e.getTitle() + "_" + e.getAmount() + "_" + e.getDate())
                .replaceAll("[^a-zA-Z0-9]", "_"); // Replace non-alphanumerics with underscores
    }

    /**
     * Static method to sync user's budget value from Firestore into local SharedPreferences.
     * This is typically called once after login or periodically.
     */
    public static void syncBudgetFromFirebase(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String userEmail = prefs.getString("user_email", null);
        if (userEmail == null) return;

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userEmail)
                .collection("profile")
                .document("budget")
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Double budget = doc.getDouble("monthly_budget");
                        if (budget != null) {
                            // Store budget value in SharedPreferences
                            String prefsName = "BudgetPrefs_" + userEmail;
                            SharedPreferences userPrefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
                            userPrefs.edit().putFloat("monthly_budget", budget.floatValue()).apply();
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Log.e("BudgetSync", "Failed to sync budget", e));
    }
}
