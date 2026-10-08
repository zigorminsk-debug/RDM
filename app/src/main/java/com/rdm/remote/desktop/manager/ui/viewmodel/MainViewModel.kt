package com.rdm.remote.desktop.manager.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rdm.remote.desktop.manager.data.model.ClientEntity
import com.rdm.remote.desktop.manager.data.model.ClientWithCount
import com.rdm.remote.desktop.manager.data.model.ServerEntity
import com.rdm.remote.desktop.manager.data.repository.RdmRepository
import com.rdm.remote.desktop.manager.utils.AppUpdateManager
import com.rdm.remote.desktop.manager.utils.UpdateStatus
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

    // ----------------- All Servers (Search across all) -----------------

    val allServers: StateFlow<List<ServerEntity>> = _serverSearchQuery
        .flatMapLatest { query ->
            repository.searchAllServers(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ----------------- Ping / Status Test Results -----------------

    private val _pingResults = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val pingResults: StateFlow<Map<Long, Boolean>> = _pingResults.asStateFlow()

    // ----------------- Dialog UI States -----------------

    // Edit Client Dialog (null = closed)
    private val _editingClient = MutableStateFlow<ClientEntity?>(null)
    val editingClient: StateFlow<ClientEntity?> = _editingClient.asStateFlow()

    // Edit Server Dialog (null = closed)
    private val _editingServer = MutableStateFlow<ServerEntity?>(null)
    val editingServer: StateFlow<ServerEntity?> = _editingServer.asStateFlow()

    // Launch Dialog (null = closed)
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

    // ----------------- App Updates -----------------

    val updateStatus: StateFlow<UpdateStatus> = AppUpdateManager.updateStatus

    fun checkForUpdates(silentIfUpToDate: Boolean = false) {
        viewModelScope.launch {
            AppUpdateManager.checkForUpdates(silentIfUpToDate)
        }
    }

    fun dismissUpdateDialog() {
        AppUpdateManager.resetStatus()
    }

    fun downloadAndInstallUpdate(context: Context, downloadUrl: String, versionName: String) {
        viewModelScope.launch {
            AppUpdateManager.downloadAndInstallApk(context, downloadUrl, versionName)
        }
    }

    fun installDownloadedUpdate(context: Context) {
        val current = AppUpdateManager.updateStatus.value
        if (current is UpdateStatus.ReadyToInstall) {
            AppUpdateManager.installApk(context, current.apkFile)
        }
    }

    // ----------------- Search Actions -----------------

    fun setClientSearchQuery(query: String) {
        _clientSearchQuery.value = query
    }

    fun setServerSearchQuery(query: String) {
        _serverSearchQuery.value = query
    }

    fun selectClient(clientId: Long) {
        _selectedClientId.value = clientId
    }

    fun observeServerById(serverId: Long): Flow<ServerEntity?> {
        return repository.observeServerById(serverId)
    }

    // ----------------- Client CRUD Operations -----------------

    fun openAddClientDialog() {
        _editingClient.value = ClientEntity(
            id = 0,
            name = "",
            description = "",
            colorHex = "#0078D7"
        )
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
                repository.insertClient(current.copy(name = name, description = description, colorHex = colorHex))
                _userMessage.emit("Клиент «$name» успешно добавлен")
            } else {
                repository.updateClient(current.copy(name = name, description = description, colorHex = colorHex))
                _userMessage.emit("Данные клиента «$name» обновлены")
            }
            closeClientDialog()
        }
    }

    fun promptDeleteClient(client: ClientWithCount) {
        _deletingClient.value = client
    }

    fun dismissDeleteClient() {
        _deletingClient.value = null
    }

    fun confirmDeleteClient() {
        val clientWithCount = _deletingClient.value ?: return
        viewModelScope.launch {
            repository.deleteClientById(clientWithCount.client.id)
            _userMessage.emit("Клиент «${clientWithCount.client.name}» и все его серверы удалены")
            dismissDeleteClient()
        }
    }

    // ----------------- Server CRUD Operations -----------------

    fun openAddServerDialog(clientId: Long) {
        _editingServer.value = ServerEntity(
            id = 0,
            clientId = clientId,
            name = "",
            ip = "",
            port = 3389,
            login = "",
            password = "",
            domain = "",
            notes = "",
            colorHex = "#0078D7"
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
            val serverToSave = current.copy(
                name = name,
                ip = ip,
                port = port,
                domain = domain,
                login = login,
                password = password,
                notes = notes,
                resolution = resolution,
                soundRedirection = soundRedirection,
                adminSession = adminSession,
                colorHex = colorHex
            )
            if (current.id == 0L) {
                repository.insertServer(serverToSave)
                _userMessage.emit("Сервер «$name» успешно добавлен")
            } else {
                repository.updateServer(serverToSave)
                _userMessage.emit("Настройки сервера «$name» сохранены")
            }
            closeServerDialog()
        }
    }

    fun duplicateServer(server: ServerEntity) {
        viewModelScope.launch {
            val duplicate = server.copy(
                id = 0,
                name = "${server.name} (Копия)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.insertServer(duplicate)
            _userMessage.emit("Создана копия сервера «${server.name}»")
        }
    }

    fun promptDeleteServer(server: ServerEntity) {
        _deletingServer.value = server
    }

    fun dismissDeleteServer() {
        _deletingServer.value = null
    }

    fun confirmDeleteServer() {
        val server = _deletingServer.value ?: return
        viewModelScope.launch {
            repository.deleteServer(server)
            _userMessage.emit("Сервер «${server.name}» удален")
            dismissDeleteServer()
        }
    }

    // ----------------- RDP Launch Dialog -----------------

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
