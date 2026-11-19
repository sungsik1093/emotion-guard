/**
 * 앱 로컬 DB.
 *
 * 마이그레이션 정책 (디자인 문서 §8.2)
 * - fallbackToDestructiveMigration()을 쓰지 않는다. 구조가 바뀌어도 기록이 지워지면 안 된다.
 * - exportSchema = true: 버전마다 스키마 JSON이 core/database/schemas/에 남는다. 이 파일을 git에 커밋한다.
 * - 컬럼 추가처럼 단순한 변경은 @AutoMigration(from = N, to = N + 1)을 autoMigrations에 추가한다.
 * - 이름 변경·테이블 분리처럼 복잡한 변경은 Migration을 직접 써서 MIGRATIONS에 추가하고,
 *   MigrationTestHelper로 데이터가 보존되는지 테스트한다.
 */
package com.emotionguard.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [EmotionLogEntity::class],
    version = 1,
    exportSchema = true,
    autoMigrations = [],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun emotionDao(): EmotionDao

    companion object {
        const val NAME = "emotion_guard.db"

        /** 직접 작성한 마이그레이션 목록 (현재 없음) */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
