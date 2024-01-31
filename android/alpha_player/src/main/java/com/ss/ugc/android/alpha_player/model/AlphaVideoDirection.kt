package com.ss.ugc.android.alpha_player.model

import java.util.*

enum class AlphaVideoDirection(private val alphaDirection: String) {
    LEFT("left"), TOP("top"), RIGHT("right"), BOTTOM("bottom");

    companion object {
        fun convertFrom(direction: String): AlphaVideoDirection {
            for (directionItem in AlphaVideoDirection.values()) {
                if (directionItem.alphaDirection == direction.toLowerCase(Locale.getDefault())) {
                    return directionItem
                }
            }
            return LEFT
        }
    }
}