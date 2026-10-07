package com.cursoragent.ui.browser

import java.net.IDN
import java.net.URI

/** HTTP(S) only; parsing never resolves a hostname or opens a connection. */
internal object BrowserUrl {
    fun normalize(input: String): String? = runCatching {
        val value = input.trim()
        require(value.none { it.isWhitespace() || it.isISOControl() || it == '\\' })
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https")
        val authority = requireNotNull(uri.rawAuthority)
        require(uri.rawUserInfo == null && '@' !in authority && '%' !in authority)
        val host: String
        val port: String
        if (authority.startsWith("[")) {
            val end = authority.indexOf(']')
            require(end > 0)
            host = authority.substring(0, end + 1)
            port = authority.substring(end + 1)
        } else {
            host = IDN.toASCII(authority.substringBefore(':'), IDN.USE_STD3_ASCII_RULES)
            port = authority.substringAfter(':', "").let { if (':' in authority) ":$it" else "" }
            require(host.removeSuffix(".").length in 1..253)
        }
        if (port.isNotEmpty()) {
            require(port.startsWith(':'))
            val digits = port.substring(1)
            require(digits.isNotEmpty() && digits.all { it in '0'..'9' })
            require(digits.toInt() in 1..65535)
        }
        val normalized = URI(buildString {
            append(scheme).append("://").append(host).append(port)
            append(uri.rawPath.orEmpty())
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }).parseServerAuthority()
        require(!normalized.host.isNullOrEmpty())
        normalized.toASCIIString()
    }.getOrNull()
}
