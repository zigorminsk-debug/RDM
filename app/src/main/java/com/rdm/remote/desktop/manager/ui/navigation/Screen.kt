package com.rdm.remote.desktop.manager.ui.navigation

sealed class Screen(val route: String) {
    object ClientList : Screen("client_list")
    object ServerList : Screen("server_list/{clientId}") {
        fun createRoute(clientId: Long) = "server_list/$clientId"
    }
    object AllServers : Screen("all_servers")
    object Settings : Screen("settings")
    object RdpSession : Screen("rdp_session/{serverId}") {
        fun createRoute(serverId: Long) = "rdp_session/$serverId"
    }
}
