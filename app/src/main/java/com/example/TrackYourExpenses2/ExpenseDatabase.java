package com.example.TrackYourExpenses2;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite database helper for storing and managing expense records locally.
 * Handles table creation, insertion, deletion, updates, and fetching.
 */
public class ExpenseDatabase extends SQLiteOpenHelper {

    // Database name and version
    private static final String DATABASE_NAME = "expenses.db";
    private static final int DATABASE_VERSION = 1;

    // Constructor that initializes the database helper
    public ExpenseDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Called when the database is created for the first time.
     * Creates the "expenses" table with the required columns.
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE expenses (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +    // Unique ID for each expense
                "title TEXT NOT NULL, " +                     // Title of the expense
                "amount REAL NOT NULL, " +                    // Amount spent
                "date TEXT NOT NULL, " +                      // Date of the expense
                "category TEXT NOT NULL, " +                  // Category (e.g., Food, Utilities)
                "imageUrl TEXT" +                             // Optional receipt image path
                ")");
    }

    /**
     * Called when the database version is incremented.
     * Drops the existing table and recreates it.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS expenses");
        onCreate(db); // Recreate the table
    }

    /**
     * Inserts a new expense into the database.
     */
    public void insertExpense(String title, double amount, String date, String category, @Nullable String imageUrl) {
        SQLiteDatabase db = this.getWritableDatabase();

        // Prepare the values to insert
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("amount", amount);
        values.put("date", date);
        values.put("category", category);
        values.put("imageUrl", imageUrl);

        // Execute the insert operation
        db.insert("expenses", null, values);
        db.close();
    }

    /**
     * Deletes an expense by matching all fields except the ID.
     * Useful when unique ID isn't tracked externally.
     */
    public void deleteExpense(Expense e) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("expenses", "title=? AND amount=? AND date=? AND category=?",
                new String[]{e.getTitle(), String.valueOf(e.getAmount()), e.getDate(), e.getCategory()});
        db.close();
    }

    /**
     * Updates an existing expense record based on its original values.
     * @param oldTitle Previous title
     * @param oldAmount Previous amount
     * @param oldDate Previous date
     * @param oldCategory Previous category
     * @param newTitle New title
     * @param newAmount New amount
     * @param newDate New date
     * @param newCategory New category
     * @param newImageUrl New image URL (nullable)
     */
    public void updateExpense(String oldTitle, double oldAmount, String oldDate, String oldCategory,
                              String newTitle, double newAmount, String newDate, String newCategory, @Nullable String newImageUrl) {

        SQLiteDatabase db = this.getWritableDatabase();

        // Prepare updated values
        ContentValues values = new ContentValues();
        values.put("title", newTitle);
        values.put("amount", newAmount);
        values.put("date", newDate);
        values.put("category", newCategory);
        values.put("imageUrl", newImageUrl);

        // Update record where all old fields match
        db.update("expenses", values,
                "title=? AND amount=? AND date=? AND category=?",
                new String[]{oldTitle, String.valueOf(oldAmount), oldDate, oldCategory});
        db.close();
    }

    /**
     * Retrieves all expenses from the database, ordered by date in descending order.
     * @return List of Expense objects
     */
    public List<Expense> getAllExpenses() {
        List<Expense> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // Query for all expense records
        Cursor cursor = db.rawQuery("SELECT * FROM expenses ORDER BY date DESC", null);

        // Convert each row into an Expense object
        if (cursor.moveToFirst()) {
            do {
                String title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
                double amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"));
                String date = cursor.getString(cursor.getColumnIndexOrThrow("date"));
                String category = cursor.getString(cursor.getColumnIndexOrThrow("category"));
                String imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("imageUrl"));

                list.add(new Expense(title, amount, date, category, imageUrl));
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return list;
    }
}
