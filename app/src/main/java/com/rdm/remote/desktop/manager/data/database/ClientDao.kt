package com.rdm.remote.desktop.manager.data.database

import androidx.room.*
import com.rdm.remote.desktop.manager.data.model.ClientEntity
import com.rdm.remote.desktop.manager.data.model.ClientWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {

    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun getAllClients(): Flow<List<ClientEntity>>

    @Query("""
        SELECT c.id, c.name, c.description, c.colorHex, c.createdAt, c.updatedAt, 
               COUNT(s.id) AS serverCount 
        FROM clients c 
        LEFT JOIN servers s ON c.id = s.clientId 
        GROUP BY c.id 
        ORDER BY c.name ASC
    """)
    fun getClientsWithCount(): Flow<List<ClientWithCount>>

    @Query("""
        SELECT c.id, c.name, c.description, c.colorHex, c.createdAt, c.updatedAt, 
               COUNT(s.id) AS serverCount 
        FROM clients c 
        LEFT JOIN servers s ON c.id = s.clientId 
        WHERE c.name LIKE '%' || :query || '%' OR c.description LIKE '%' || :query || '%'
        GROUP BY c.id 
        ORDER BY c.name ASC
    """)
    fun searchClientsWithCount(query: String): Flow<List<ClientWithCount>>

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun getClientById(id: Long): ClientEntity?

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    fun observeClientById(id: Long): Flow<ClientEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: ClientEntity): Long

    @Update
    suspend fun updateClient(client: ClientEntity)

    @Delete
    suspend fun deleteClient(client: ClientEntity)

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun deleteClientById(id: Long)

    @Query("SELECT COUNT(*) FROM clients")
    suspend fun getClientCount(): Int
}
