package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/** Reusable, ID/URI-based multi-selection state for all Browse entity types. */
class BrowseSelectionState {
    var active by mutableStateOf(false)
        private set
    var kind by mutableStateOf(SelectionKind.SONG)
        private set
    var contextKey by mutableStateOf("")
        private set
    var selectedKeys by mutableStateOf<Set<String>>(emptySet())
        private set

    val count: Int get() = selectedKeys.size

    fun begin(kind: SelectionKind, contextKey: String, key: String? = null) {
        this.kind = kind
        this.contextKey = contextKey
        active = true
        selectedKeys = if (key == null) emptySet() else setOf(key)
    }

    fun toggle(key: String) {
        if (!active) return
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
    }

    fun selectAll(keys: Collection<String>) {
        if (!active) return
        selectedKeys = keys.toSet()
    }

    fun clearSelection() {
        selectedKeys = emptySet()
    }

    fun exit() {
        active = false
        selectedKeys = emptySet()
        contextKey = ""
    }

    fun restore(active: Boolean, kind: SelectionKind, contextKey: String, keys: Set<String>) {
        this.active = active
        this.kind = kind
        this.contextKey = contextKey
        this.selectedKeys = keys
    }

    companion object {
        val Saver: Saver<BrowseSelectionState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.active,
                    state.kind.name,
                    state.contextKey,
                    state.selectedKeys.toList()
                )
            },
            restore = { values ->
                val state = BrowseSelectionState()
                val active = values.getOrNull(0) as? Boolean ?: false
                val kind = (values.getOrNull(1) as? String)?.let { name ->
                    runCatching { SelectionKind.valueOf(name) }.getOrDefault(SelectionKind.SONG)
                } ?: SelectionKind.SONG
                val context = values.getOrNull(2) as? String ?: ""
                val keys = (values.getOrNull(3) as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
                state.restore(active, kind, context, keys)
                state
            }
        )
    }
}

enum class SelectionKind {
    SONG, FOLDER, ALBUM, ARTIST, PLAYLIST, SMART
}
