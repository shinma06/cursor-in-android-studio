package com.cursoragent.notification

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ToolActivityNoticeTest {
    private class Notice : Notification("synthetic", "title", "content", NotificationType.INFORMATION) {
        var expiredCount = 0
        override fun expire() { expiredCount++ }
    }

    @Test fun `activity is aggregated across turns and old termination cannot expire a newer notice`() {
        val service = ToolActivityNotice()
        val first = Notice()
        val second = Notice()
        service.replace("first", first)
        service.replace("second", second)
        assertEquals(1, first.expiredCount)
        service.clear("first")
        assertEquals(0, second.expiredCount)
        service.clear("second")
        assertEquals(1, second.expiredCount)
        val third = Notice()
        service.replace("third", third)
        service.dispose()
        service.dispose()
        assertEquals(1, third.expiredCount)
        val late = Notice()
        service.replace("late", late)
        assertEquals(1, late.expiredCount)
    }
}
