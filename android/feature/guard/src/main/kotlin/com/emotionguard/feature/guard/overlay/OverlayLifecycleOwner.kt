/**
 * 서비스 위에서 Compose를 쓰기 위한 생명주기 소유자 (디자인 문서 §5.6).
 *
 * ComposeView는 화면(Activity)에 붙을 때 LifecycleOwner 등을 찾는데, 접근성 서비스에는 없다.
 * 그래서 직접 만들어서 오버레이 뷰에 달아준다.
 */
package com.emotionguard.feature.guard.overlay

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    init {
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun attachTo(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
    }

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
