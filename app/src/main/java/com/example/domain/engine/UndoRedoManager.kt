package com.example.domain.engine

import com.example.domain.model.ProjectTimeline

class UndoRedoManager(
    private val maxHistory: Int = 50
) {
    private val undoStack = ArrayDeque<ProjectTimeline>()
    private val redoStack = ArrayDeque<ProjectTimeline>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun recordBeforeMutation(previousState: ProjectTimeline) {
        if (undoStack.lastOrNull() == previousState) return
        undoStack.addLast(previousState)
        if (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    fun undo(currentState: ProjectTimeline): ProjectTimeline? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(currentState)
        return previous
    }

    fun redo(currentState: ProjectTimeline): ProjectTimeline? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(currentState)
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
