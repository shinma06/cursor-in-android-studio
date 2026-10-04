package com.cursoragent.ui.composer

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.JPanel
import javax.swing.KeyStroke

class ModelParameterKeymapTest {
    @Test
    fun `native action uses platform defaults and local registration follows remap and removal`() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder("model parameter keymap").fixture
        fixture.setUp()
        try {
            runInEdtAndWait {
                val id = "CursorAgent.CycleModelParameter"
                val action = requireNotNull(ActionManager.getInstance().getAction(id))
                val manager = KeymapManagerEx.getInstanceEx()
                val original = manager.activeKeymap
                val expected = mapOf(
                    "\$default" to listOf("ctrl shift SLASH", "ctrl alt SLASH"),
                    "Default for XWin" to listOf("ctrl shift SLASH"),
                    "Mac OS X" to listOf("meta shift SLASH"),
                    "Mac OS X 10.5+" to listOf("meta shift SLASH"),
                )
                expected.forEach { (name, keys) ->
                    assertEquals(keys.map { KeyStroke.getKeyStroke(it) }.toSet(),
                        manager.getKeymap(name)!!.getShortcuts(id).map { (it as KeyboardShortcut).firstKeyStroke }.toSet(), name)
                }
                val customized = original.deriveKeymap("Model parameter test")
                val component = JPanel()
                try {
                    manager.activeKeymap = customized
                    action.registerCustomShortcutSet(action.shortcutSet, component)
                    val local = com.intellij.openapi.actionSystem.ex.ActionUtil.getActions(component).single()
                    customized.removeAllActionShortcuts(id)
                    assertTrue(local.shortcutSet.shortcuts.isEmpty())
                    val remapped = KeyboardShortcut(KeyStroke.getKeyStroke("ctrl alt F12"), null)
                    customized.addShortcut(id, remapped)
                    assertEquals(listOf(remapped), local.shortcutSet.shortcuts.toList())
                    customized.removeAllActionShortcuts(id)
                    assertTrue(local.shortcutSet.shortcuts.isEmpty())
                } finally {
                    action.unregisterCustomShortcutSet(component)
                    manager.activeKeymap = original
                }
                assertTrue(com.intellij.openapi.actionSystem.ex.ActionUtil.getActions(component).isEmpty())
            }
        } finally {
            runInEdtAndWait { fixture.tearDown() }
        }
    }
}
