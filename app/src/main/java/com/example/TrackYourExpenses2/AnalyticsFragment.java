package com.example.TrackYourExpenses2;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Fragment responsible for displaying financial analytics to the user.
 * Includes pie and bar charts for visualizing expense data by category and time.
 */
public class AnalyticsFragment extends Fragment {

    // UI Components for charts and buttons
    PieChart pieChart;
    BarChart barChart;
    Button btnMonth, btnYear;

    // Reference to the local SQLite database
    ExpenseDatabase db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Inflate the layout containing the charts and buttons
        View view = inflater.inflate(R.layout.fragment_analytics, container, false);

        // Initialize UI elements
        pieChart = view.findViewById(R.id.pieChart);
        barChart = view.findViewById(R.id.barChart);
        btnMonth = view.findViewById(R.id.btnMonth);
        btnYear = view.findViewById(R.id.btnYear);
        db = new ExpenseDatabase(getContext());

        // Set chart update behavior based on selected button
        btnMonth.setOnClickListener(v -> loadCharts("month"));
        btnYear.setOnClickListener(v -> loadCharts("year"));

        // Default chart display is monthly
        loadCharts("month");

        return view;
    }

    /**
     * Loads and renders pie and bar charts based on the specified mode (monthly or yearly).
     * @param mode "month" or "year"
     */
    private void loadCharts(String mode) {
        List<Expense> expenses = db.getAllExpenses(); // Fetch all expenses

        // Stores total spending per category and per date/month
        Map<String, Float> categoryTotals = new HashMap<>();
        Map<String, Float> timeTotals = new TreeMap<>(); // TreeMap keeps entries sorted

        Calendar now = Calendar.getInstance();
        int thisMonth = now.get(Calendar.MONTH);
        int thisYear = now.get(Calendar.YEAR);

        SimpleDateFormat sdf = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());

        // Aggregate expenses based on mode
        for (Expense e : expenses) {
            try {
                Date date = sdf.parse(e.getDate());
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                int month = cal.get(Calendar.MONTH);
                int year = cal.get(Calendar.YEAR);

                boolean include = mode.equals("year") ? (year == thisYear) : (month == thisMonth && year == thisYear);
                if (!include) continue;

                // Add expense to category total (for pie chart)
                categoryTotals.put(e.getCategory(),
                        categoryTotals.getOrDefault(e.getCategory(), 0f) + (float) e.getAmount());

                // Generate label based on mode and add to time totals (for bar chart)
                String label = mode.equals("year") ? getMonthLabel(month) : e.getDate();
                timeTotals.put(label,
                        timeTotals.getOrDefault(label, 0f) + (float) e.getAmount());

            } catch (Exception ignored) {
                // Ignore malformed date strings
            }
        }

        // === Pie Chart Setup ===
        List<PieEntry> pieEntries = new ArrayList<>();
        for (Map.Entry<String, Float> entry : categoryTotals.entrySet()) {
            pieEntries.add(new PieEntry(entry.getValue(), entry.getKey()));
        }

        PieDataSet pieDataSet = new PieDataSet(pieEntries, "Spending by Category");
        pieDataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        pieDataSet.setValueTextColor(Color.BLACK);
        pieDataSet.setValueTextSize(12f);

        PieData pieData = new PieData(pieDataSet);
        pieChart.setData(pieData);
        pieChart.setUsePercentValues(true); // Show percentage values
        pieChart.setEntryLabelColor(Color.BLACK);
        pieChart.setCenterText("Categories"); // Center text inside pie
        pieChart.setHoleRadius(40f); // Inner radius
        pieChart.getDescription().setEnabled(false); // Hide default description
        pieChart.invalidate(); // Refresh chart

        // === Bar Chart Setup ===
        List<BarEntry> barEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Float> entry : timeTotals.entrySet()) {
            barEntries.add(new BarEntry(i, entry.getValue())); // X=index, Y=amount
            labels.add(entry.getKey());
            i++;
        }

        BarDataSet barDataSet = new BarDataSet(barEntries, "Expenses");
        barDataSet.setColors(ColorTemplate.COLORFUL_COLORS);
        barDataSet.setValueTextColor(Color.BLACK);
        barDataSet.setValueTextSize(12f);

        BarData barData = new BarData(barDataSet);
        barData.setBarWidth(0.9f);

        barChart.setData(barData);
        // Custom formatter to use date/month labels on X-axis
        barChart.getXAxis().setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = Math.round(value);
                if (index >= 0 && index < labels.size()) {
                    return labels.get(index);
                } else {
                    return "";
                }
            }
        });
        barChart.getXAxis().setGranularity(1f); // Ensures labels align with bars
        barChart.setFitBars(true); // Bars take full width
        barChart.getDescription().setEnabled(false); // Hide chart description
        barChart.invalidate(); // Refresh chart
    }

    /**
     * Returns abbreviated month name for a given month index (0 = Jan).
     * @param month int from 0 to 11
     * @return String abbreviation like "Jan", "Feb", etc.
     */
    private String getMonthLabel(int month) {
        return new DateFormatSymbols().getShortMonths()[month];
    }
}
