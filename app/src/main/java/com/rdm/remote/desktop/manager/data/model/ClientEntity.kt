package com.rdm.remote.desktop.manager.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    val id: Long = 0,

    @SerializedName("name")
    val name: String,

    @SerializedName("description")
    val description: String = "",

    @SerializedName("colorHex")
    val colorHex: String = "#0078D7",

    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @SerializedName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
)

data class ClientWithCount(
    val id: Long,
    val name: String,
    val description: String,
    val colorHex: String,
    val createdAt: Long,
    val updatedAt: Long,
    val serverCount: Int
)
