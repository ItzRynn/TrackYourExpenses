package com.example.TrackYourExpenses2;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Fragment responsible for displaying the full details of a selected expense.
 * Includes functionality to edit or delete the expense locally and in Firebase.
 */
public class ExpenseDetailFragment extends Fragment {

    // Fields to store expense data passed as arguments
    private String title, date, category, imageUrl;
    private double amount;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Inflate the layout file associated with this fragment
        View view = inflater.inflate(R.layout.fragment_expense_detail, container, false);

        // Reference UI components from the layout
        TextView titleView = view.findViewById(R.id.detailTitle);
        TextView amountView = view.findViewById(R.id.detailAmount);
        TextView dateView = view.findViewById(R.id.detailDate);
        TextView categoryView = view.findViewById(R.id.detailCategory);
        ImageView imageView = view.findViewById(R.id.detailImage);

        Button btnEdit = view.findViewById(R.id.btnEditExpense);
        Button btnDelete = view.findViewById(R.id.btnDeleteExpense);

        // Retrieve passed arguments to populate UI
        Bundle args = getArguments();
        if (args != null) {
            title = args.getString("title");
            amount = args.getDouble("amount");
            date = args.getString("date");
            category = args.getString("category");
            imageUrl = args.getString("imageUrl");

            // Populate UI fields
            titleView.setText(title);
            amountView.setText("Amount: $" + amount);
            dateView.setText("Date: " + date);
            categoryView.setText("Category: " + category);

            // Load image using Glide if URL is present
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(requireContext()).load(imageUrl).into(imageView);
            } else {
                imageView.setVisibility(View.GONE); // Hide image view if no image
            }
        }

        // Edit button opens AddExpenseFragment prefilled with this expense
        btnEdit.setOnClickListener(v -> {
            // Bundle the current expense details
            Bundle bundle = new Bundle();
            bundle.putString("title", title);
            bundle.putDouble("amount", amount);
            bundle.putString("date", date);
            bundle.putString("category", category);
            bundle.putString("imageUrl", imageUrl);

            // Launch edit fragment with bundled data
            AddExpenseFragment editFragment = new AddExpenseFragment();
            editFragment.setArguments(bundle);

            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, editFragment)
                    .addToBackStack(null) // Enable back navigation
                    .commit();
        });

        // Delete button triggers confirmation dialog
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Expense")
                    .setMessage("Are you sure you want to delete this expense?")
                    .setPositiveButton("Delete", (dialog, which) -> deleteExpense())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        return view;
    }

    /**
     * Deletes the expense from local database and cloud, then navigates back.
     */
    private void deleteExpense() {
        // Delete from local database
        ExpenseDatabase db = new ExpenseDatabase(requireContext());
        db.deleteExpense(new Expense(title, amount, date, category, imageUrl));

        // Delete from Firebase Firestore
        deleteFromFirebase();

        Toast.makeText(getContext(), "Expense deleted", Toast.LENGTH_SHORT).show();

        // Navigate back to previous fragment
        requireActivity().getSupportFragmentManager().popBackStack();
    }

    /**
     * Deletes the expense from the user's Firebase Firestore collection.
     * Uses SharedPreferences to identify user and constructs a unique doc ID.
     */
    private void deleteFromFirebase() {
        SharedPreferences prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String userEmail = prefs.getString("user_email", null); // Retrieve stored user email

        if (userEmail != null) {
            // Construct unique document ID based on expense fields
            String docId = (title + "_" + amount + "_" + date).replaceAll("[^a-zA-Z0-9]", "_");

            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userEmail)
                    .collection("expenses")
                    .document(docId)
                    .delete()
                    .addOnSuccessListener(unused -> Log.d("FirestoreDelete", "Deleted from cloud: " + docId))
                    .addOnFailureListener(e -> Log.e("FirestoreDelete", "Cloud delete failed: " + docId, e));
        }
    }
}
