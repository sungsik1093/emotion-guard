/**
 * 개인정보 가리기 (1차 방어선, 기기 안).
 * 서버(server/src/pii.ts)에도 같은 규칙이 있다. 두 곳의 규칙을 바꿀 때는 함께 바꾼다.
 *
 * 더 구체적인 형식(주민번호, 카드)을 먼저 바꿔야 일반 숫자열 규칙(계좌)이 먼저 먹지 않는다.
 */
package com.emotionguard.core.analysis

object PiiMasker {
    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""") to "[이메일]",
        Regex("""(?<!\d)\d{6}\s?-\s?[1-8]\d{6}(?!\d)""") to "[주민번호]",
        Regex("""(?<!\d)\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}(?!\d)""") to "[카드번호]",
        Regex("""(?:\+82[\s-]?|(?<!\d)0)1[016789][\s-]?\d{3,4}[\s-]?\d{4}(?!\d)""") to "[전화번호]",
        Regex("""(?<!\d)0\d{1,2}-\d{3,4}-\d{4}(?!\d)""") to "[전화번호]",
        Regex("""(?<!\d)\d{2,6}-\d{2,6}-\d{2,8}(?:-\d{1,4})?(?!\d)""") to "[계좌번호]",
        Regex("""(?<!\d)\d{11,16}(?!\d)""") to "[계좌번호]",
    )

    fun mask(text: String): String = rules.fold(text) { acc, (pattern, label) -> pattern.replace(acc, label) }
}
