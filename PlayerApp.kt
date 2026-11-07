package com.example.offlineplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

// ---------------------------------------------------------------------------
// הרשאות
// ---------------------------------------------------------------------------

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasMediaPermission(context: android.content.Context): Boolean {
    fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    return if (Build.VERSION.SDK_INT >= 33) {
        granted(Manifest.permission.READ_MEDIA_AUDIO) || granted(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit, onSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.perm_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.perm_body),
            modifier = Modifier.padding(vertical = 16.dp),
            textAlign = TextAlign.Center,
        )
        Button(onClick = onGrant) { Text(stringResource(R.string.perm_grant)) }
        OutlinedButton(onClick = onSettings, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.perm_settings))
        }
    }
}

// ---------------------------------------------------------------------------
// מסך ראשי
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerApp(vm: PlayerViewModel) {
    val context = LocalContext.current

    var granted by remember { mutableStateOf(hasMediaPermission(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        granted = hasMediaPermission(context)
        if (granted) vm.refresh()
    }
    LaunchedEffect(Unit) {
        if (granted) vm.refresh() else launcher.launch(requiredPermissions())
    }

    if (!granted) {
        Scaffold { padding ->
            Box(Modifier.padding(padding)) {
                PermissionScreen(
                    onGrant = { launcher.launch(requiredPermissions()) },
                    onSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                        )
                    },
                )
            }
        }
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openPlaylist by rememberSaveable { mutableStateOf<String?>(null) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf<MediaFile?>(null) }
    var showNewPlaylist by remember { mutableStateOf(false) }

    val audio = remember(vm.library) { vm.library.filter { !it.isVideo } }
    val video = remember(vm.library) { vm.library.filter { it.isVideo } }
    val byId = remember(vm.library) { vm.library.associateBy { it.id } }

    if (showNowPlaying && vm.currentId != null) {
        BackHandler { showNowPlaying = false }
        NowPlayingScreen(vm = vm, onClose = { showNowPlaying = false })
    } else {
        BackHandler(enabled = openPlaylist != null) { openPlaylist = null }

        Scaffold(
            topBar = {
                val playlist = openPlaylist
                TopAppBar(
                    title = { Text(playlist ?: stringResource(R.string.app_name)) },
                    navigationIcon = {
                        if (playlist != null) {
                            IconButton(onClick = { openPlaylist = null }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        }
                    },
                    actions = {
                        if (playlist != null) {
                            IconButton(onClick = {
                                vm.deletePlaylist(playlist)
                                openPlaylist = null
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete_playlist)
                                )
                            }
                        } else {
                            IconButton(onClick = { vm.refresh() }) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.refresh)
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                Column {
                    if (vm.currentId != null) {
                        MiniPlayer(vm = vm, onClick = { showNowPlaying = true })
                    }
                    NavigationBar {
                        NavTab(0, tab, stringResource(R.string.tab_audio), Icons.Filled.MusicNote) {
                            tab = 0; openPlaylist = null
                        }
                        NavTab(1, tab, stringResource(R.string.tab_video), Icons.Filled.Movie) {
                            tab = 1; openPlaylist = null
                        }
                        NavTab(2, tab, stringResource(R.string.tab_playlists), Icons.Filled.QueueMusic) {
                            tab = 2; openPlaylist = null
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                val playlist = openPlaylist
                when {
                    playlist != null -> {
                        val files = vm.playlists[playlist].orEmpty().mapNotNull { byId[it] }
                        MediaListScreen(
                            files = files,
                            loading = vm.loading,
                            currentId = vm.currentId,
                            searchable = false,
                            onPlay = { list, index ->
                                vm.play(list, index)
                                showNowPlaying = true
                            },
                            menuFor = { file ->
                                listOf(
                                    stringResource(R.string.remove_from_playlist) to {
                                        vm.removeFromPlaylist(playlist, file.id)
                                    }
                                )
                            },
                        )
                    }

                    tab == 0 || tab == 1 -> MediaListScreen(
                        files = if (tab == 0) audio else video,
                        loading = vm.loading,
                        currentId = vm.currentId,
                        searchable = true,
                        onPlay = { list, index ->
                            vm.play(list, index)
                            showNowPlaying = true
                        },
                        menuFor = { file ->
                            listOf(
                                stringResource(R.string.add_to_playlist) to { addTarget = file }
                            )
                        },
                    )

                    else -> PlaylistsScreen(
                        playlists = vm.playlists,
                        onOpen = { openPlaylist = it },
                        onNew = { showNewPlaylist = true },
                    )
                }
            }
        }
    }

    addTarget?.let { file ->
        AddToPlaylistDialog(
            playlists = vm.playlists.keys.toList(),
            onPick = { name ->
                vm.addToPlaylist(name, file.id)
                addTarget = null
            },
            onCreate = { name ->
                vm.createPlaylist(name)
                vm.addToPlaylist(name.trim(), file.id)
                addTarget = null
            },
            onDismiss = { addTarget = null },
        )
    }

    if (showNewPlaylist) {
        NameDialog(
            confirmLabel = stringResource(R.string.create),
            onConfirm = {
                vm.createPlaylist(it)
                showNewPlaylist = false
            },
            onDismiss = { showNewPlaylist = false },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavTab(
    index: Int,
    selected: Int,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = index == selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
    )
}

// ---------------------------------------------------------------------------
// רשימות קבצים
// ---------------------------------------------------------------------------

@Composable
private fun MediaListScreen(
    files: List<MediaFile>,
    loading: Boolean,
    currentId: String?,
    searchable: Boolean,
    onPlay: (List<MediaFile>, Int) -> Unit,
    menuFor: @Composable (MediaFile) -> List<Pair<String, () -> Unit>>,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(files, query) {
        if (query.isBlank()) files
        else files.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (searchable) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.search)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (shown.isEmpty() && !loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.empty_library))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(shown, key = { _, f -> f.id }) { index, file ->
                    MediaRow(
                        file = file,
                        isCurrent = file.id == currentId,
                        menu = menuFor(file),
                        onClick = { onPlay(shown, index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaRow(
    file: MediaFile,
    isCurrent: Boolean,
    menu: List<Pair<String, () -> Unit>>,
    onClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val subtitle = listOf(file.artist, formatTime(file.durationMs))
        .filter { it.isNotBlank() }
        .joinToString(" • ")

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                file.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            Icon(
                if (file.isVideo) Icons.Filled.Movie else Icons.Filled.MusicNote,
                contentDescription = null,
                tint = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    menu.forEach { (label, action) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                expanded = false
                                action()
                            },
                        )
                    }
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
// רשימות השמעה
// ---------------------------------------------------------------------------

@Composable
private fun PlaylistsScreen(
    playlists: Map<String, List<String>>,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Button(
            onClick = onNew,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.new_playlist), modifier = Modifier.padding(start = 8.dp))
        }
        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_playlists))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(playlists.entries.toList(), key = { it.key }) { (name, ids) ->
                    ListItem(
                        modifier = Modifier.clickable { onOpen(name) },
                        headlineContent = { Text(name) },
                        supportingContent = { Text(stringResource(R.string.items_count, ids.size)) },
                        leadingContent = { Icon(Icons.Filled.QueueMusic, contentDescription = null) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun NameDialog(
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_playlist)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.playlist_name)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AddToPlaylistDialog(
    playlists: List<String>,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_playlist)) },
        text = {
            Column {
                playlists.forEach { name ->
                    TextButton(onClick = { onPick(name) }, modifier = Modifier.fillMaxWidth()) {
                        Text(name, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.new_playlist)) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(newName) }, enabled = newName.isNotBlank()) {
                Text(stringResource(R.string.create_and_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ---------------------------------------------------------------------------
// נגן מוקטן
// ---------------------------------------------------------------------------

@Composable
private fun MiniPlayer(vm: PlayerViewModel, onClick: () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    vm.currentTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (vm.currentArtist.isNotBlank()) {
                    Text(
                        vm.currentArtist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            IconButton(onClick = vm::togglePlay) {
                Icon(
                    if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (vm.isPlaying) R.string.pause else R.string.play),
                )
            }
        }
    }
}
