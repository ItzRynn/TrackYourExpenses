package com.example.TrackYourExpenses2;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import java.util.*;

/**
 * Fragment that displays a scrollable history of all expenses using RecyclerView.
 * Supports filtering, sorting, item-click navigation, and swipe-to-delete with Firebase sync.
 */
public class HistoryFragment extends Fragment {

    RecyclerView recyclerView;        // RecyclerView to display expenses
    Button btnFilter, btnSort;        // Buttons to open filter/sort dialogs
    ExpenseAdapter adapter;           // Custom adapter for expenses
    ExpenseDatabase db;               // Local SQLite database
    List<Expense> allExpenses;        // Complete list of expenses

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Inflate the fragment's layout
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        // Bind UI elements
        recyclerView = view.findViewById(R.id.recycler_history);
        btnFilter = view.findViewById(R.id.btnFilter);
        btnSort = view.findViewById(R.id.btnSort);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext())); // Vertical list

        // Load expenses from local database
        db = new ExpenseDatabase(getContext());
        allExpenses = db.getAllExpenses();

        // Initialize adapter and attach it to RecyclerView
        adapter = new ExpenseAdapter();
        adapter.updateList(allExpenses);
        recyclerView.setAdapter(adapter);

        // Handle click on individual expense to open detail view
        adapter.setOnItemClickListener(expense -> {
            Bundle bundle = new Bundle();
            bundle.putString("title", expense.getTitle());
            bundle.putDouble("amount", expense.getAmount());
            bundle.putString("date", expense.getDate());
            bundle.putString("category", expense.getCategory());
            bundle.putString("imageUrl", expense.getImageUrl());

            ExpenseDetailFragment detailFragment = new ExpenseDetailFragment();
            detailFragment.setArguments(bundle);

            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, detailFragment)
                    .addToBackStack(null)
                    .commit();
        });

        // Enable swipe-to-delete functionality
        setupSwipeToDelete();

        // Handle sorting and filtering button clicks
        btnFilter.setOnClickListener(v -> showFilterDialog());
        btnSort.setOnClickListener(v -> showSortDialog());

        return view;
    }

    /**
     * Enables swipe gestures to delete expense items and syncs deletion with Firestore.
     */
    private void setupSwipeToDelete() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                return false; // No drag/drop support
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                int position = vh.getAdapterPosition();
                Expense toDelete = adapter.getExpenseAt(position);

                // Remove from local DB and adapter
                db.deleteExpense(toDelete);
                adapter.removeItem(position);
                Toast.makeText(getContext(), "Expense deleted", Toast.LENGTH_SHORT).show();

                // Also delete from Firebase if user is logged in
                SharedPreferences prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
                String userEmail = prefs.getString("user_email", null);

                if (userEmail != null) {
                    String docId = (toDelete.getTitle() + "_" + toDelete.getAmount() + "_" + toDelete.getDate())
                            .replaceAll("[^a-zA-Z0-9]", "_");

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
        }).attachToRecyclerView(recyclerView);
    }

    /**
     * Displays a dialog allowing the user to sort expenses by various criteria.
     */
    private void showSortDialog() {
        String[] options = {
                "Amount (High to Low)",
                "Amount (Low to High)",
                "Date (Newest First)",
                "Date (Oldest First)",
                "Category (A-Z)"
        };

        new AlertDialog.Builder(getContext())
                .setTitle("Sort by")
                .setItems(options, (dialog, which) -> {
                    Comparator<Expense> comparator = null;

                    // Define sort logic based on selection
                    switch (which) {
                        case 0: comparator = (a, b) -> Double.compare(b.getAmount(), a.getAmount()); break;
                        case 1: comparator = Comparator.comparingDouble(Expense::getAmount); break;
                        case 2: comparator = (a, b) -> parseDate(b.getDate()).compareTo(parseDate(a.getDate())); break;
                        case 3: comparator = (a, b) -> parseDate(a.getDate()).compareTo(parseDate(b.getDate())); break;
                        case 4: comparator = Comparator.comparing(Expense::getCategory); break;
                    }

                    // Apply sorting and refresh list
                    Collections.sort(allExpenses, comparator);
                    adapter.updateList(allExpenses);
                })
                .show();
    }

    /**
     * Displays a dialog to filter the expense list by category.
     */
    private void showFilterDialog() {
        List<String> categories = new ArrayList<>(Arrays.asList(
                "All", "Food", "Transport", "Utilities", "Entertainment", "Other"
        ));

        new AlertDialog.Builder(getContext())
                .setTitle("Filter by Category")
                .setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, categories), (dialog, which) -> {
                    String selected = categories.get(which);

                    if (selected.equals("All")) {
                        adapter.updateList(allExpenses); // Show full list
                    } else {
                        List<Expense> filtered = new ArrayList<>();
                        for (Expense e : allExpenses) {
                            if (e.getCategory().equals(selected)) {
                                filtered.add(e);
                            }
                        }
                        adapter.updateList(filtered); // Show filtered list
                    }
                })
                .show();
    }

    /**
     * Safely parses a date string (format: d/M/yyyy) into a Date object.
     * Returns a fallback date (epoch) on parse failure.
     */
    private Date parseDate(String dateStr) {
        try {
            return new java.text.SimpleDateFormat("d/M/yyyy", Locale.getDefault()).parse(dateStr);
        } catch (Exception e) {
            return new Date(0); // Epoch fallback
        }
    }
}
