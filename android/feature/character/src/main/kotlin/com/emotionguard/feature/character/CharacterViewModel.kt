package com.emotionguard.feature.character

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emotionguard.core.datastore.GrowthRepository
import com.emotionguard.core.datastore.SkinRepository
import com.emotionguard.core.model.Accessory
import com.emotionguard.core.model.Growth
import com.emotionguard.core.model.CharacterSkin
import com.emotionguard.core.model.MoodState
import com.emotionguard.core.model.SkinMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object CharacterDestination

@HiltViewModel
class CharacterViewModel @Inject constructor(
    private val skins: SkinRepository,
    private val growthRepository: GrowthRepository,
) : ViewModel() {

    val growth: StateFlow<Growth> = growthRepository.growth.stateIn(viewModelScope, SharingStarted.Eagerly, Growth())

    fun selectAccessory(accessory: Accessory) = viewModelScope.launch { growthRepository.selectAccessory(accessory) }

    val skin: StateFlow<CharacterSkin> = skins.skin.stateIn(viewModelScope, SharingStarted.Eagerly, CharacterSkin())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    fun resolveImage(name: String): File = skins.fileOf(name)

    fun setMode(mode: SkinMode) = viewModelScope.launch { skins.setMode(mode) }

    /** @param state null이면 "사진 한 장" 슬롯 */
    fun setImage(state: MoodState?, uri: Uri) = viewModelScope.launch {
        skins.setImage(state, uri)
            .onSuccess { _messages.tryEmit("사진을 바꿨어요.") }
            .onFailure { _messages.tryEmit("사진을 불러오지 못했어요.") }
    }

    fun resetState(state: MoodState) = viewModelScope.launch { skins.resetState(state) }

    fun clearSingleImage() = viewModelScope.launch { skins.update { it.copy(singleImage = null) } }

    fun resetAll() = viewModelScope.launch {
        skins.resetAll()
        _messages.tryEmit("기본 캐릭터로 되돌렸어요.")
    }

    fun setShowRing(value: Boolean) = viewModelScope.launch { skins.update { it.copy(showColorRing = value) } }

    fun setAnimate(value: Boolean) = viewModelScope.launch { skins.update { it.copy(animate = value) } }
}
