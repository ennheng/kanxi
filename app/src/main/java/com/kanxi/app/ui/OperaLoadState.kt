package com.kanxi.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.kanxi.app.KanxiViewModel
import com.kanxi.app.data.OperaWithCategory
import kotlinx.coroutines.flow.collect

internal sealed interface OperaLoadState {
    data object Loading : OperaLoadState
    data class Ready(val opera: OperaWithCategory?) : OperaLoadState
}

@Composable
internal fun rememberOperaLoadState(
    viewModel: KanxiViewModel,
    itemId: Long,
): State<OperaLoadState> = produceState<OperaLoadState>(
    initialValue = OperaLoadState.Loading,
    key1 = itemId,
) {
    viewModel.observeItem(itemId).collect { opera ->
        value = OperaLoadState.Ready(opera)
    }
}

