package com.example.TrackYourExpenses2;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import android.animation.ObjectAnimator;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * HomeFragment displays an overview of the user's monthly financial activity,
 * including total spent, budget usage, category breakdown, and average spend insights.
 * Also triggers a budget usage alert notification when spending exceeds 90%.
 */
public class HomeFragment extends Fragment {

    // UI elements
    private TextView totalSpentText, remainingBudgetText, dailySpendText, usagePercentText;
    private Button btnDaily, btnWeekly;
    private ProgressBar budgetProgressBar;
    private LinearLayout categoryBreakdownLayout;

    // Data variables
    private ExpenseDatabase db;
    private List<Expense> monthlyExpenses;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Bind UI components
        totalSpentText = view.findViewById(R.id.totalSpentText);
        remainingBudgetText = view.findViewById(R.id.remainingBudgetText);
        dailySpendText = view.findViewById(R.id.dailySpendText);
        usagePercentText = view.findViewById(R.id.usagePercentText);
        budgetProgressBar = view.findViewById(R.id.budgetProgressBar);
        categoryBreakdownLayout = view.findViewById(R.id.categoryBreakdownLayout);
        btnDaily = view.findViewById(R.id.btnDaily);
        btnWeekly = view.findViewById(R.id.btnWeekly);
        Button addExpenseButton = view.findViewById(R.id.addExpenseButton);
        Button viewAnalyticsButton = view.findViewById(R.id.viewAnalyticsButton);

        db = new ExpenseDatabase(getContext());
        monthlyExpenses = getThisMonthExpenses(); // Only get expenses for the current month

        // Define categories and emoji icons for each
        String[] allCategories = {"Food", "Transport", "Utilities", "Entertainment", "Shopping", "Other"};
        Map<String, String> categoryIcons = new HashMap<>();
        categoryIcons.put("Food", "🍔");
        categoryIcons.put("Transport", "🚗");
        categoryIcons.put("Utilities", "💡");
        categoryIcons.put("Entertainment", "🎮");
        categoryIcons.put("Shopping", "🛍️");
        categoryIcons.put("Other", "❓");

