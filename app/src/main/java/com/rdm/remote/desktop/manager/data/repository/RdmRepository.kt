package com.rdm.remote.desktop.manager.data.repository

import com.rdm.remote.desktop.manager.data.database.AppDatabase
import com.rdm.remote.desktop.manager.data.model.ClientEntity
import com.rdm.remote.desktop.manager.data.model.ClientWithCount
import com.rdm.remote.desktop.manager.data.model.ServerEntity
import com.rdm.remote.desktop.manager.utils.JsonBackupUtils
import com.rdm.remote.desktop.manager.utils.SampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class RdmRepository(private val database: AppDatabase) {

    private val clientDao = database.clientDao()
    private val serverDao = database.serverDao()

    // ----------------- Client Operations -----------------

    fun getClientsWithCount(): Flow<List<ClientWithCount>> {
        return clientDao.getClientsWithCount()
    }

    fun searchClientsWithCount(query: String): Flow<List<ClientWithCount>> {
        return if (query.isBlank()) {
            clientDao.getClientsWithCount()
        } else {
            clientDao.searchClientsWithCount(query.trim())
        }
    }

    fun observeClientById(id: Long): Flow<ClientEntity?> {
        return clientDao.observeClientById(id)
    }

    suspend fun getClientById(id: Long): ClientEntity? = withContext(Dispatchers.IO) {
        clientDao.getClientById(id)
    }

    suspend fun insertClient(client: ClientEntity): Long = withContext(Dispatchers.IO) {
        clientDao.insertClient(client)
    }

    suspend fun updateClient(client: ClientEntity) = withContext(Dispatchers.IO) {
        clientDao.updateClient(client.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteClient(client: ClientEntity) = withContext(Dispatchers.IO) {
        clientDao.deleteClient(client)
    }

    suspend fun deleteClientById(id: Long) = withContext(Dispatchers.IO) {
        clientDao.deleteClientById(id)
    }

    // ----------------- Server Operations -----------------

    fun getServersByClient(clientId: Long): Flow<List<ServerEntity>> {
        return serverDao.getServersByClient(clientId)
    }

    fun searchServersByClient(clientId: Long, query: String): Flow<List<ServerEntity>> {
        return if (query.isBlank()) {
            serverDao.getServersByClient(clientId)
        } else {
            serverDao.searchServersByClient(clientId, query.trim())
        }
    }

    fun getAllServers(): Flow<List<ServerEntity>> {
        return serverDao.getAllServers()
    }

    fun searchAllServers(query: String): Flow<List<ServerEntity>> {
        return if (query.isBlank()) {
            serverDao.getAllServers()
        } else {
            serverDao.searchAllServers(query.trim())
        }
    }

    suspend fun getServerById(id: Long): ServerEntity? = withContext(Dispatchers.IO) {
        serverDao.getServerById(id)
    }

    suspend fun insertServer(server: ServerEntity): Long = withContext(Dispatchers.IO) {
        serverDao.insertServer(server)
    }

    suspend fun updateServer(server: ServerEntity) = withContext(Dispatchers.IO) {
        serverDao.updateServer(server.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteServer(server: ServerEntity) = withContext(Dispatchers.IO) {
        serverDao.deleteServer(server)
    }

    suspend fun deleteServerById(id: Long) = withContext(Dispatchers.IO) {
        serverDao.deleteServerById(id)
    }

    // ----------------- Demo Data & First Launch -----------------

    suspend fun checkAndInitSampleData() = withContext(Dispatchers.IO) {
        val count = clientDao.getClientCount()
        if (count == 0) {
            loadDemoData()
        }
    }

    suspend fun loadDemoData() = withContext(Dispatchers.IO) {
        val sampleList = SampleData.getSampleData()
        for (item in sampleList) {
            val clientId = clientDao.insertClient(item.client)
            for (srv in item.servers) {
                serverDao.insertServer(srv.copy(clientId = clientId))
            }
        }
    }

    // ----------------- Backup Export & Import -----------------

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val clients = clientDao.getAllClients().first()
        val servers = serverDao.getAllServers().first()
        JsonBackupUtils.exportToJson(clients, servers)
    }

    suspend fun importBackupJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        val data = JsonBackupUtils.importFromJson(jsonString) ?: return@withContext false
        val oldToNewClientId = mutableMapOf<Long, Long>()

        for (client in data.clients) {
            val oldId = client.id
            val newId = clientDao.insertClient(client.copy(id = 0))
            oldToNewClientId[oldId] = newId
        }

        for (server in data.servers) {
            val targetClientId = oldToNewClientId[server.clientId] ?: continue
            serverDao.insertServer(server.copy(id = 0, clientId = targetClientId))
        }

        true
    }

    // ----------------- TCP Ping Check -----------------

    suspend fun testConnection(ip: String, port: Int, timeoutMs: Int = 3000): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
