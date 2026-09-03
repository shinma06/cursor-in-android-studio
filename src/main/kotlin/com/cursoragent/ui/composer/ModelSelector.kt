package com.cursoragent.ui.composer

import com.intellij.util.ui.JBUI
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox

class ModelSelector : JComboBox<String>(DefaultComboBoxModel(arrayOf("Default model"))) {
    init {
        isEnabled = false
        toolTipText = "Model selection will be available in a future update"
        preferredSize = JBUI.size(140, 28)
    }
}
