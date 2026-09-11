package com.aurora.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ช่องทางสื่อสาร session id ระหว่าง PlaybackService กับ UI process
 *
 * หมายเหตุเชิงสถาปัตยกรรม: ใช้ singleton ได้อย่างปลอดภัย เนื่องจาก
 * MediaSessionService ไม่ได้ประกาศ android:process จึงอยู่ใน process เดียวกับ UI
 * หากในอนาคตแยก process ต้องเปลี่ยนไปใช้ SessionCommand + Bundle extras แทน
 */
object AudioSessionHolder {
    private val _sessionId = MutableStateFlow(0)
    val sessionId: StateFlow<Int> = _sessionId.asStateFlow()
    internal fun publish(id: Int) { _sessionId.value = id }
}
