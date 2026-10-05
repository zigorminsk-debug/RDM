package com.rdm.remote.desktop.manager.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "servers",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("clientId"),
        Index("name"),
        Index("ip")
    ]
)
data class ServerEntity(
    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    val id: Long = 0,

    @SerializedName("clientId")
    val clientId: Long,

    @SerializedName("name")
    val name: String,

    @SerializedName("ip")
    val ip: String,

    @SerializedName("port")
    val port: Int = 3389,

    @SerializedName("domain")
    val domain: String = "",

    @SerializedName("login")
    val login: String = "",

    @SerializedName("password")
    val password: String = "",

    @SerializedName("notes")
    val notes: String = "",

    @SerializedName("resolution")
    val resolution: String = "1920x1080",

    @SerializedName("soundRedirection")
    val soundRedirection: Int = 0, // 0 = local, 1 = remote, 2 = none

    @SerializedName("adminSession")
    val adminSession: Boolean = false,

    @SerializedName("colorHex")
    val colorHex: String = "#0078D7",

    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @SerializedName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Returns full address string in "ip:port" or "ip" if port == 3389
     */
    fun formattedAddress(): String {
        return if (port == 3389 || port <= 0) ip else "$ip:$port"
    }

    /**
     * Formats username with domain if present, e.g. "DOMAIN\username"
     */
    fun formattedUsername(): String {
        return if (domain.isNotBlank()) "$domain\\$login" else login
    }
}
