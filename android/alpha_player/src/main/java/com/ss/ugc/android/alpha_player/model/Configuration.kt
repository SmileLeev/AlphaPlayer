package com.ss.ugc.android.alpha_player.model

import android.content.Context
import androidx.lifecycle.LifecycleOwner

/**
 * created by dengzhuoyao on 2020/07/07
 */
class Configuration(var context: Context,
                    var lifecycleOwner: LifecycleOwner) {
    var alphaVideoViewType: AlphaVideoViewType = AlphaVideoViewType.GL_SURFACE_VIEW
    var alphaVideoDirection: AlphaVideoDirection = AlphaVideoDirection.LEFT
}

enum class AlphaVideoViewType {
    GL_TEXTURE_VIEW, GL_SURFACE_VIEW, GL_MULTI_TEXTURE_VIEW
}