        // Get user-specific budget from SharedPreferences
        SharedPreferences basePrefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String userEmail = basePrefs.getString("user_email", null);
        String prefsName = userEmail != null ? "BudgetPrefs_" + userEmail : "GuestPrefs";
        SharedPreferences prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE);

        double monthlyBudget = prefs.getFloat("monthly_budget", 1000f); // Default: $1000
        float lastNotifiedSpent = prefs.getFloat("last_notified_spent", -1f); // Used to prevent repeated notifications

        // Calculate total monthly spending
        double total = 0;
        for (Expense e : monthlyExpenses) {
            total += e.getAmount();
        }

        // Budget calculations
        double remaining = monthlyBudget - total;
        double usagePercent = (total / monthlyBudget) * 100;
        int progress = (int) Math.min(usagePercent, 100); // Clamp to 100 for progress bar

        // Display budget status
        totalSpentText.setText("Total Spent (This Month): $" + String.format("%.2f", total));
        remainingBudgetText.setText("Remaining Budget: $" + String.format("%.2f", remaining));
        usagePercentText.setText("Used: " + String.format("%.0f", usagePercent) + "%");

        // Animate progress bar
        ObjectAnimator animation = ObjectAnimator.ofInt(budgetProgressBar, "progress", 0, progress);
        animation.setDuration(1000);
        animation.start();

        // Trigger notification if budget usage is >= 90% and not previously notified
        if (usagePercent >= 90 && (float) total > lastNotifiedSpent) {
            createNotificationChannel();
            NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), "budget_channel")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Budget Alert")
                    .setContentText("You've used " + String.format("%.0f", usagePercent) + "% of your budget!")
                    .setPriority(NotificationCompat.PRIORITY_HIGH);

            NotificationManagerCompat.from(requireContext()).notify(1, builder.build());

            // Update last notified value
            prefs.edit().putFloat("last_notified_spent", (float) total).apply();
        } else if (usagePercent < 90 && lastNotifiedSpent != -1f) {
            prefs.edit().putFloat("last_notified_spent", -1f).apply(); // Reset notification trigger
        }

        // Show daily or weekly average spend
        updateAverageSpend("daily");
        btnDaily.setOnClickListener(v -> updateAverageSpend("daily"));
        btnWeekly.setOnClickListener(v -> updateAverageSpend("weekly"));

        // Navigate to add expense screen
        addExpenseButton.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new AddExpenseFragment())
                    .addToBackStack(null)
                    .commit();
        });

        // Navigate to analytics screen via bottom nav
        viewAnalyticsButton.setOnClickListener(v -> {
            if (getActivity() != null) {
                BottomNavigationView nav = getActivity().findViewById(R.id.bottom_navigation);
                nav.setSelectedItemId(R.id.nav_analytics);
            }
        });

        // Calculate totals per category
        Map<String, Double> categoryTotals = new HashMap<>();
        for (Expense e : monthlyExpenses) {
            String category = e.getCategory();
            categoryTotals.put(category, categoryTotals.getOrDefault(category, 0.0) + e.getAmount());
        }

        // Determine top 3 spending categories
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(categoryTotals.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        String top1 = sorted.size() > 0 ? sorted.get(0).getKey() : null;
        String top2 = sorted.size() > 1 ? sorted.get(1).getKey() : null;
        String top3 = sorted.size() > 2 ? sorted.get(2).getKey() : null;

        // Display category breakdown in a vertical list
        categoryBreakdownLayout.removeAllViews();
        categoryBreakdownLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        for (String category : allCategories) {
            double amount = categoryTotals.getOrDefault(category, 0.0);
            String icon = categoryIcons.getOrDefault(category, "❔");

            TextView categoryView = new TextView(getContext());
            categoryView.setText(icon + " " + category + ": $" + String.format("%.2f", amount));
            categoryView.setPadding(8, 6, 8, 6);
            categoryView.setGravity(Gravity.CENTER);

            // Styling for top 3 categories
            if (category.equals(top1)) {
                categoryView.setTextSize(18);
                categoryView.setTypeface(null, Typeface.BOLD);
                categoryView.setTextColor(0xFFD32F2F); // Red
            } else if (category.equals(top2) || category.equals(top3)) {
                categoryView.setTextSize(16);
                categoryView.setTypeface(null, Typeface.BOLD_ITALIC);
            } else {
                categoryView.setTextSize(15);
            }

            categoryBreakdownLayout.addView(categoryView);
        }

        return view;
    }

    /**
     * Updates the average spending text based on selected mode.
     * @param type "daily" or "weekly"
     */
    private void updateAverageSpend(String type) {
        double total = 0;
        for (Expense e : monthlyExpenses) {
            total += e.getAmount();
        }

        Calendar calendar = Calendar.getInstance();
        int today = calendar.get(Calendar.DAY_OF_MONTH);
        double average;

        if (type.equals("daily")) {
            average = today == 0 ? 0 : total / today;
            dailySpendText.setText("Avg Daily Spend: $" + String.format("%.2f", average));
        } else {
            int week = (int) Math.ceil(today / 7.0);
            average = week == 0 ? 0 : total / week;
            dailySpendText.setText("Avg Weekly Spend: $" + String.format("%.2f", average));
        }
    }

    /**
     * Filters and returns only this month's expenses.
     */
    private List<Expense> getThisMonthExpenses() {
        List<Expense> filtered = new ArrayList<>();
        List<Expense> all = db.getAllExpenses();

        Calendar now = Calendar.getInstance();
        int thisMonth = now.get(Calendar.MONTH);
        int thisYear = now.get(Calendar.YEAR);

        SimpleDateFormat sdf = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());

        for (Expense e : all) {
            try {
                Date date = sdf.parse(e.getDate());
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                int month = cal.get(Calendar.MONTH);
                int year = cal.get(Calendar.YEAR);

                if (month == thisMonth && year == thisYear) {
                    filtered.add(e);
                }
            } catch (Exception ignored) {}
        }

        return filtered;
    }

    /**
     * Creates a notification channel (required for Android O and above).
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Budget Alerts";
            String description = "Notifications for high budget usage";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel("budget_channel", name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = requireContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
