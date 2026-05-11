package com.spendsense.ui.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.spendsense.data.models.Transaction
import com.spendsense.data.repository.TransactionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = TransactionRepository(application)

    // ─── Filter State ──────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow<String?>(null)
    private val _filterMethod = MutableStateFlow<String?>(null)
    private val _filterBank = MutableStateFlow<String?>(null)
    private val _filterCategory = MutableStateFlow<String?>(null)
    private val _filterFrom = MutableStateFlow<Long?>(null)
    private val _filterTo = MutableStateFlow<Long?>(null)

    val searchQuery: StateFlow<String?> = _searchQuery
    val filterMethod: StateFlow<String?> = _filterMethod
    val filterBank: StateFlow<String?> = _filterBank
    val filterCategory: StateFlow<String?> = _filterCategory

    // ─── Transactions ──────────────────────────────────────────────────────
    val transactions: StateFlow<List<Transaction>> =
        combine(
            _searchQuery.debounce(300),
            _filterMethod,
            _filterBank,
            _filterCategory,
            _filterFrom,
            _filterTo
        ) { query, method, bank, category, from, to ->
            repo.searchTransactions(
                query = if (query.isNullOrBlank()) null else query,
                method = method,
                bank = bank,
                category = category,
                from = from,
                to = to
            )
        }
            .flatMapLatest { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Stats ─────────────────────────────────────────────────────────────
    val totalSpent: StateFlow<Double> = repo.totalSpent
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val transactionCount: StateFlow<Int> = repo.transactionCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val thisMonthTotal: StateFlow<Double> = repo.getTotalSpentInRange(
        thisMonthStart(), System.currentTimeMillis()
    )
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayTotal: StateFlow<Double> = repo.getTotalSpentInRange(
        todayStart(), System.currentTimeMillis()
    )
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // ─── Filter Options ────────────────────────────────────────────────────
    val distinctMethods: StateFlow<List<String>> = repo.distinctPaymentMethods
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val distinctBanks: StateFlow<List<String>> = repo.distinctBanks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val distinctCategories: StateFlow<List<String>> = repo.distinctCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Import Status ─────────────────────────────────────────────────────
    private val _importStatus = MutableLiveData<ImportStatus>()
    val importStatus: LiveData<ImportStatus> = _importStatus

    // ─── Actions ───────────────────────────────────────────────────────────

    fun setSearchQuery(query: String?) {
        _searchQuery.value = if (query.isNullOrBlank()) null else query
    }

    fun setFilterMethod(method: String?) { _filterMethod.value = method }
    fun setFilterBank(bank: String?) { _filterBank.value = bank }
    fun setFilterCategory(category: String?) { _filterCategory.value = category }

    fun setDateRange(from: Long?, to: Long?) {
        _filterFrom.value = from
        _filterTo.value = to
    }

    fun clearFilters() {
        _searchQuery.value = null
        _filterMethod.value = null
        _filterBank.value = null
        _filterCategory.value = null
        _filterFrom.value = null
        _filterTo.value = null
    }

    fun hasActiveFilters(): Boolean =
        !_searchQuery.value.isNullOrBlank() ||
                _filterMethod.value != null ||
                _filterBank.value != null ||
                _filterCategory.value != null ||
                _filterFrom.value != null

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { repo.delete(transaction) }
    }

    fun deleteTransactionById(id: Long) {
        viewModelScope.launch { repo.deleteById(id) }
    }

    fun startSmsImport() {
        viewModelScope.launch {
            _importStatus.value = ImportStatus.Running
            try {
                val result = com.spendsense.service.SmsHistoricalImporter
                    .importHistoricalSms(getApplication())
                _importStatus.value = ImportStatus.Done(result.imported, result.total)
            } catch (e: Exception) {
                _importStatus.value = ImportStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private fun todayStart(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun thisMonthStart(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    sealed class ImportStatus {
        object Running : ImportStatus()
        data class Done(val imported: Int, val total: Int) : ImportStatus()
        data class Error(val message: String) : ImportStatus()
    }
}
