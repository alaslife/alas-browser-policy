package com.sun.alasbrowser.utils

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debounces rapid state updates to prevent flickering
 * Useful for progress updates and other high-frequency events
 */
class DebouncedValue<T>(
    initialValue: T,
    private val delayMs: Long = 100L
) {
    private var job: Job? = null
    private val _value = mutableStateOf(initialValue)
    val value get() = _value.value
    
    fun update(newValue: T, scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            delay(delayMs)
            _value.value = newValue
        }
    }
    
    fun updateImmediate(newValue: T) {
        job?.cancel()
        _value.value = newValue
    }
}

/**
 * Batches rapid event callbacks to reduce recompositions
 */
class ThrottledCallback<T>(
    private val delayMs: Long = 50L,
    private val onEmit: (T) -> Unit
) {
    private var lastEmitTime = 0L
    private var pendingValue: T? = null
    private var job: Job? = null
    
    fun emit(value: T, scope: CoroutineScope) {
        val now = System.currentTimeMillis()
        pendingValue = value
        
        if (now - lastEmitTime >= delayMs) {
            job?.cancel()
            lastEmitTime = now
            onEmit(value)
        } else if (job == null) {
            job = scope.launch {
                delay(delayMs - (now - lastEmitTime))
                pendingValue?.let { onEmit(it) }
                lastEmitTime = System.currentTimeMillis()
                job = null
            }
        }
    }
}
