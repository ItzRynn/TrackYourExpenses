package com.example.TrackYourExpenses2;

import androidx.annotation.Nullable;

/**
 * Model class representing a single expense entry.
 * Contains details such as title, amount, date, category, and an optional image URL.
 */
public class Expense {

    // Fields to store expense details
    private String title;       // A short description/title of the expense (e.g., "Food expense")
    private String date;        // Date of the expense in string format (e.g., "30/05/2025")
    private String category;    // Category such as "Food", "Transport", etc.
    private String imageUrl;    // Optional local file path or URL to a receipt image
    private double amount;      // Expense amount

    /**
     * Constructor to initialize an Expense object.
     * @param title Short description or generated title of the expense
     * @param amount Expense amount
     * @param date Date when the expense occurred
     * @param category The category the expense falls under
     * @param imageUrl Optional image path for the receipt (nullable)
     **/
    public Expense(String title, double amount, String date, String category, @Nullable String imageUrl) {
        this.title = title;
        this.amount = amount;
        this.date = date;
        this.category = category;
        this.imageUrl = imageUrl;
    }

    // Getter methods to retrieve each property

    public String getTitle() {
        return title;
    }

    public double getAmount() {
        return amount;
    }

    public String getDate() {
        return date;
    }

    public String getCategory() {
        return category;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
