/**
 * 접근성 서비스가 감지 상태를 올리고, 설정의 진단 화면이 그걸 읽는 통로.
 * 메모리에만 있다 (프로세스가 죽으면 사라짐). 메시지 원문은 화면 표시용으로만 잠깐 들고 있다.
 */
package com.emotionguard.core.common

import com.emotionguard.core.model.DiagnosticsSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class DiagnosticsBus @Inject constructor() {
    private val _snapshot = MutableStateFlow(DiagnosticsSnapshot())
    val snapshot: StateFlow<DiagnosticsSnapshot> = _snapshot.asStateFlow()

    fun update(transform: (DiagnosticsSnapshot) -> DiagnosticsSnapshot) {
        _snapshot.update { transform(it).copy(updatedAt = System.currentTimeMillis()) }
    }
}
