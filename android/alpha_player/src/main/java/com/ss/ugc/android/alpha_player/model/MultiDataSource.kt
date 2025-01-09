package com.ss.ugc.android.alpha_player.model

import android.content.res.Configuration
import android.text.TextUtils

class MultiDataSource: DataSource() {

    lateinit var portPathList: List<String>
    lateinit var landPathList: List<String>

    fun setPortraitPathList(portraitPathList: List<String>, portraitScaleType: Int): MultiDataSource {
        this.portPathList = portraitPathList
        this.portScaleType = ScaleType.convertFrom(portraitScaleType)
        return this
    }

    fun setLandscapePathList(landscapePathList: List<String>, landscapeScaleType: Int): MultiDataSource {
        this.landPathList = landscapePathList
        this.landScaleType = ScaleType.convertFrom(landscapeScaleType)
        return this
    }

    fun getPathList(orientation: Int): List<String> {
        return if (Configuration.ORIENTATION_PORTRAIT == orientation) portPathList else landPathList
    }

    override fun isValid(): Boolean {
        if (portPathList.none { TextUtils.isEmpty(it).not() }) {
            return false
        }
        if (landPathList.none { TextUtils.isEmpty(it).not() }) {
            return false
        }
        return portScaleType != null && landScaleType != null
    }
}