package com.spendsense.data.repository

import android.content.Context
import com.spendsense.data.db.CategorySummary
import com.spendsense.data.db.SpendSenseDatabase
import com.spendsense.data.models.Transaction
import kotlinx.coroutines.flow.Flow

class TransactionRepository(context: Context) {

    private val dao = SpendSenseDatabase.getInstance(context).transactionDao()

    val allTransactions: Flow<List<Transaction>> = dao.getAllTransactions()
    val totalSpent: Flow<Double?> = dao.getTotalSpent()
    val transactionCount: Flow<Int> = dao.getTransactionCount()
    val distinctPaymentMethods: Flow<List<String>> = dao.getDistinctPaymentMethods()
    val distinctBanks: Flow<List<String>> = dao.getDistinctBanks()
    val distinctCategories: Flow<List<String>> = dao.getDistinctCategories()

    fun searchTransactions(
        query: String? = null,
        method: String? = null,
        bank: String? = null,
        category: String? = null,
        from: Long? = null,
        to: Long? = null
    ): Flow<List<Transaction>> = dao.searchTransactions(query, method, bank, category, from, to)

    fun getTotalSpentInRange(from: Long, to: Long): Flow<Double?> =
        dao.getTotalSpentInRange(from, to)

    suspend fun getTransactionById(id: Long): Transaction? = dao.getTransactionById(id)

    suspend fun insert(transaction: Transaction): Long = dao.insert(transaction)

    suspend fun delete(transaction: Transaction) = dao.delete(transaction)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun existsByHash(hash: String): Boolean = dao.existsByHash(hash) > 0

    suspend fun getCategoryBreakdown(from: Long, to: Long): List<CategorySummary> =
        dao.getCategoryBreakdown(from, to)

    suspend fun getTransactionsInRange(from: Long, to: Long): List<Transaction> =
        dao.getTransactionsInRange(from, to)

    suspend fun getLatestTimestamp(): Long? = dao.getLatestTimestamp()
}
