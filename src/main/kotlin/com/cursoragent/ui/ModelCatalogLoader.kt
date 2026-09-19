package com.cursoragent.ui

import com.cursoragent.service.ModelCatalogState
import java.util.concurrent.Future

/** Per-tab request ownership. Entry points and delivered state run on EDT. */
internal class ModelCatalogLoader(
    private val fetch: () -> ModelCatalogState,
    private val execute: (() -> Unit) -> Future<*>,
    private val dispatch: (() -> Unit) -> Unit,
    private val isActive: () -> Boolean,
    private val show: (ModelCatalogState) -> Unit,
) {
    private var generation = 0L
    private var pending: Future<*>? = null

    fun load() {
        if (!isActive()) return
        cancel()
        val request = generation
        show(ModelCatalogState.Loading)
        pending = execute {
            val result = try {
                fetch()
            } catch (_: Exception) {
                ModelCatalogState.Failed
            }
            dispatch {
                if (request == generation && isActive()) {
                    pending = null
                    show(result)
                }
            }
        }
    }

    fun cancel() {
        generation++
        pending?.cancel(true)
        pending = null
    }
}
