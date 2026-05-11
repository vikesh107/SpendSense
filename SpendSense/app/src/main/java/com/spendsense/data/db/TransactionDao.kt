package com.spendsense.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.spendsense.data.models.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<Transaction>): List<Long>

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getTransactionsPaged(limit: Int, offset: Int): List<Transaction>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE message_hash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): Transaction?

    @Query("SELECT COUNT(*) FROM transactions WHERE message_hash = :hash")
    suspend fun existsByHash(hash: String): Int

    @Query("""
        SELECT * FROM transactions 
        WHERE (:query IS NULL OR 
               amount LIKE '%' || :query || '%' OR 
               merchant LIKE '%' || :query || '%' OR 
               bank_name LIKE '%' || :query || '%' OR
               payment_method LIKE '%' || :query || '%' OR
               category LIKE '%' || :query || '%')
        AND (:method IS NULL OR payment_method = :method)
        AND (:bank IS NULL OR bank_name = :bank)
        AND (:category IS NULL OR category = :category)
        AND (:from IS NULL OR timestamp >= :from)
        AND (:to IS NULL OR timestamp <= :to)
        ORDER BY timestamp DESC
    """)
    fun searchTransactions(
        query: String? = null,
        method: String? = null,
        bank: String? = null,
        category: String? = null,
        from: Long? = null,
        to: Long? = null
    ): Flow<List<Transaction>>

    @Query("SELECT SUM(amount) FROM transactions")
    fun getTotalSpent(): Flow<Double?>

    @Query("SELECT SUM(amount) FROM transactions WHERE timestamp >= :from AND timestamp <= :to")
    fun getTotalSpentInRange(from: Long, to: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM transactions")
    fun getTransactionCount(): Flow<Int>

    @Query("SELECT DISTINCT payment_method FROM transactions ORDER BY payment_method")
    fun getDistinctPaymentMethods(): Flow<List<String>>

    @Query("SELECT DISTINCT bank_name FROM transactions WHERE bank_name != '' ORDER BY bank_name")
    fun getDistinctBanks(): Flow<List<String>>

    @Query("SELECT DISTINCT category FROM transactions ORDER BY category")
    fun getDistinctCategories(): Flow<List<String>>

    @Query("""
        SELECT category, SUM(amount) as total, COUNT(*) as count
        FROM transactions
        WHERE timestamp >= :from AND timestamp <= :to
        GROUP BY category
        ORDER BY total DESC
    """)
    suspend fun getCategoryBreakdown(from: Long, to: Long): List<CategorySummary>

    @Query("""
        SELECT * FROM transactions
        WHERE timestamp >= :from AND timestamp <= :to
        ORDER BY timestamp DESC
    """)
    suspend fun getTransactionsInRange(from: Long, to: Long): List<Transaction>

    @Query("SELECT MAX(timestamp) FROM transactions")
    suspend fun getLatestTimestamp(): Long?
}

data class CategorySummary(
    val category: String,
    val total: Double,
    val count: Int
)
