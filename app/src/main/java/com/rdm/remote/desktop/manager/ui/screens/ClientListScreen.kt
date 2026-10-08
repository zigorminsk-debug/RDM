package com.rdm.remote.desktop.manager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rdm.remote.desktop.manager.ui.components.ClientCard
import com.rdm.remote.desktop.manager.ui.components.ClientEditDialog
import com.rdm.remote.desktop.manager.ui.components.RdmSearchBar
import com.rdm.remote.desktop.manager.ui.components.UpdateDialog
import com.rdm.remote.desktop.manager.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientListScreen(
    viewModel: MainViewModel,
    onClientClick: (Long) -> Unit,
    onAllServersClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val searchQuery by viewModel.clientSearchQuery.collectAsStateWithLifecycle()
    val editingClient by viewModel.editingClient.collectAsStateWithLifecycle()
    val deletingClient by viewModel.deletingClient.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()

    // Automatically check for updates silently on startup
    LaunchedEffect(Unit) {
        viewModel.checkForUpdates(silentIfUpToDate = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "RDM — Клиенты",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.checkForUpdates(silentIfUpToDate = false) }) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Проверить обновления",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onAllServersClick) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = "Все серверы",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                onClick = { viewModel.openAddClientDialog() },
                icon = { Icon(Icons.Default.Add, contentDescription = "Добавить клиента") },
                text = { Text("Добавить клиента") },
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
                onQueryChange = { viewModel.setClientSearchQuery(it) },
                placeholder = "Поиск по клиентам и проектам...",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Content List
            if (clients.isEmpty()) {
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
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = if (searchQuery.isBlank()) {
                                "Список клиентов пуст.\nДобавьте первого клиента или загрузите примеры в Настройках."
                            } else {
                                "Ничего не найдено по запросу «$searchQuery»"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (searchQuery.isBlank()) {
                            Button(onClick = { viewModel.openAddClientDialog() }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Создать клиента")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(clients, key = { it.id }) { clientWithCount ->
                        ClientCard(
                            clientWithCount = clientWithCount,
                            onClick = {
                                viewModel.selectClient(clientWithCount.id)
                                onClientClick(clientWithCount.id)
                            },
                            onEdit = { viewModel.openEditClientDialog(it) },
                            onDelete = { viewModel.promptDeleteClient(it) }
                        )
                    }
                }
            }
        }
    }

    // Client Add / Edit Dialog
    editingClient?.let { client ->
        ClientEditDialog(
            client = client,
            onDismiss = { viewModel.closeClientDialog() },
            onSave = { name, description, colorHex ->
                viewModel.saveClient(name, description, colorHex)
            }
        )
    }

    // Client Delete Confirmation Dialog
    deletingClient?.let { target ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteClient() },
            title = { Text("Удалить клиента?") },
            text = {
                Text("Вы действительно хотите удалить клиента «${target.name}»? Все привязанные серверы (${target.serverCount} шт.) также будут удалены.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteClient() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteClient() }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Update Dialog
    UpdateDialog(
        status = updateStatus,
        onDismiss = { viewModel.dismissUpdateDialog() },
        onDownloadAndInstall = { downloadUrl, versionName ->
            viewModel.downloadAndInstallUpdate(context, downloadUrl, versionName)
        },
        onInstallLocalFile = {
            viewModel.installDownloadedUpdate(context)
        }
    )
}
