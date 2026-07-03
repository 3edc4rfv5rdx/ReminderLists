package com.reminderlists.ui.components

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// Shared drag-to-reorder state for LazyColumn rows with a drag handle (TZ 3.3).
// Tracks the dragged row by its LazyColumn key; onMove(from, to) swaps rows in the caller's
// state while dragging, onDrop persists the final order.
class DragReorderState(
    private val listState: LazyListState,
    private val canDragOver: (LazyListItemInfo) -> Boolean,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onDrop: () -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    private var draggedDelta by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)

    private val draggingInfo: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }

    // Visual translationY for the dragged row relative to its current laid-out slot.
    val draggingOffset: Float
        get() = draggingInfo?.let { initialOffset + draggedDelta - it.offset } ?: 0f

    fun start(key: Any) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        draggingKey = key
        initialOffset = info.offset
        draggedDelta = 0f
    }

    fun drag(deltaY: Float) {
        draggedDelta += deltaY
        val dragging = draggingInfo ?: return
        val middle = dragging.offset + draggingOffset + dragging.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            info.key != draggingKey &&
                canDragOver(info) &&
                middle.toInt() in info.offset..(info.offset + info.size)
        } ?: return
        onMove(dragging.index, target.index)
    }

    fun drop() {
        if (draggingKey != null) onDrop()
        draggingKey = null
        draggedDelta = 0f
        initialOffset = 0
    }
}

@Composable
fun rememberDragReorderState(
    listState: LazyListState,
    canDragOver: (LazyListItemInfo) -> Boolean,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDrop: () -> Unit,
): DragReorderState = remember(listState) {
    DragReorderState(listState, canDragOver, onMove, onDrop)
}
