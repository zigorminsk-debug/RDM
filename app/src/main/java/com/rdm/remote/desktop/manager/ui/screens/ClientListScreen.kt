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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rdm.remote.desktop.manager.ui.components.ClientCard
import com.rdm.remote.desktop.manager.ui.components.ClientEditDialog
import com.rdm.remote.desktop.manager.ui.components.RdmSearchBar
import com.rdm.remote.desktop.manager.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientListScreen(
    viewModel: MainViewModel,
    onClientClick: (Long) -> Unit,
    onAllServersClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val searchQuery by viewModel.clientSearchQuery.collectAsStateWithLifecycle()
    val editingClient by viewModel.editingClient.collectAsStateWithLifecycle()
    val deletingClient by viewModel.deletingClient.collectAsStateWithLifecycle()

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
                placeholder = "Поиск клиента или описания..."
            )

            // Hint Text
            Text(
                text = "Нажмите на клиента, чтобы открыть список серверов. Долгое нажатие — редактировать.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Client List or Empty State
            if (clients.isEmpty()) {
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
                            imageVector = Icons.Default.FolderShared,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Клиенты не найдены" else "Список клиентов пуст",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Попробуйте изменить поисковый запрос" else "Добавьте первого клиента или загрузите демо-данные для быстрого старта",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (searchQuery.isBlank()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(onClick = { viewModel.loadDemoData() }) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Загрузить демо-данные")
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
}
