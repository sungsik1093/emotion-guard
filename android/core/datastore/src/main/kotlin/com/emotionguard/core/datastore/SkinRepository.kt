/**
 * 캐릭터 꾸미기 저장소 (디자인 문서 §6).
 *
 * - 설정(CharacterSkin)은 DataStore에 JSON으로 저장한다.
 * - 사용자가 고른 사진은 앱 내부 저장소 filesDir/skins/에 256×256으로 잘라 복사한다.
 *   Photo Picker가 준 URI 권한은 앱을 다시 켜면 사라질 수 있어서, 원본 URI가 아니라 복사본을 쓴다.
 * - 더 이상 어디서도 쓰지 않는 사진 파일은 저장할 때마다 지운다.
 */
package com.emotionguard.core.datastore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.emotionguard.core.common.IoDispatcher
import com.emotionguard.core.model.CharacterSkin
import com.emotionguard.core.model.MoodState
import com.emotionguard.core.model.SkinMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class SkinRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val store = context.appDataStore
    private val key = stringPreferencesKey("character_skin")
    private val json = Json { ignoreUnknownKeys = true }

    val skinDir: File get() = File(context.filesDir, "skins").apply { mkdirs() }

    val skin: Flow<CharacterSkin> = store.data.map { p ->
        p[key]?.let { runCatching { json.decodeFromString<CharacterSkin>(it) }.getOrNull() } ?: CharacterSkin()
    }.distinctUntilChanged()

    fun fileOf(name: String): File = File(skinDir, name)

    suspend fun update(transform: (CharacterSkin) -> CharacterSkin) {
        var saved = CharacterSkin()
        store.edit { p ->
            val current = p[key]?.let { runCatching { json.decodeFromString<CharacterSkin>(it) }.getOrNull() }
                ?: CharacterSkin()
            saved = transform(current)
            p[key] = json.encodeToString(CharacterSkin.serializer(), saved)
        }
        deleteUnreferenced(saved)
    }

    suspend fun setMode(mode: SkinMode) = update { it.copy(mode = mode) }

    /** 사진을 골라 [state]에 지정한다. state가 null이면 "사진 한 장" 슬롯. */
    suspend fun setImage(state: MoodState?, uri: Uri): Result<Unit> = runCatching {
        val name = importImage(uri)
        update { skin ->
            if (state == null) skin.copy(singleImage = name)
            else skin.copy(stateImages = skin.stateImages + (state to name))
        }
    }

    suspend fun resetState(state: MoodState) = update { it.copy(stateImages = it.stateImages - state) }

    suspend fun resetAll() = update { CharacterSkin() }

    suspend fun current(): CharacterSkin = skin.first()

    private suspend fun importImage(uri: Uri): String = withContext(io) {
        val bitmap = decodeSampled(uri, TARGET_SIZE * 2)
            ?: error("이미지를 읽을 수 없습니다")
        val square = centerCropSquare(bitmap, TARGET_SIZE)
        val name = "skin_${UUID.randomUUID()}.webp"
        fileOf(name).outputStream().use { out ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            square.compress(format, 90, out)
        }
        if (square !== bitmap) square.recycle()
        bitmap.recycle()
        name
    }

    private fun decodeSampled(uri: Uri, minSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= minSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun centerCropSquare(src: Bitmap, size: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val cropped = Bitmap.createBitmap(src, x, y, side, side)
        if (side == size) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
        if (cropped !== src && cropped !== scaled) cropped.recycle()
        return scaled
    }

    private suspend fun deleteUnreferenced(skin: CharacterSkin) = withContext(io) {
        val keep = skin.referencedFiles()
        // 방금 복사했지만 아직 설정에 반영되기 전인 파일을 지우지 않도록 1분 이상 된 것만 정리한다.
        val cutoff = System.currentTimeMillis() - 60_000
        skinDir.listFiles()
            ?.filter { it.name !in keep && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    companion object {
        const val TARGET_SIZE = 256
    }
}
