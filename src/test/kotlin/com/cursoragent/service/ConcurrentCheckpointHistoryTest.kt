package com.cursoragent.service

import com.cursoragent.settings.CheckpointHistoryState
import com.cursoragent.settings.CheckpointRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

class ConcurrentCheckpointHistoryTest {
    @Test
    fun `parallel tabs and persistence retain every checkpoint without sharing mutable state`() {
        val history = CheckpointHistoryState()
        val workers = (0..3).map { tab -> thread {
            repeat(100) { index ->
                history.addRecord(CheckpointRecord(id = "$tab-$index", chatId = "$tab"))
                history.getState().records.clear() // Persistence receives a detached snapshot.
            }
        } }
        workers.forEach { it.join() }
        assertEquals(400, history.getState().records.size)
        (0..3).forEach { assertEquals(100, history.recordsForChat("$it").size) }
        val record = CheckpointRecord(id = "copy", untrackedFilesAtSnapshot = mutableListOf("keep"))
        history.addRecord(record)
        record.untrackedFilesAtSnapshot.clear()
        history.findRecord("copy")!!.untrackedFilesAtSnapshot.clear()
        assertEquals(listOf("keep"), history.findRecord("copy")!!.untrackedFilesAtSnapshot)
    }
}
