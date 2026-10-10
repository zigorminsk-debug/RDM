package com.rdm.remote.desktop.manager.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.rdm.remote.desktop.manager.data.model.ClientEntity
import com.rdm.remote.desktop.manager.data.model.ServerEntity

data class RdmBackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val clients: List<ClientEntity>,
    val servers: List<ServerEntity>
)

object JsonBackupUtils {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun exportToJson(clients: List<ClientEntity>, servers: List<ServerEntity>): String {
        val backup = RdmBackupData(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            clients = clients,
            servers = servers
        )
        return gson.toJson(backup)
    }

    fun importFromJson(jsonString: String): RdmBackupData? {
        return try {
            gson.fromJson(jsonString, RdmBackupData::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
