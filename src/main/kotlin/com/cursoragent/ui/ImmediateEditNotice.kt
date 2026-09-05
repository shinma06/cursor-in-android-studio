package com.cursoragent.ui

import com.intellij.ui.components.JBLabel

/** Shared explanation shown only in advanced options and plugin settings. */
class ImmediateEditNotice : JBLabel(
    "<html>標準設定でも、ファイルの編集は<br>" +
        "すぐに反映される場合があります。<br>" +
        "変更カードの Revert は、適用後の編集を<br>" +
        "元に戻す操作です。</html>",
)
