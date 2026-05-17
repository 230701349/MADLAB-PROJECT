package com.example.spendwise.data

import com.google.firebase.firestore.FirebaseFirestore

class FirebaseHelper {

    private val database = FirebaseFirestore.getInstance()
    private val transactionCollection = database.collection("transactions")

    fun addTransactionToCloud(transaction: Transaction, onResult: (Boolean) -> Unit) {
        val documentRef = transactionCollection.document()
        val finalTransaction = transaction.copy(id = documentRef.id)

        documentRef.set(finalTransaction)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    fun listenToTransactions(onUpdate: (List<Transaction>) -> Unit) {
        transactionCollection.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val transactions = snapshot.toObjects(Transaction::class.java)
            onUpdate(transactions)
        }
    }

    fun deleteTransactionFromCloud(id: String, onResult: (Boolean) -> Unit) {
        if (id.isEmpty()) return
        transactionCollection.document(id).delete()
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}