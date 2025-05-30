package com.example.TrackYourExpenses2;

import android.app.DatePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;

import java.io.*;
import java.util.Calendar;

// Fragment responsible for adding or editing an expense
public class AddExpenseFragment extends Fragment {

    // UI elements
    EditText amountInput, dateInput;
    Spinner categorySpinner;
    Button btnUpload, btnSave;
    ImageView imagePreview;

    // URI to store selected image from gallery
    Uri selectedImageUri = null;

    // Categories for the dropdown (Spinner)
    String[] categories = {"Food", "Transport", "Utilities", "Entertainment", "Other"};

    // Flag to distinguish between Add and Edit modes
    boolean isEditMode = false;

    // Stores the old expense when editing
    Expense oldExpense;

    // Image picker launcher using Android's Activity Result API
    ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    imagePreview.setImageURI(uri); // Preview the selected image
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Inflate layout for the fragment
        View view = inflater.inflate(R.layout.fragment_add_expense, container, false);

        // Initialize UI components
        amountInput = view.findViewById(R.id.inputAmount);
        dateInput = view.findViewById(R.id.inputDate);
        categorySpinner = view.findViewById(R.id.spinnerCategory);
        btnUpload = view.findViewById(R.id.btnUpload);
        btnSave = view.findViewById(R.id.btnSave);
        imagePreview = view.findViewById(R.id.imagePreview);

        // Set up category dropdown
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);

        // Show DatePickerDialog when clicking date input
        dateInput.setOnClickListener(v -> {
            final Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            // Set selected date in the input field
            DatePickerDialog dpd = new DatePickerDialog(getContext(), (view1, y, m, d) ->
                    dateInput.setText(d + "/" + (m + 1) + "/" + y), year, month, day);
            dpd.show();
        });

        // Launch image picker
        btnUpload.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        // Check if this is an edit operation and populate fields accordingly
        Bundle args = getArguments();
        if (args != null && args.containsKey("title")) {
            isEditMode = true;

            // Create Expense object with passed arguments
            oldExpense = new Expense(
                    args.getString("title"),
                    args.getDouble("amount"),
                    args.getString("date"),
                    args.getString("category"),
                    args.getString("imageUrl")
            );

            // Populate UI with existing data
            amountInput.setText(String.valueOf(oldExpense.getAmount()));
            dateInput.setText(oldExpense.getDate());
            int index = java.util.Arrays.asList(categories).indexOf(oldExpense.getCategory());
            if (index != -1) categorySpinner.setSelection(index);

            // Load existing image using Glide if available
            if (oldExpense.getImageUrl() != null && !oldExpense.getImageUrl().isEmpty()) {
                Glide.with(requireContext()).load(oldExpense.getImageUrl()).into(imagePreview);
            }

            // Change button text to indicate update mode
            btnSave.setText("Update");
        }

        // Save or update expense when button is clicked
        btnSave.setOnClickListener(v -> {
            String amountStr = amountInput.getText().toString().trim();
            String date = dateInput.getText().toString().trim();
            String category = categorySpinner.getSelectedItem().toString();

            // Basic input validation
            if (amountStr.isEmpty() || date.isEmpty()) {
                Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid amount", Toast.LENGTH_SHORT).show();
                return;
            }

            String title = category + " expense";
            String localImagePath = null;

            // Handle image saving if selected
            if (selectedImageUri != null) {
                try {
                    String fileName = "expense_" + System.currentTimeMillis() + ".jpg";
                    File imageFile = new File(requireContext().getFilesDir(), fileName);

                    // Save selected image to internal storage
                    try (InputStream in = requireContext().getContentResolver().openInputStream(selectedImageUri);
                         OutputStream out = new FileOutputStream(imageFile)) {
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }

                    localImagePath = imageFile.getAbsolutePath();

                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(getContext(), "Failed to save image locally", Toast.LENGTH_SHORT).show();
                }
            } else if (isEditMode) {
                localImagePath = oldExpense.getImageUrl(); // retain old image if no new one chosen
            }

            // Create new Expense object from form inputs
            Expense newExpense = new Expense(title, amount, date, category, localImagePath);

            // Perform update or insert
            if (isEditMode) {
                updateExpense(oldExpense, newExpense);
            } else {
                saveNewExpense(newExpense);
            }
        });

        return view;
    }

    // Updates an existing expense in both local DB and Firebase
    private void updateExpense(Expense oldExpense, Expense newExpense) {
        ExpenseDatabase db = new ExpenseDatabase(requireContext());
        db.updateExpense(
                oldExpense.getTitle(), oldExpense.getAmount(), oldExpense.getDate(), oldExpense.getCategory(),
                newExpense.getTitle(), newExpense.getAmount(), newExpense.getDate(), newExpense.getCategory(), newExpense.getImageUrl()
        );

        // Update cloud data
        FirebaseSyncHelper syncHelper = new FirebaseSyncHelper(requireContext());
        syncHelper.updateExpenseInFirebase(oldExpense, newExpense);

        Toast.makeText(getContext(), "Expense updated!", Toast.LENGTH_SHORT).show();
        requireActivity().getSupportFragmentManager().popBackStack(); // Navigate back
    }

    // Saves a new expense to the local DB and syncs to Firebase
    private void saveNewExpense(Expense expense) {
        ExpenseDatabase db = new ExpenseDatabase(requireContext());
        db.insertExpense(expense.getTitle(), expense.getAmount(), expense.getDate(), expense.getCategory(), expense.getImageUrl());

        // Trigger cloud sync
        FirebaseSyncHelper syncHelper = new FirebaseSyncHelper(requireContext());
        syncHelper.syncLocalToFirebase();

        Toast.makeText(getContext(), "Expense saved!", Toast.LENGTH_SHORT).show();
        requireActivity().getSupportFragmentManager().popBackStack(); // Navigate back
    }
}
