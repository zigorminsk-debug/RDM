package com.rdm.remote.desktop.manager.utils

import com.rdm.remote.desktop.manager.data.model.ClientEntity
import com.rdm.remote.desktop.manager.data.model.ServerEntity

object SampleData {

    data class ClientWithServers(
        val client: ClientEntity,
        val servers: List<ServerEntity>
    )

    fun getSampleData(): List<ClientWithServers> {
        val client1 = ClientEntity(
            id = 0,
            name = "ООО «ТехноПром»",
            description = "Главный офис и филиалы компании",
            colorHex = "#0078D7"
        )
        val servers1 = listOf(
            ServerEntity(
                clientId = 0,
                name = "DC-01 (Контроллер домена)",
                ip = "192.168.10.10",
                port = 3389,
                domain = "CORP",
                login = "Administrator",
                password = "Password123!",
                notes = "Active Directory, DNS, DHCP",
                resolution = "1920x1080",
                soundRedirection = 0,
                adminSession = true,
                colorHex = "#0078D7"
            ),
            ServerEntity(
                clientId = 0,
                name = "SQL-PROD (База 1С и ERP)",
                ip = "192.168.10.20",
                port = 3389,
                domain = "CORP",
                login = "sql_admin",
                password = "SqlSecurePass#2026",
                notes = "MS SQL Server 2022 Enterprise",
                resolution = "1920x1080",
                soundRedirection = 2,
                adminSession = false,
                colorHex = "#D83B01"
            ),
            ServerEntity(
                clientId = 0,
                name = "RDS-Farm-01 (Терминальный сервер)",
                ip = "rds.technoprom.local",
                port = 3389,
                domain = "CORP",
                login = "terminal_user",
                password = "UserRdsPass2026",
                notes = "Удаленные рабочие столы для сотрудников",
                resolution = "1920x1080",
                soundRedirection = 0,
                adminSession = false,
                colorHex = "#107C41"
            )
        )

        val client2 = ClientEntity(
            id = 0,
            name = "АО «ФинансКонсалт»",
            description = "Финансовый сектор и аналитика",
            colorHex = "#5C2D91"
        )
        val servers2 = listOf(
            ServerEntity(
                clientId = 0,
                name = "APP-SERVER-01",
                ip = "10.0.50.15",
                port = 33890,
                domain = "FINANCE",
                login = "sysadmin",
                password = "FinAdminSec#99",
                notes = "Кастомный порт RDP: 33890",
                resolution = "1920x1080",
                soundRedirection = 0,
                adminSession = true,
                colorHex = "#5C2D91"
            ),
            ServerEntity(
                clientId = 0,
                name = "BACKUP-STORAGE",
                ip = "10.0.50.80",
                port = 3389,
                domain = "FINANCE",
                login = "backup_operator",
                password = "BackupOperatorPass!",
                notes = "Veeam Backup & Replication",
                resolution = "1280x720",
                soundRedirection = 2,
                adminSession = false,
                colorHex = "#008272"
            )
        )

        val client3 = ClientEntity(
            id = 0,
            name = "Home Lab & Cloud",
            description = "Домашняя лаборатория и тестовые стенды",
            colorHex = "#D83B01"
        )
        val servers3 = listOf(
            ServerEntity(
                clientId = 0,
                name = "WinServer-HyperV",
                ip = "192.168.1.200",
                port = 3389,
                domain = "WORKGROUP",
                login = "admin",
                password = "LabPassword!",
                notes = "Hyper-V виртуалки и тесты",
                resolution = "1920x1080",
                soundRedirection = 0,
                adminSession = true,
                colorHex = "#E81123"
            )
        )

        return listOf(
            ClientWithServers(client1, servers1),
            ClientWithServers(client2, servers2),
            ClientWithServers(client3, servers3)
        )
    }
}
