package com.example.spendwise

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.spendwise.data.Transaction
import com.example.spendwise.databinding.ItemTransactionBinding
import java.util.Locale

class TransactionAdapter(private var displayList: List<Transaction>) :
    RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    private var allTransactions: List<Transaction> = displayList

    class TransactionViewHolder(val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = displayList[position]
        holder.binding.tvItemTitle.text = transaction.title
        holder.binding.tvItemCategory.text = transaction.category

        if (transaction.isIncome) {
            holder.binding.tvItemAmount.text = "+ ₹${transaction.amount}"
            holder.binding.tvItemAmount.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_green_dark))
        } else {
            holder.binding.tvItemAmount.text = "- ₹${transaction.amount}"
            holder.binding.tvItemAmount.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark))
        }
    }

    override fun getItemCount(): Int = displayList.size

    fun getTransactionAt(position: Int): Transaction {
        return displayList[position]
    }

    fun updateData(newList: List<Transaction>) {
        allTransactions = newList
        displayList = newList
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val lowercaseQuery = query.lowercase(Locale.getDefault()).trim()

        displayList = if (lowercaseQuery.isEmpty()) {
            allTransactions
        } else {
            allTransactions.filter {
                it.title.lowercase(Locale.getDefault()).contains(lowercaseQuery) ||
                        it.category.lowercase(Locale.getDefault()).contains(lowercaseQuery)
            }
        }
        notifyDataSetChanged()
    }
}