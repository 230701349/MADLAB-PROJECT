package com.example.spendwise.data

import com.google.firebase.firestore.PropertyName

data class Transaction(
    var id: String = "",
    var title: String = "",
    var amount: Double = 0.0,
    var category: String = "",

    // Add these lines right here above the variable:
    @get:PropertyName("isIncome")
    @set:PropertyName("isIncome")
    var isIncome: Boolean = false,

    var timestamp: Long = 0L
)