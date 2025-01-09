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

/**
 * Created by pengboboer.
 * Date: 2023/3/28
 */
class ExoPlayerImpl(private val context: Context) : AbsPlayer(context) {

    private val TAG = "XB.ExoImpl"

    private lateinit var exoPlayer: ExoPlayer

    private var currVideoWidth: Int = 0
    private var currVideoHeight: Int = 0
    private var isLooping: Boolean = false

    private val exoPlayerListener: Player.Listener = object : Player.Listener {
        override fun onVideoSizeChanged(
            videoSize: VideoSize
        ) {
            currVideoWidth = videoSize.width
            currVideoHeight = videoSize.height
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
        if (exoPlayer.playbackState == Player.STATE_READY ) {
            Log.w(TAG, "player duration=${exoPlayer.duration}")
            preparedListener?.onPrepared()
        } else {
            Log.w(TAG, "player not ready")
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
        exoPlayer = ExoPlayer.Builder(context).setTrackSelector(trackSelector).build()
        exoPlayer.addListener(exoPlayerListener)
        exoPlayer.repeatMode = REPEAT_MODE_ONE
    }

    override fun setDataSource(dataPath: String, index: Int) {
        reset()
        val mediaItem = MediaItem.fromUri(Uri.parse(dataPath))
        exoPlayer.setMediaItem(mediaItem)
    }

    override fun prepareAsync() {
        exoPlayer.prepare()
        //exoPlayer.playWhenReady = false
    }

    override fun start() {
        exoPlayer.play()
    }

    override fun seekTo(position: Long) {
        if (position >= 0) {
            exoPlayer.seekTo(position)
        }
    }

    override fun getCurrentPosition(): Long {
        return exoPlayer.currentPosition
    }

    override fun pause() {
        exoPlayer.playWhenReady = false
    }

    override fun stop() {
        exoPlayer.stop()
    }

    override fun reset() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    override fun release() {
        exoPlayer.release()
    }

    override fun setLooping(looping: Boolean) {
        this.isLooping = looping
        exoPlayer.repeatMode = if (isLooping) REPEAT_MODE_ONE else REPEAT_MODE_OFF
    }

    override fun setScreenOnWhilePlaying(onWhilePlaying: Boolean) {
    }

    override fun setSurface(surface: Surface, index: Int) {
        exoPlayer.setVideoSurface(surface)
    }

    override fun getVideoInfo(index: Int): VideoInfo {
        return VideoInfo(currVideoWidth, currVideoHeight)
    }

    override fun getPlayerType(): String {
        return "ExoPlayerImpl"
    }


}