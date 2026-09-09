package com.cursoragent.ui.browser

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandler
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities

internal class ManualBrowserPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    val address = JBTextField().apply {
        emptyText.text = "https:// または http:// から始まるURL"
        accessibleContext.accessibleName = "URL"
    }
    private val status = JBTextArea("URLを入力するとページを開きます。Agentによる操作・会話への共有は未接続です。").apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        rows = 2
        border = JBUI.Borders.emptyTop(4)
        accessibleContext.accessibleName = "ブラウザーの状態"
    }
    private val back = JButton("戻る").apply { isEnabled = false }
    private val forward = JButton("進む").apply { isEnabled = false }
    private val reload = JButton("再読込").apply { isEnabled = false }
    private val go = JButton("移動")
    private var browser: JBCefBrowser? = null
    @Volatile private var disposed = false

    init {
        border = JBUI.Borders.empty(6)
        add(JPanel(BorderLayout(6, 0)).apply {
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(back)
                add(forward)
                add(reload)
            }, BorderLayout.WEST)
            add(address, BorderLayout.CENTER)
            add(go, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        add(status, BorderLayout.SOUTH)
        try {
            if (JBCefApp.isSupported()) {
                val created = JBCefBrowser("about:blank")
                browser = created
                installHandlers(created)
                add(created.component, BorderLayout.CENTER)
            } else {
                unavailable("このIDEでは内蔵ブラウザー（JCEF）を利用できません。")
            }
        } catch (_: Exception) {
            releaseBrowser()
            unavailable("内蔵ブラウザーを起動できませんでした。閉じてから開き直してください。")
        } catch (_: LinkageError) {
            releaseBrowser()
            unavailable("このIDEの実行環境では内蔵ブラウザー（JCEF）を利用できません。")
        }
        address.addActionListener { navigate(address.text) }
        go.addActionListener { navigate(address.text) }
        back.addActionListener { browser?.cefBrowser?.goBack() }
        forward.addActionListener { browser?.cefBrowser?.goForward() }
        reload.addActionListener { browser?.cefBrowser?.reload() }
    }

    private fun unavailable(message: String) {
        status.text = message
        address.isEnabled = false
        go.isEnabled = false
    }

    private fun navigate(value: String) {
        if (disposed || project.isDisposed) return
        val url = BrowserUrl.normalize(value)
        if (url == null) {
            status.text = "認証情報を含まない有効なHTTP/HTTPS URLを入力してください。"
            return
        }
        address.text = url
        browser?.loadURL(url)
    }

    private fun installHandlers(view: JBCefBrowser) {
        val client = view.jbCefClient
        val cef = view.cefBrowser
        // Use the browser-scoped wrapper: JBCefBrowser already installs native handlers of these types.
        client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onAddressChange(browser: CefBrowser, frame: CefFrame, url: String) {
                if (frame.isMain) onUi { address.text = url.takeUnless { it == "about:blank" }.orEmpty() }
            }
        }, cef)
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(browser: CefBrowser, loading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
                val hasPage = browser.url != "about:blank"
                onUi {
                    back.isEnabled = canGoBack
                    forward.isEnabled = canGoForward
                    reload.isEnabled = hasPage
                }
            }

            override fun onLoadStart(browser: CefBrowser, frame: CefFrame, transition: CefRequest.TransitionType) {
                if (frame.isMain && frame.url != "about:blank") onUi { status.text = "読み込み中…" }
            }

            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain && frame.url != "about:blank" && httpStatusCode >= 200) onUi {
                    status.text = if (httpStatusCode >= 400) "ページの応答エラー（HTTP $httpStatusCode）。URLを確認して再読込してください。" else "読み込み完了"
                }
            }

            override fun onLoadError(browser: CefBrowser, frame: CefFrame, errorCode: CefLoadHandler.ErrorCode, errorText: String, failedUrl: String) {
                if (frame.isMain && errorCode != CefLoadHandler.ErrorCode.ERR_ABORTED) onUi {
                    status.text = "ページを読み込めませんでした（${errorCode.name}）。URLや接続を確認してください。"
                }
            }
        }, cef)
        client.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(browser: CefBrowser, frame: CefFrame, request: CefRequest, userGesture: Boolean, isRedirect: Boolean): Boolean {
                val allowed = request.url == "about:blank" || BrowserUrl.normalize(request.url) != null
                if (!allowed) onUi { status.text = "このURLへの移動は許可されていません。HTTP/HTTPSを指定してください。" }
                return disposed || project.isDisposed || !allowed
            }

            override fun onRenderProcessTerminated(
                browser: CefBrowser,
                status: CefRequestHandler.TerminationStatus,
                errorCode: Int,
                errorString: String,
            ) {
                onUi { this@ManualBrowserPanel.status.text = "ページの表示処理が終了しました。再読込してください。" }
            }

            override fun onOpenURLFromTab(browser: CefBrowser, frame: CefFrame, targetUrl: String, userGesture: Boolean): Boolean {
                onUi { navigate(targetUrl) }
                return true
            }
        }, cef)
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(browser: CefBrowser, frame: CefFrame, targetUrl: String, targetFrameName: String): Boolean {
                onUi { status.text = "別ウィンドウは開けません。リンクのURLを入力して移動してください。" }
                return true
            }
        }, cef)
    }

    private fun onUi(action: () -> Unit) {
        SwingUtilities.invokeLater { if (!disposed && !project.isDisposed && browser != null) action() }
    }

    private fun releaseBrowser() {
        val current = browser
        browser = null
        // The implicit JBCefClient and its handler registrations belong to this browser.
        current?.let { Disposer.dispose(it) }
    }

    override fun dispose() {
        disposed = true
        releaseBrowser()
    }
}
