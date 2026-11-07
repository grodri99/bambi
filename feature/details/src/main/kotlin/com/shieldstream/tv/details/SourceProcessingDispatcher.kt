package com.shieldstream.tv.details

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class SourceProcessingDispatcher(
    val dispatcher: CoroutineDispatcher,
) {
    @Inject constructor() : this(Dispatchers.Default)
}
