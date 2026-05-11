package com.spendsense.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.spendsense.data.models.Transaction
import com.spendsense.data.repository.TransactionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = TransactionRepository(application)

    private val _searchQuery    = MutableStateFlow<String?>(null)
    private val _filterMethod   = MutableStateFlow<String?>(null)
    private val _filterBank     = MutableStateFlow<String?>(null)
    private val _filterCategory = MutableStateFlow<String?>(null)
    private val _filterFrom     = MutableStateFlow<Long?>(null)
    private val _filterTo       = MutableStateFlow<Long?>(null)

    val searchQuery:    StateFlow<String?> = _searchQuery
    val filterMethod:   StateFlow<String?> = _filterMethod
    val filterBank:     StateFlow<String?> = _filterBank
    val filterCategory: StateFlow<String?> = _filterCategory

    data class FilterState(
        val query:    String? = null,
        val method:   String? = null,
        val bank:     String? = null,
        val category: String? = null,
        val from:     Long?   = null,
        val to:       Long?   = null
    )

    private val _activeFilters = MutableStateFlow(FilterState())

    @Suppress("OPT_IN_USAGE")
    val transactions: StateFlow<List<Transaction>> =
        _activeFilters
            .flatMapLatest { f ->
                repo.searchTransactions(
                    query    = f.query,
                    method   = f.method,
                    bank     = f.bank,
                    category = f.category,
                    from     = f.from,
                    to       = f.to
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSpent: StateFlow<Double> = repo.totalSpent
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val transactionCount: StateFlow<Int> = repo.transactionCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val thisMonthTotal: StateFlow<Double> = repo.getTotalSpentInRange(
        thisMonthStart(), System.currentTimeMillis()
    ).map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayTotal: StateFlow<Double> = repo.getTotalSpentInRange(
        todayStart(), System.currentTimeMillis()
    ).map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val distinctMethods: StateFlow<List<String>> = repo.distinctPaymentMethods
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val distinctBanks: StateFlow<List<String>> = repo.distinctBanks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val distinctCategories: StateFlow<List<String>> = repo.distinctCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importStatus = MutableLiveData<ImportStatus>()
    val importStatus: LiveData<ImportStatus> = _importStatus

    private var searchDebounceJob: Job? = null

    fun setSearchQuery(query: String?) {
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(300)
            val q = if (query.isNullOrBlank()) null else query
            _searchQuery.value = q
            _activeFilters.value = _activeFilters.value.copy(query = q)
        }
    }

    fun setFilterMethod(method: String?) {
        _filterMethod.value = method
        _activeFilters.value = _activeFilters.value.copy(method = method)
    }

    fun setFilterBank(bank: String?) {
        _filterBank.value = bank
        _activeFilters.value = _activeFilters.value.copy(bank = bank)
    }

    fun setFilterCategory(category: String?) {
        _filterCategory.value = category
        _activeFilters.value = _activeFilters.value.copy(category = category)
    }

    fun setDateRange(from: Long?, to: Long?) {
        _filterFrom.value = from
        _filterTo.value   = to
        _activeFilters.value = _activeFilters.value.copy(from = from, to = to)
    }

    fun clearFilters() {
        _searchQuery.value    = null
        _filterMethod.value   = null
        _filterBank.value     = null
        _filterCategory.value = null
        _filterFrom.value     = null
        _filterTo.value       = null
        _activeFilters.value  = FilterState()
    }

    fun hasActiveFilters(): Boolean = _activeFilters.value != FilterState()

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

    private fun todayStart(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun thisMonthStart(): Long =
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0);       set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    sealed class ImportStatus {
        object Running : ImportStatus()
        data class Done(val imported: Int, val total: Int) : ImportStatus()
        data class Error(val message: String) : ImportStatus()
    }
}
