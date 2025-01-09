package com.ss.ugc.android.alpha_player.model

import android.content.res.Configuration
import android.text.TextUtils

/**
 * created by dengzhuoyao on 2020/07/07
 */
open class DataSource {

    lateinit var portPath: String
    lateinit var landPath: String

    var portScaleType: ScaleType? = null
    var landScaleType: ScaleType? = null
    var isLooping: Boolean = false

    fun setPortraitPath(portraitPath: String, portraitScaleType: Int): DataSource {
        this.portPath = portraitPath
        this.portScaleType = ScaleType.convertFrom(portraitScaleType)
        return this
    }

    fun setLandscapePath(landscapePath: String, landscapeScaleType: Int): DataSource {
        this.landPath = landscapePath
        this.landScaleType = ScaleType.convertFrom(landscapeScaleType)
        return this
    }

    fun setLooping(isLooping: Boolean): DataSource {
        this.isLooping = isLooping
        return this
    }

    fun getPath(orientation: Int): String {
        return if (Configuration.ORIENTATION_PORTRAIT == orientation) portPath else landPath
    }

    fun getScaleType(orientation: Int): ScaleType? {
        return if (Configuration.ORIENTATION_PORTRAIT == orientation) portScaleType else landScaleType
    }

    open fun isValid(): Boolean {
        return !TextUtils.isEmpty(portPath) && !TextUtils.isEmpty(landPath) && portScaleType != null && landScaleType != null
    }
}