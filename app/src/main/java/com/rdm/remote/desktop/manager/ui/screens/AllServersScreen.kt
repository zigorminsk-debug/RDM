package com.rdm.remote.desktop.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rdm.remote.desktop.manager.data.model.ServerEntity
import com.rdm.remote.desktop.manager.ui.components.RdpLaunchDialog
import com.rdm.remote.desktop.manager.ui.components.RdmSearchBar
import com.rdm.remote.desktop.manager.ui.components.ServerCard
import com.rdm.remote.desktop.manager.ui.components.ServerEditDialog
import com.rdm.remote.desktop.manager.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllServersScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onLaunchInApp: (ServerEntity) -> Unit
) {
    val servers by viewModel.allServers.collectAsStateWithLifecycle()
    val searchQuery by viewModel.serverSearchQuery.collectAsStateWithLifecycle()
    val pingResults by viewModel.pingResults.collectAsStateWithLifecycle()

    val editingServer by viewModel.editingServer.collectAsStateWithLifecycle()
    val launchingServer by viewModel.launchingServer.collectAsStateWithLifecycle()
    val deletingServer by viewModel.deletingServer.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Все серверы",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            RdmSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setServerSearchQuery(it) },
                placeholder = "Глобальный поиск серверов..."
            )

            if (servers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Серверы не найдены" else "Нет добавленных серверов",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Добавьте серверы через карточки клиентов",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(servers, key = { it.id }) { server ->
                        ServerCard(
                            server = server,
                            pingStatus = pingResults[server.id],
                            onClick = { viewModel.openRdpLaunchDialog(server) },
                            onLongClick = { viewModel.openEditServerDialog(server) },
                            onEdit = { viewModel.openEditServerDialog(server) },
                            onDuplicate = { viewModel.duplicateServer(server) },
                            onDelete = { viewModel.promptDeleteServer(server) },
                            onPingTest = { viewModel.testServerConnection(server) }
                        )
                    }
                }
            }
        }
    }

    editingServer?.let { server ->
        ServerEditDialog(
            server = server,
            onDismiss = { viewModel.closeServerDialog() },
            onSave = { name, ip, port, domain, login, password, notes, resolution, sound, admin, color ->
                viewModel.saveServer(name, ip, port, domain, login, password, notes, resolution, sound, admin, color)
            }
        )
    }

    launchingServer?.let { server ->
        RdpLaunchDialog(
            server = server,
            onLaunchInApp = { onLaunchInApp(it) },
            onDismiss = { viewModel.closeRdpLaunchDialog() }
        )
    }

    deletingServer?.let { server ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteServer() },
            title = { Text("Удалить сервер?") },
            text = { Text("Вы действительно хотите удалить карточку сервера «${server.name}»?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteServer() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteServer() }) {
                    Text("Отмена")
                }
            }
        )
    }
}
