package com.cursoragent.ui.composer.mention

import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class MentionResolverTest {
    @Test
    fun `missing legacy words are omitted while explicit missing attachments remain visible`() {
        val project = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Project::class.java)) { _, method, _ ->
            check(method.name == "isDefault")
            true // No project directory: the actual resolver cannot find either reference.
        } as Project
        val resolver = MentionResolver(project)
        assertNull(resolver.buildFileAndFolderContext("Why does @Composable recompose? @missing/"))
        val explicit = listOf(
            Mention(MentionKind.FILE, "ファイル", "./Composable"),
            Mention(MentionKind.FOLDER, "フォルダ", "missing/"),
        )
        assertEquals(
            "@./Composable: (file unavailable; no contents attached)\n\n@missing/: (folder unavailable; no listing attached)",
            resolver.buildFileAndFolderContext("@Composable @./Composable @missing/", explicit),
        )
        assertNull(resolver.buildFileAndFolderContext("@Composable"))
    }
}
