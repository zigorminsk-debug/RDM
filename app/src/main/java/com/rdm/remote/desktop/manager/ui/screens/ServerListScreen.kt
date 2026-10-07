package com.rdm.remote.desktop.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rdm.remote.desktop.manager.data.model.ServerEntity
import com.rdm.remote.desktop.manager.ui.components.RdmSearchBar
import com.rdm.remote.desktop.manager.ui.components.RdpLaunchDialog
import com.rdm.remote.desktop.manager.ui.components.ServerCard
import com.rdm.remote.desktop.manager.ui.components.ServerEditDialog
import com.rdm.remote.desktop.manager.ui.viewmodel.MainViewModel
import com.rdm.remote.desktop.manager.utils.RdpLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    clientId: Long,
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(clientId) {
        viewModel.selectClient(clientId)
    }

    val client by viewModel.currentClient.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val searchQuery by viewModel.serverSearchQuery.collectAsStateWithLifecycle()
    val pingResults by viewModel.pingResults.collectAsStateWithLifecycle()

    val editingServer by viewModel.editingServer.collectAsStateWithLifecycle()
    val launchingServer by viewModel.launchingServer.collectAsStateWithLifecycle()
    val deletingServer by viewModel.deletingServer.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = client?.name ?: "Серверы клиента",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (client?.description?.isNotBlank() == true) {
                            Text(
                                text = client?.description ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openAddServerDialog(clientId) }) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить сервер")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddServerDialog(clientId) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить сервер")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            RdmSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setServerSearchQuery(it) },
                placeholderText = "Поиск по серверу, IP, логину...",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Content List
            if (servers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = if (searchQuery.isBlank()) {
                                "У этого клиента пока нет добавленных серверов"
                            } else {
                                "Ничего не найдено по запросу «$searchQuery»"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (searchQuery.isBlank()) {
                            Button(
                                onClick = { viewModel.openAddServerDialog(clientId) }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Добавить сервер")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = servers,
                        key = { it.id }
                    ) { server ->
                        ServerCard(
                            server = server,
                            pingStatus = pingResults[server.id],
                            onClick = {
                                RdpLauncher.connectToServer(context, server)
                            },
                            onLongClick = { viewModel.openEditServerDialog(server) },
                            onEdit = { viewModel.openEditServerDialog(server) },
                            onDuplicate = { viewModel.duplicateServer(server) },
                            onDelete = { viewModel.promptDeleteServer(server) },
                            onPingTest = { viewModel.testServerConnection(server) },
                            onOptions = { viewModel.openRdpLaunchDialog(server) }
                        )
                    }
                }
            }
        }
    }

    // Server Add / Edit Dialog
    editingServer?.let { server ->
        ServerEditDialog(
            server = server,
            onDismiss = { viewModel.closeServerDialog() },
            onSave = { name, ip, port, domain, login, password, notes, resolution, sound, admin, color ->
                viewModel.saveServer(name, ip, port, domain, login, password, notes, resolution, sound, admin, color)
            }
        )
    }

    // RDP Launch Dialog
    launchingServer?.let { server ->
        RdpLaunchDialog(
            server = server,
            onLaunchInApp = {
                RdpLauncher.connectToServer(context, it)
                viewModel.closeRdpLaunchDialog()
            },
            onDismiss = { viewModel.closeRdpLaunchDialog() }
        )
    }

    // Server Delete Confirmation Dialog
    deletingServer?.let { server ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteServer() },
            title = { Text("Удалить сервер?") },
            text = { Text("Вы действительно хотите удалить карточку сервера «${server.name}» (${server.formattedAddress()})?") },
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
