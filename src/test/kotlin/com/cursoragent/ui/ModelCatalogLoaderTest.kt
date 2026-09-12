package com.cursoragent.ui

import com.cursoragent.service.ModelCatalogState
import com.cursoragent.service.ModelOption
import com.cursoragent.session.SessionTabs
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities

class ModelCatalogLoaderTest {
    private class Fixture {
        val work = mutableListOf<() -> Unit>()
        val delivery = mutableListOf<() -> Unit>()
        val futures = mutableListOf<CompletableFuture<Unit>>()
        val states = mutableListOf<ModelCatalogState>()
        var active = true
        var result: ModelCatalogState = ModelCatalogState.Failed
        var fail = false
        val loader = ModelCatalogLoader(
            fetch = { if (fail) error("fixture launch failed") else result },
            execute = { work.add(it); CompletableFuture<Unit>().also(futures::add) },
            dispatch = { delivery.add(it) },
            isActive = { active },
            show = { assertTrue(SwingUtilities.isEventDispatchThread()); states.add(it) },
        )
    }

    @Test
    fun `loading is immediate while failure empty success and retry are asynchronous`() = SwingUtilities.invokeAndWait {
        val fixture = Fixture()
        val results = listOf(ModelCatalogState.Failed, ModelCatalogState.Loaded(emptyList()),
            ModelCatalogState.Loaded(listOf(ModelOption("auto", "Auto"))))
        for (result in results) {
            fixture.result = result
            fixture.loader.load()
            assertEquals(ModelCatalogState.Loading, fixture.states.last())
            fixture.work.removeAt(0)()
            assertEquals(ModelCatalogState.Loading, fixture.states.last())
            fixture.delivery.removeAt(0)()
            assertEquals(result, fixture.states.last())
        }
        fixture.fail = true
        fixture.loader.load()
        fixture.work.removeAt(0)()
        fixture.delivery.removeAt(0)()
        assertEquals(ModelCatalogState.Failed, fixture.states.last())
    }

    @Test
    fun `retry and print ACP print roundtrip reject an already queued old result`() = SwingUtilities.invokeAndWait {
        for (switchTransport in listOf(false, true)) {
            val fixture = Fixture()
            fixture.loader.load()
            fixture.work.removeAt(0)()
            if (switchTransport) {
                fixture.active = false
                fixture.loader.cancel()
                fixture.loader.load()
                assertEquals(0, fixture.work.size)
                fixture.active = true
            }
            fixture.loader.load()
            assertTrue(fixture.futures.first().isCancelled)
            val ready = ModelCatalogState.Loaded(listOf(ModelOption("saved", "Saved")))
            fixture.result = ready
            fixture.work.removeAt(0)()
            fixture.delivery.removeAt(1)()
            fixture.delivery.removeAt(0)()
            assertEquals(listOf(ModelCatalogState.Loading, ModelCatalogState.Loading, ready), fixture.states)
        }
    }

    @Test
    fun `disposed or closed owner ignores late results while tab selection leaves owner alive`() = SwingUtilities.invokeAndWait {
        for (change in listOf("dispose", "close", "select")) {
            val tabs = SessionTabs()
            val owner = tabs.snapshot().selectedId
            val fixture = Fixture()
            fixture.loader.load()
            fixture.work.removeAt(0)()
            if (change == "select") tabs.open() else if (change == "close") tabs.close(owner)
            fixture.active = change != "dispose" && tabs.snapshot().tabs.any { it.id == owner }
            if (change == "dispose") fixture.loader.cancel()
            fixture.delivery.removeAt(0)()
            assertEquals(if (change == "select") ModelCatalogState.Failed else ModelCatalogState.Loading, fixture.states.last())
        }
    }
}
