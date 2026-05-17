package com.example.spendwise

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.spendwise.data.FirebaseHelper
import com.example.spendwise.data.Transaction
import com.example.spendwise.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var transactionAdapter: TransactionAdapter
    private val firebaseHelper = FirebaseHelper()
    private var currentMasterList: List<Transaction> = emptyList()

    // Global tracker to hold the calendar month selection
    private var selectedCalendarInstance: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Setup Spinner Dropdown Menu
        val categories = listOf("Food", "Rent", "Salary", "Bills", "Entertainment")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        binding.spinnerCategory.adapter = spinnerAdapter

        // 2. Setup RecyclerView Layout
        transactionAdapter = TransactionAdapter(emptyList())
        binding.recyclerViewTransactions.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewTransactions.adapter = transactionAdapter

        // 3. Listen to Real-time Cloud Firebase Changes
        firebaseHelper.listenToTransactions { transactionList ->
            currentMasterList = transactionList
            transactionAdapter.updateData(transactionList)
            calculateBalance(transactionList)
            generateMonthlyAnalytics(transactionList)
        }

        // 4. Handle Entry Creation Save Click
        binding.btnSave.setOnClickListener { saveTransaction() }

        // 5. Connect Text Watcher to Search Bar for Real-time Filtering
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                transactionAdapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 6. Bind Swipe-to-Delete Touch Gesture Handler to RecyclerView
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val targetTransaction = transactionAdapter.getTransactionAt(position)

                firebaseHelper.deleteTransactionFromCloud(targetTransaction.id) { isSuccess ->
                    if (isSuccess) {
                        Toast.makeText(this@MainActivity, "Item deleted from Cloud!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to remove entry", Toast.LENGTH_SHORT).show()
                        transactionAdapter.notifyItemChanged(position)
                    }
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerViewTransactions)

        // 7. Connect Spreadsheet Generation to Export Button Click
        binding.btnExportExcel.setOnClickListener { exportDataToCSV() }

        // 8. Handle Month Selector Click Dialogue Popup
        binding.btnSelectMonth.setOnClickListener { showMonthYearPickerDialog() }

        // 9. Handle Tab Layout Page Switches
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    binding.layoutTransactionsPage.visibility = View.VISIBLE
                    binding.layoutSummaryPage.visibility = View.GONE
                } else {
                    binding.layoutTransactionsPage.visibility = View.GONE
                    binding.layoutSummaryPage.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun calculateBalance(list: List<Transaction>) {
        var totalBalance = 0.0
        for (item in list) {
            if (item.isIncome == true) totalBalance += item.amount else totalBalance -= item.amount
        }
        binding.tvTotalBalance.text = "₹${String.format("%.2f", totalBalance)}"
    }

    private fun showMonthYearPickerDialog() {
        val months = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Select Statement Month")
        builder.setItems(months) { _, which ->
            selectedCalendarInstance.set(Calendar.MONTH, which)
            selectedCalendarInstance.set(Calendar.DAY_OF_MONTH, 1)
            generateMonthlyAnalytics(currentMasterList)
        }
        builder.show()
    }

    private fun generateMonthlyAnalytics(list: List<Transaction>) {
        var incomeSum = 0.0
        var expenseSum = 0.0
        val categoryMap = HashMap<String, Double>()

        // Milestone boundaries calculation for selected time window window
        val startCalendar = selectedCalendarInstance.clone() as Calendar
        startCalendar.set(Calendar.DAY_OF_MONTH, 1)
        startCalendar.set(Calendar.HOUR_OF_DAY, 0)
        startCalendar.set(Calendar.MINUTE, 0)
        startCalendar.set(Calendar.SECOND, 0)
        startCalendar.set(Calendar.MILLISECOND, 0)
        val startOfSelectedMonthMs = startCalendar.timeInMillis

        val endCalendar = selectedCalendarInstance.clone() as Calendar
        endCalendar.set(Calendar.DAY_OF_MONTH, endCalendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        endCalendar.set(Calendar.HOUR_OF_DAY, 23)
        endCalendar.set(Calendar.MINUTE, 59)
        endCalendar.set(Calendar.SECOND, 59)
        endCalendar.set(Calendar.MILLISECOND, 999)
        val endOfSelectedMonthMs = endCalendar.timeInMillis

        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        val selectedMonthName = monthFormat.format(selectedCalendarInstance.time)

        binding.tvAnalyticsHeader.text = "$selectedMonthName Report"

        for (item in list) {
            if (item.timestamp in startOfSelectedMonthMs..endOfSelectedMonthMs) {
                if (item.isIncome == true) {
                    incomeSum += item.amount
                } else {
                    expenseSum += item.amount
                    categoryMap[item.category] = categoryMap.getOrDefault(item.category, 0.0) + item.amount
                }
            }
        }

        binding.tvMonthlyIncome.text = "₹${String.format("%.2f", incomeSum)}"
        binding.tvMonthlyExpense.text = "₹${String.format("%.2f", expenseSum)}"

        val highestExpenseCategory = categoryMap.maxByOrNull { it.value }
        if (highestExpenseCategory != null) {
            binding.tvSmartInsight.text = "Insight for $selectedMonthName: You spent the most on ${highestExpenseCategory.key} this month (₹${String.format("%.2f", highestExpenseCategory.value)}). Setting a limit on ${highestExpenseCategory.key} will speed up your savings!"
        } else {
            binding.tvSmartInsight.text = "Insight for $selectedMonthName: No records found matching this monthly window framework parameters."
        }
    }

    private fun saveTransaction() {
        val title = binding.etTitle.text.toString().trim()
        val amountText = binding.etAmount.text.toString().trim()

        if (title.isEmpty() || amountText.isEmpty()) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountText.toDoubleOrNull() ?: 0.0
        val category = binding.spinnerCategory.selectedItem.toString()
        val isIncome = binding.radioIncome.isChecked

        val transaction = Transaction("", title, amount, category, isIncome, System.currentTimeMillis())

        firebaseHelper.addTransactionToCloud(transaction) { isSuccess ->
            if (isSuccess) {
                binding.etTitle.text.clear()
                binding.etAmount.text.clear()
                Toast.makeText(this, "Saved Successfully!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportDataToCSV() {
        if (currentMasterList.isEmpty()) {
            Toast.makeText(this, "No transaction data available to export.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val targetFolder = File(getExternalFilesDir(null), "Reports")
            if (!targetFolder.exists()) targetFolder.mkdirs()

            val reportFile = File(targetFolder, "SpendWise_Report.csv")
            val writer = FileWriter(reportFile)

            writer.append("Document ID,Title,Category,Amount,Type,Timestamp\n")

            for (record in currentMasterList) {
                val recordTypeString = if (record.isIncome) "Income" else "Expense"
                writer.append("${record.id},${record.title},${record.category},${record.amount},$recordTypeString,${record.timestamp}\n")
            }

            writer.flush()
            writer.close()

            val contentUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", reportFile)

            val exportIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "SpendWise Financial Report")
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(exportIntent, "Export Report via:"))

        } catch (error: Exception) {
            error.printStackTrace()
            Toast.makeText(this, "Failed to export spreadsheet report.", Toast.LENGTH_SHORT).show()
        }
    }
}