package dev.rahier.picswipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI-style ViewModel. The composable sends [SwipeIntent]s via [onIntent];
 * the ViewModel reduces them to a new [SwipeState] emitted on [state].
 *
 * Side-effects (the swipe exit animation delay) live in [viewModelScope],
 * not in the composable, and are cancellable via [animationJob] so album
 * changes / reset mid-animation don't fire the deferred completion.
 */
class SwipeViewModel : ViewModel() {

    private val _state = MutableStateFlow(SwipeState())
    val state: StateFlow<SwipeState> = _state.asStateFlow()

    private var animationJob: Job? = null

    fun onIntent(intent: SwipeIntent) {
        when (intent) {
            SwipeIntent.AboutDismissed ->
                _state.update { it.copy(aboutOpen = false) }

            SwipeIntent.AboutMenuItemTapped ->
                _state.update { it.copy(menuOpen = false, aboutOpen = true) }

            is SwipeIntent.AlbumPicked -> {
                cancelAnimation()
                _state.update {
                    resetProgress(it).copy(albumID = intent.albumId, pickerOpen = false)
                }
            }

            SwipeIntent.AlbumPickerDismissed ->
                _state.update { it.copy(pickerOpen = false) }

            SwipeIntent.AlbumTitleTapped ->
                _state.update { it.copy(pickerOpen = true) }

            is SwipeIntent.CardDragChanged ->
                _state.update {
                    it.copy(
                        isDragging = true,
                        dragX = it.dragX + intent.x,
                        dragY = it.dragY + intent.y
                    )
                }

            is SwipeIntent.CardDragEnded -> {
                val s = _state.value
                when {
                    s.dragX > intent.thresholdPx -> startSwipe(direction = 1)
                    s.dragX < -intent.thresholdPx -> startSwipe(direction = -1)
                    else -> _state.update { it.copy(dragX = 0f, dragY = 0f, isDragging = false) }
                }
            }

            SwipeIntent.DeleteButtonTapped -> startSwipe(direction = -1)
            SwipeIntent.KeepButtonTapped -> startSwipe(direction = 1)

            SwipeIntent.MenuButtonTapped ->
                _state.update { it.copy(menuOpen = !it.menuOpen) }

            SwipeIntent.MenuDismissed ->
                _state.update { it.copy(menuOpen = false) }

            SwipeIntent.ResetButtonTapped -> {
                cancelAnimation()
                _state.update { resetProgress(it) }
            }

            is SwipeIntent.SwipeAnimationCompleted ->
                _state.update {
                    it.copy(
                        history = it.history + SwipeHistoryEntry(it.activeIndex, intent.direction == 1),
                        kept = if (intent.direction == 1) it.kept + 1 else it.kept,
                        deleted = if (intent.direction == -1) it.deleted + 1 else it.deleted,
                        activeIndex = it.activeIndex + 1,
                        dragX = 0f,
                        dragY = 0f,
                        exitingDirection = 0,
                        isDragging = false
                    )
                }

            SwipeIntent.UndoButtonTapped -> {
                val s = _state.value
                val last = s.history.lastOrNull() ?: return
                _state.update {
                    it.copy(
                        history = it.history.dropLast(1),
                        kept = if (last.kept) (it.kept - 1).coerceAtLeast(0) else it.kept,
                        deleted = if (!last.kept) (it.deleted - 1).coerceAtLeast(0) else it.deleted,
                        activeIndex = last.index
                    )
                }
            }
        }
    }

    private fun startSwipe(direction: Int) {
        val s = _state.value
        if (s.activeIndex >= PhotoLibrary.samples.size) return
        _state.update { it.copy(exitingDirection = direction) }
        cancelAnimation()
        animationJob = viewModelScope.launch {
            delay(ANIMATION_DURATION_MS)
            onIntent(SwipeIntent.SwipeAnimationCompleted(direction))
        }
    }

    private fun cancelAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    private fun resetProgress(state: SwipeState): SwipeState = state.copy(
        activeIndex = 0,
        history = emptyList(),
        kept = 0,
        deleted = 0,
        dragX = 0f,
        dragY = 0f,
        exitingDirection = 0,
        isDragging = false
    )

    companion object {
        const val ANIMATION_DURATION_MS = 320L
    }
}
