package com.ss.ugc.android.alphavideoplayer.player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.REPEAT_MODE_OFF
import com.google.android.exoplayer2.Player.REPEAT_MODE_ONE
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.video.VideoSize
import com.ss.ugc.android.alpha_player.model.VideoInfo
import com.ss.ugc.android.alpha_player.player.AbsPlayer
import kotlin.math.abs

class MultiExoPlayerImpl(private val context: Context) : AbsPlayer(context) {

    private val TAG = "XB.MultiExo"

    private val exoPlayerList = mutableListOf<ExoPlayer>()

    private val videoSizeList = arrayOfNulls<VideoSize?>(2)
    private var isLooping: Boolean = false

    private val exoPlayerListener: Player.Listener = object : Player.Listener {
        override fun onVideoSizeChanged(
            videoSize: VideoSize
        ) {
            videoSizeList[0] = videoSize
        }

        override fun onRenderedFirstFrame() {
            firstFrameListener?.onFirstFrame()
        }

        override fun onPlayerError(error: PlaybackException) {
            errorListener?.onError(0, 0, "ExoPlayer on error: " + Log.getStackTraceString(error))
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    playerReady()
                }

                Player.STATE_ENDED -> {
                    completionListener?.onCompletion()
                }

                else -> {}
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            Log.v(
                TAG,
                "src position: old: ${oldPosition.positionMs}, new: ${newPosition.positionMs}, reason: $reason"
            )
        }
    }

    private fun playerReady() {
        if (exoPlayerList.all { it.playbackState == Player.STATE_READY }) {
            exoPlayerList.forEach {
                Log.w(TAG, "player duration=${it.duration}")
            }
            preparedListener?.onPrepared()
        } else {
            Log.w(TAG, "player not ready")
        }
    }

    private val exoMaskPlayerListener: Player.Listener = object : Player.Listener {

        override fun onVideoSizeChanged(
            videoSize: VideoSize
        ) {
            videoSizeList[1] = videoSize
        }

        override fun onPlayerError(error: PlaybackException) {
            errorListener?.onError(0, 0, "ExoPlayer on error: " + Log.getStackTraceString(error))
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    playerReady()
                }

                else -> {}
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            Log.v(
                TAG,
                "mask position: old: ${oldPosition.positionMs}, new: ${newPosition.positionMs}, reason: $reason"
            )
        }
    }

    override fun initMediaPlayer() {
        val trackSelector = DefaultTrackSelector(context).apply {
            // 设置一个 TrackSelection 参数，这里使用的是 AdaptiveTrackSelection.Factory
            val parametersBuilder = this.buildUponParameters()
            // 禁用音频轨道
            parametersBuilder.setRendererDisabled(C.TRACK_TYPE_AUDIO, true)
            // 设置参数
            this.setParameters(parametersBuilder)
        }
        // add source video
        exoPlayerList.add(ExoPlayer.Builder(context).setTrackSelector(trackSelector).build().apply {
            addListener(exoPlayerListener)
            repeatMode = REPEAT_MODE_ONE
        })
        // add mask video
        exoPlayerList.add(ExoPlayer.Builder(context).setTrackSelector(trackSelector).build().apply {
            addListener(exoMaskPlayerListener)
            repeatMode = REPEAT_MODE_ONE
        })
    }

    override fun setDataSource(dataPath: String, index: Int) {
        if (index == 0) {
            reset()
        }
        if (index < 0 || index >= exoPlayerList.size) {
            throw IndexOutOfBoundsException("set source index out of player list range, index=$index, player size=${exoPlayerList.size}")
        }
        val mediaItem = MediaItem.fromUri(Uri.parse(dataPath))
        exoPlayerList[index].setMediaItem(mediaItem)
    }

    override fun prepareAsync() {
        exoPlayerList.forEach {
            it.prepare()
        }
    }

    override fun start() {
        val srcPosition = exoPlayerList[0].currentPosition
        val maskPosition = exoPlayerList[1].currentPosition
        if (abs(srcPosition - maskPosition) >= 50) {
            exoPlayerList[1].seekTo(maskPosition)
        }
        exoPlayerList.forEach {
            it.play()
        }
    }

    override fun pause() {
        exoPlayerList.forEach {
            it.playWhenReady = false
        }
    }

    override fun stop() {
        exoPlayerList.forEach {
            it.stop()
        }
    }

    override fun reset() {
        exoPlayerList.forEach {
            it.stop()
            it.clearMediaItems()
        }
    }

    override fun release() {
        exoPlayerList.forEach {
            it.release()
        }
    }

    override fun setLooping(looping: Boolean) {
        this.isLooping = looping
        exoPlayerList.forEach {
            it.repeatMode = if (isLooping) REPEAT_MODE_ONE else REPEAT_MODE_OFF
        }
    }

    override fun setScreenOnWhilePlaying(onWhilePlaying: Boolean) {
    }

    override fun seekTo(position: Long) {
        if (exoPlayerList.firstOrNull()?.isPlaying == true) {
            // 播放中seek，需要先暂停，默认seek后需要手动播放
            pause()
        }
        exoPlayerList.forEach {
            it.seekTo(position)
        }
    }

    override fun setSurface(surface: Surface, index: Int) {
        if (index < 0 || index >= exoPlayerList.size) {
            throw IndexOutOfBoundsException("set surface index out of player list range, index=$index, player size=${exoPlayerList.size}")
        }
        exoPlayerList[index].setVideoSurface(surface)
    }

    override fun getVideoInfo(index: Int): VideoInfo {
        if (videoSizeList.size <= index) {
            return VideoInfo(0, 0)
        }
        return VideoInfo(videoSizeList[index]?.width ?: 0, videoSizeList[index]?.height ?: 0)
    }

    override fun getPlayerType(): String {
        return "MultiExoPlayerImpl"
    }

    override fun getCurrentPosition(): Long {
        return exoPlayerList[0].currentPosition
    }


}