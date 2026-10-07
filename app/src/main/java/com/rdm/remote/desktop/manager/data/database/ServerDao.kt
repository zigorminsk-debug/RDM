package com.rdm.remote.desktop.manager.data.database

import androidx.room.*
import com.rdm.remote.desktop.manager.data.model.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers WHERE clientId = :clientId ORDER BY name ASC")
    fun getServersByClient(clientId: Long): Flow<List<ServerEntity>>

    @Query("""
        SELECT * FROM servers 
        WHERE clientId = :clientId 
        AND (name LIKE '%' || :query || '%' OR ip LIKE '%' || :query || '%' OR login LIKE '%' || :query || '%' OR domain LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    fun searchServersByClient(clientId: Long, query: String): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers ORDER BY name ASC")
    fun getAllServers(): Flow<List<ServerEntity>>

    @Query("""
        SELECT * FROM servers 
        WHERE name LIKE '%' || :query || '%' OR ip LIKE '%' || :query || '%' OR login LIKE '%' || :query || '%' OR domain LIKE '%' || :query || '%'
        ORDER BY name ASC
    """)
    fun searchAllServers(query: String): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: Long): ServerEntity?

    @Query("SELECT * FROM servers WHERE id = :id LIMIT 1")
    fun observeServerById(id: Long): Flow<ServerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity): Long

    @Update
    suspend fun updateServer(server: ServerEntity)

    @Delete
    suspend fun deleteServer(server: ServerEntity)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteServerById(id: Long)

    @Query("DELETE FROM servers WHERE clientId = :clientId")
    suspend fun deleteServersByClient(clientId: Long)

    @Query("SELECT COUNT(*) FROM servers")
    suspend fun getServerCount(): Int
}
