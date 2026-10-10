package com.rdm.remote.desktop.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rdm.remote.desktop.manager.data.model.ClientEntity
import com.rdm.remote.desktop.manager.data.model.ClientWithCount
import com.rdm.remote.desktop.manager.data.model.ServerEntity
import com.rdm.remote.desktop.manager.data.repository.RdmRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val repository: RdmRepository) : ViewModel() {

    // ----------------- Client List State -----------------

    private val _clientSearchQuery = MutableStateFlow("")
    val clientSearchQuery: StateFlow<String> = _clientSearchQuery.asStateFlow()

    val clients: StateFlow<List<ClientWithCount>> = _clientSearchQuery
        .flatMapLatest { query ->
            repository.searchClientsWithCount(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ----------------- Server List State -----------------

    private val _selectedClientId = MutableStateFlow<Long?>(null)
    val selectedClientId: StateFlow<Long?> = _selectedClientId.asStateFlow()

    val currentClient: StateFlow<ClientEntity?> = _selectedClientId
        .flatMapLatest { id ->
            if (id != null) repository.observeClientById(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _serverSearchQuery = MutableStateFlow("")
    val serverSearchQuery: StateFlow<String> = _serverSearchQuery.asStateFlow()

    val servers: StateFlow<List<ServerEntity>> = combine(
        _selectedClientId,
        _serverSearchQuery
    ) { clientId, query ->
        Pair(clientId, query)
    }.flatMapLatest { (clientId, query) ->
        if (clientId != null) {
            repository.searchServersByClient(clientId, query)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allServers: StateFlow<List<ServerEntity>> = _serverSearchQuery
        .flatMapLatest { query ->
            repository.searchAllServers(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ----------------- Ping / Status State -----------------

    private val _pingResults = MutableStateFlow<Map<Long, Boolean?>>(emptyMap())
    val pingResults: StateFlow<Map<Long, Boolean?>> = _pingResults.asStateFlow()

    // ----------------- Dialog UI States -----------------

    // Client dialog (null = closed, ClientEntity with id 0 = create, id > 0 = edit)
    private val _editingClient = MutableStateFlow<ClientEntity?>(null)
    val editingClient: StateFlow<ClientEntity?> = _editingClient.asStateFlow()

    // Server dialog (null = closed, ServerEntity with id 0 = create, id > 0 = edit)
    private val _editingServer = MutableStateFlow<ServerEntity?>(null)
    val editingServer: StateFlow<ServerEntity?> = _editingServer.asStateFlow()

    // RDP Launch Dialog (null = closed, ServerEntity = open)
    private val _launchingServer = MutableStateFlow<ServerEntity?>(null)
    val launchingServer: StateFlow<ServerEntity?> = _launchingServer.asStateFlow()

    // Delete Client Confirmation (null = closed)
    private val _deletingClient = MutableStateFlow<ClientWithCount?>(null)
    val deletingClient: StateFlow<ClientWithCount?> = _deletingClient.asStateFlow()

    // Delete Server Confirmation (null = closed)
    private val _deletingServer = MutableStateFlow<ServerEntity?>(null)
    val deletingServer: StateFlow<ServerEntity?> = _deletingServer.asStateFlow()

    // Snackbar / Toast events
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    // ----------------- Search Actions -----------------

    fun setClientSearchQuery(query: String) {
        _clientSearchQuery.value = query
    }

    fun setServerSearchQuery(query: String) {
        _serverSearchQuery.value = query
    }

    fun selectClient(clientId: Long) {
        _selectedClientId.value = clientId
        _serverSearchQuery.value = ""
    }

    fun observeServerById(id: Long): Flow<ServerEntity?> {
        return repository.observeServerById(id)
    }

    // ----------------- Client CRUD Actions -----------------

    fun openAddClientDialog() {
        _editingClient.value = ClientEntity(name = "")
    }

    fun openEditClientDialog(client: ClientEntity) {
        _editingClient.value = client
    }

    fun closeClientDialog() {
        _editingClient.value = null
    }

    fun saveClient(name: String, description: String, colorHex: String) {
        val current = _editingClient.value ?: return
        viewModelScope.launch {
            if (current.id == 0L) {
                repository.insertClient(
                    ClientEntity(
                        name = name.trim(),
                        description = description.trim(),
                        colorHex = colorHex
                    )
                )
                _userMessage.emit("Клиент «$name» создан")
            } else {
                repository.updateClient(
                    current.copy(
                        name = name.trim(),
                        description = description.trim(),
                        colorHex = colorHex
                    )
                )
                _userMessage.emit("Клиент «$name» обновлен")
            }
            _editingClient.value = null
        }
    }

    fun promptDeleteClient(client: ClientWithCount) {
        _deletingClient.value = client
    }

    fun dismissDeleteClient() {
        _deletingClient.value = null
    }

    fun confirmDeleteClient() {
        val target = _deletingClient.value ?: return
        viewModelScope.launch {
            repository.deleteClientById(target.id)
            _userMessage.emit("Клиент «${target.name}» удален")
            _deletingClient.value = null
        }
    }

    // ----------------- Server CRUD Actions -----------------

    fun openAddServerDialog(clientId: Long) {
        _editingServer.value = ServerEntity(
            clientId = clientId,
            name = "",
            ip = "",
            port = 3389
        )
    }

    fun openEditServerDialog(server: ServerEntity) {
        _editingServer.value = server
    }

    fun closeServerDialog() {
        _editingServer.value = null
    }

    fun saveServer(
        name: String,
        ip: String,
        port: Int,
        domain: String,
        login: String,
        password: String,
        notes: String,
        resolution: String,
        soundRedirection: Int,
        adminSession: Boolean,
        colorHex: String
    ) {
        val current = _editingServer.value ?: return
        viewModelScope.launch {
            val updated = current.copy(
                name = name.trim(),
                ip = ip.trim(),
                port = if (port > 0) port else 3389,
                domain = domain.trim(),
                login = login.trim(),
                password = password,
                notes = notes.trim(),
                resolution = resolution,
                soundRedirection = soundRedirection,
                adminSession = adminSession,
                colorHex = colorHex
            )
            if (current.id == 0L) {
                repository.insertServer(updated)
                _userMessage.emit("Сервер «$name» добавлен")
            } else {
                repository.updateServer(updated)
                _userMessage.emit("Сервер «$name» сохранен")
            }
            _editingServer.value = null
        }
    }

    fun duplicateServer(server: ServerEntity) {
        viewModelScope.launch {
            val copy = server.copy(
                id = 0,
                name = "${server.name} (Копия)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.insertServer(copy)
            _userMessage.emit("Сервер продублирован")
        }
    }

    fun promptDeleteServer(server: ServerEntity) {
        _deletingServer.value = server
    }

    fun dismissDeleteServer() {
        _deletingServer.value = null
    }

    fun confirmDeleteServer() {
        val target = _deletingServer.value ?: return
        viewModelScope.launch {
            repository.deleteServerById(target.id)
            _userMessage.emit("Сервер «${target.name}» удален")
            _deletingServer.value = null
        }
    }

    // ----------------- RDP Launch Dialog Actions -----------------

    fun openRdpLaunchDialog(server: ServerEntity) {
        _launchingServer.value = server
    }

    fun closeRdpLaunchDialog() {
        _launchingServer.value = null
    }

    // ----------------- Ping / Status Check -----------------

    fun testServerConnection(server: ServerEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val isOnline = repository.testConnection(server.ip, server.port)
            val updatedMap = _pingResults.value.toMutableMap()
            updatedMap[server.id] = isOnline
            _pingResults.value = updatedMap

            if (isOnline) {
                _userMessage.emit("${server.name}: Сервер доступен (${server.ip}:${server.port})")
            } else {
                _userMessage.emit("${server.name}: Не удалось подключиться к ${server.ip}:${server.port}")
            }
        }
    }

    // ----------------- Backup / Demo Actions -----------------

    fun loadDemoData() {
        viewModelScope.launch {
            repository.loadDemoData()
            _userMessage.emit("Демо-данные успешно загружены")
        }
    }

    fun exportBackup(onExportReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportBackupJson()
            onExportReady(json)
            _userMessage.emit("Резервная копия подготовлена")
        }
    }

    fun importBackup(jsonString: String) {
        viewModelScope.launch {
            val success = repository.importBackupJson(jsonString)
            if (success) {
                _userMessage.emit("Резервная копия успешно импортирована")
            } else {
                _userMessage.emit("Ошибка при импорте резервной копии: неверный формат")
            }
        }
    }

    // ViewModel Factory
    class Factory(private val repository: RdmRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
