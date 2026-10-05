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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rdm.remote.desktop.manager.ui.components.RdpLaunchDialog
import com.rdm.remote.desktop.manager.ui.components.RdmSearchBar
import com.rdm.remote.desktop.manager.ui.components.ServerCard
import com.rdm.remote.desktop.manager.ui.components.ServerEditDialog
import com.rdm.remote.desktop.manager.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    clientId: Long,
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
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
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Добавить сервер",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddServerDialog(clientId) },
                icon = { Icon(Icons.Default.Add, contentDescription = "Добавить сервер") },
                text = { Text("Добавить сервер") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            RdmSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setServerSearchQuery(it) },
                placeholder = "Поиск по названию, IP, логину, домену..."
            )

            // Hint Text for click / long click actions
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Нажатие — запуск RDP. Долгое нажатие — редактирование.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Servers List or Empty State
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
                            imageVector = Icons.Default.DesktopAccessDisabled,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Серверы не найдены" else "Нет добавленных серверов",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Попробуйте изменить поисковый фильтр" else "Нажмите «Добавить сервер», чтобы создать первую карточку подключения",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (searchQuery.isBlank()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(onClick = { viewModel.openAddServerDialog(clientId) }) {
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
