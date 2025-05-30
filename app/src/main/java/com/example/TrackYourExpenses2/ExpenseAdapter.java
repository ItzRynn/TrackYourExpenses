package com.example.TrackYourExpenses2;

import com.bumptech.glide.Glide;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView.Adapter that binds a list of Expense objects to views in a RecyclerView.
 * It provides support for item click handling, image loading, and category-based icons.
 */
public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder> {

    // Internal list of expenses to display
    private List<Expense> expenses = new ArrayList<>();

    // Listener for item clicks
    private OnItemClickListener listener;

    // Interface for handling clicks on individual expense items
    public interface OnItemClickListener {
        void onItemClick(Expense expense);
    }

    // Method to allow external classes (like Fragments) to set the click listener
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    // Called when RecyclerView needs a new ViewHolder
    @NonNull
    @Override
    public ExpenseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the layout for individual expense items
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_expense, parent, false);
        return new ExpenseViewHolder(view);
    }

    // Called to bind data to a ViewHolder at a given position
    @Override
    public void onBindViewHolder(@NonNull ExpenseViewHolder holder, int position) {
        Expense expense = expenses.get(position); // Get current expense
        holder.bind(expense); // Bind data to UI

        // Set up click listener for this item
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(expense);
        });
    }

    // Returns the total number of items in the data set
    @Override
    public int getItemCount() {
        return expenses.size();
    }

    // Updates the adapter's list and refreshes the view
    public void updateList(List<Expense> newList) {
        expenses.clear();
        expenses.addAll(newList);
        notifyDataSetChanged(); // Redraw all items
    }

    // Returns the expense at a specific position (useful for swipes, edits, etc.)
    public Expense getExpenseAt(int position) {
        return expenses.get(position);
    }

    // Removes an item at the specified position and notifies the adapter
    public void removeItem(int position) {
        expenses.remove(position);
        notifyItemRemoved(position);
    }

    /**
     * ViewHolder class that holds the view for each individual item.
     */
    static class ExpenseViewHolder extends RecyclerView.ViewHolder {
        TextView title, amount, date;
        ImageView icon;

        public ExpenseViewHolder(@NonNull View itemView) {
            super(itemView);
            // Find views in the item layout
            title = itemView.findViewById(R.id.item_title);
            amount = itemView.findViewById(R.id.item_amount);
            date = itemView.findViewById(R.id.item_date);
            icon = itemView.findViewById(R.id.item_icon);
        }

        /**
         * Binds an Expense object to the UI elements in the view.
         */
        public void bind(Expense expense) {
            // Set text views
            title.setText(expense.getTitle());
            amount.setText(String.format("$%.2f", expense.getAmount()));
            date.setText(expense.getDate());

            // Load image if available using Glide, otherwise fallback to a category icon
            if (expense.getImageUrl() != null && !expense.getImageUrl().isEmpty()) {
                Glide.with(icon.getContext())
                        .load(expense.getImageUrl())
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(icon);
            } else {
                // Use a default icon based on the category
                switch (expense.getCategory()) {
                    case "Food":
                        icon.setImageResource(android.R.drawable.ic_menu_compass);
                        break;
                    case "Transport":
                        icon.setImageResource(android.R.drawable.ic_menu_directions);
                        break;
                    case "Utilities":
                        icon.setImageResource(android.R.drawable.ic_menu_manage);
                        break;
                    case "Entertainment":
                        icon.setImageResource(android.R.drawable.ic_menu_slideshow);
                        break;
                    case "Shopping":
                        icon.setImageResource(android.R.drawable.ic_menu_crop);
                        break;
                    default:
                        icon.setImageResource(android.R.drawable.ic_menu_info_details);
                        break;
                }
            }
        }
    }
}
