package com.ss.ugc.android.alpha_player.controller

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.Process
import android.text.TextUtils
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.annotation.WorkerThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.ss.ugc.android.alpha_player.IMonitor
import com.ss.ugc.android.alpha_player.IPlayerAction
import com.ss.ugc.android.alpha_player.model.AlphaVideoDirection
import com.ss.ugc.android.alpha_player.model.AlphaVideoViewType
import com.ss.ugc.android.alpha_player.model.Configuration
import com.ss.ugc.android.alpha_player.model.DataSource
import com.ss.ugc.android.alpha_player.player.DefaultSystemPlayer
import com.ss.ugc.android.alpha_player.player.IMediaPlayer
import com.ss.ugc.android.alpha_player.player.PlayerState
import com.ss.ugc.android.alpha_player.render.VideoRenderer
import com.ss.ugc.android.alpha_player.widget.AlphaVideoGLSurfaceView
import com.ss.ugc.android.alpha_player.widget.AlphaVideoGLTextureView
import com.ss.ugc.android.alpha_player.widget.IAlphaVideoView

/**
 * created by dengzhuoyao on 2020/07/08
 */
class PlayerController(
    val context: Context,
    owner: LifecycleOwner,
    private val alphaVideoViewType: AlphaVideoViewType,
    private val alphaVideoDirection: AlphaVideoDirection,
    private var mediaPlayer: IMediaPlayer
) : IPlayerControllerExt, LifecycleEventObserver, Handler.Callback {

    companion object {
        const val INIT_MEDIA_PLAYER: Int = 1
        const val SET_DATA_SOURCE: Int = 2
        const val START: Int = 3
        const val PAUSE: Int = 4
        const val RESUME: Int = 5
        const val STOP: Int = 6
        const val DESTROY: Int = 7
        const val SURFACE_PREPARED: Int = 8
        const val RESET: Int = 9
        const val SEEK: Int = 10

        fun get(configuration: Configuration, mediaPlayer: IMediaPlayer? = null): PlayerController {
            return PlayerController(
                configuration.context, configuration.lifecycleOwner,
                configuration.alphaVideoViewType,
                configuration.alphaVideoDirection,
                mediaPlayer ?: DefaultSystemPlayer()
            )
        }
    }

    private var suspendDataSource: DataSource? = null
    var isPlaying: Boolean = false
    var playerState = PlayerState.NOT_PREPARED
    var mMonitor: IMonitor? = null
    var mPlayerAction: IPlayerAction? = null
    lateinit var alphaVideoView: IAlphaVideoView

    var workHandler: Handler? = null
    val mainHandler: Handler = Handler(Looper.getMainLooper())
    var playThread: HandlerThread? = null

    private val mPreparedListener = object : IMediaPlayer.OnPreparedListener {
        override fun onPrepared() {
            mainHandler.post {
                mPlayerAction?.mediaPrepared()
            }
        }
    }

    private val mErrorListener = object : IMediaPlayer.OnErrorListener {
        override fun onError(what: Int, extra: Int, desc: String) {
            monitor(false, what, extra, "mediaPlayer error, info: $desc")
            emitEndSignal()
        }
    }

    init {
        init(owner)
        initAlphaView()
        initMediaPlayer()
    }

    private fun init(owner: LifecycleOwner) {
        owner.lifecycle.addObserver(this)
        playThread = HandlerThread("alpha-play-thread", Process.THREAD_PRIORITY_BACKGROUND)
        playThread!!.start()
        workHandler = Handler(playThread!!.looper, this)
    }

    private fun initAlphaView() {
        alphaVideoView = when (alphaVideoViewType) {
            AlphaVideoViewType.GL_SURFACE_VIEW -> AlphaVideoGLSurfaceView(context, null)
            AlphaVideoViewType.GL_TEXTURE_VIEW -> AlphaVideoGLTextureView(context, null)
            AlphaVideoViewType.GL_MULTI_TEXTURE_VIEW -> throw IllegalArgumentException()
        }
        alphaVideoView.let {
            val layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            it.setLayoutParams(layoutParams)
            it.setPlayerController(this)
            it.setVideoRenderer(VideoRenderer(it, alphaVideoDirection))
        }
    }

    private fun initMediaPlayer() {
        sendMessage(getMessage(INIT_MEDIA_PLAYER, null))
    }

    override fun setPlayerAction(playerAction: IPlayerAction) {
        this.mPlayerAction = playerAction
    }

    override fun setMonitor(monitor: IMonitor) {
        this.mMonitor = monitor
    }

    override fun setVisibility(visibility: Int) {
        alphaVideoView.setVisibility(visibility)
        if (visibility == View.VISIBLE) {
            alphaVideoView.bringToFront()
        }
    }

    override fun attachAlphaView(parentView: ViewGroup) {
        alphaVideoView.addParentView(parentView)
    }

    override fun detachAlphaView(parentView: ViewGroup) {
        alphaVideoView.removeParentView(parentView)
    }

    private fun sendMessage(msg: Message) {
        playThread?.let {
            if (it.isAlive && !it.isInterrupted) {
                when (workHandler) {
                    null -> workHandler = Handler(it.looper, this)
                }
                workHandler!!.sendMessageDelayed(msg, 0)
            }
        }
    }

    private fun getMessage(what: Int, obj: Any?): Message {
        val message = Message.obtain()
        message.what = what
        message.obj = obj
        return message
    }

    override fun surfacePrepared(surface: Surface) {
        sendMessage(getMessage(SURFACE_PREPARED, surface))
    }

    override fun start(dataSource: DataSource) {
        if (dataSource.isValid()) {
            setVisibility(View.VISIBLE)
            sendMessage(getMessage(SET_DATA_SOURCE, dataSource))
        } else {
            emitEndSignal()
            monitor(false, errorInfo = "dataSource is invalid!")
        }
    }

    override fun play() {
        sendMessage(getMessage(START, null))
    }

    override fun pause() {
        sendMessage(getMessage(PAUSE, null))
    }

    override fun resume() {
        sendMessage(getMessage(RESUME, null))
    }

    override fun stop() {
        sendMessage(getMessage(STOP, null))
    }

    override fun reset() {
        sendMessage(getMessage(RESET, null))
    }

    override fun release() {
        sendMessage(getMessage(DESTROY, null))
    }

    override fun getView(): View {
        return alphaVideoView.getView()
    }

    override fun getPlayerType(): String {
        return mediaPlayer.getPlayerType()
    }

    override fun seekTo(position: Long) {
        sendMessage(getMessage(SEEK, position))
    }

    override fun currentPosition(): Long {
        return mediaPlayer.getCurrentPosition()
    }

    @WorkerThread
    private fun initPlayer() {
        try {
            mediaPlayer.initMediaPlayer()
        } catch (e: Exception) {
            mediaPlayer = DefaultSystemPlayer()
            mediaPlayer.initMediaPlayer()
            // TODO: add log
        }
        mediaPlayer.setScreenOnWhilePlaying(true)
        mediaPlayer.setLooping(false)

        mediaPlayer.setOnFirstFrameListener(object : IMediaPlayer.OnFirstFrameListener {
            override fun onFirstFrame() {
                alphaVideoView.onFirstFrame()
            }
        })
        mediaPlayer.setOnCompletionListener(object : IMediaPlayer.OnCompletionListener {
            override fun onCompletion() {
                alphaVideoView.onCompletion()
                playerState = PlayerState.PAUSED
                monitor(true, errorInfo = "")
                emitEndSignal()
            }
        })
    }

    @WorkerThread
    private fun setDataSource(dataSource: DataSource) {
        try {
            val portPath = dataSource.portPath
            if (portPath.startsWith("http://") || portPath.startsWith("https://")) {
                setVideoFromNet(dataSource)
            } else {
                setVideoFromFile(dataSource)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            monitor(
                false,
                errorInfo = "alphaVideoView set dataSource failure: " + Log.getStackTraceString(e)
            )
            emitEndSignal()
        }
    }

    private fun setVideoFromNet(dataSource: DataSource) {
        mediaPlayer.reset()
        playerState = PlayerState.NOT_PREPARED
        val orientation = context.resources.configuration.orientation
        val isPort = android.content.res.Configuration.ORIENTATION_PORTRAIT == orientation
        val dataPath =
            (if (isPort) dataSource.portPath else dataSource.landPath)
        val scaleType = dataSource.getScaleType(orientation)
        if (TextUtils.isEmpty(dataPath)) {
            monitor(false, errorInfo = "dataPath is empty or File is not exists. path = $dataPath")
            emitEndSignal()
            return
        }
        scaleType?.let {
            alphaVideoView.setScaleType(it)
        }
        mediaPlayer.setLooping(dataSource.isLooping)
        mediaPlayer.setDataSource(dataPath, 0)
        if (alphaVideoView.isSurfaceCreated()) {
            prepareAsync()
        } else {
            suspendDataSource = dataSource
        }
    }

    @WorkerThread
    private fun setVideoFromFile(dataSource: DataSource) {
        mediaPlayer.reset()
        playerState = PlayerState.NOT_PREPARED
        val orientation = context.resources.configuration.orientation

        val dataPath = dataSource.getPath(orientation)
        val scaleType = dataSource.getScaleType(orientation)
        if (TextUtils.isEmpty(dataPath)) {
            monitor(false, errorInfo = "dataPath is empty or File is not exists. path = $dataPath")
            emitEndSignal()
            return
        }
        scaleType?.let {
            alphaVideoView.setScaleType(it)
        }
        mediaPlayer.setLooping(dataSource.isLooping)
        mediaPlayer.setDataSource(dataPath, 0)
        if (alphaVideoView.isSurfaceCreated()) {
            prepareAsync()
        } else {
            suspendDataSource = dataSource
        }
    }

    @WorkerThread
    private fun handleSuspendedEvent() {
        suspendDataSource?.let {
            setDataSource(it)
        }
        suspendDataSource = null
    }


    @WorkerThread
    private fun prepareAsync() {
        mediaPlayer.let {
            if (playerState == PlayerState.NOT_PREPARED || playerState == PlayerState.STOPPED) {
                it.setOnPreparedListener(mPreparedListener)
                it.setOnErrorListener(mErrorListener)
                it.prepareAsync()
            }
        }
    }

    @WorkerThread
    private fun startPlay() {
        when (playerState) {
            PlayerState.PREPARED -> {
                mediaPlayer.start()
                isPlaying = true
                playerState = PlayerState.STARTED
                mainHandler.post {
                    mPlayerAction?.startAction()
                }
            }

            PlayerState.PAUSED -> {
                mediaPlayer.start()
                playerState = PlayerState.STARTED
            }

            PlayerState.NOT_PREPARED, PlayerState.STOPPED -> {
                try {
                    prepareAsync()
                } catch (e: Exception) {
                    e.printStackTrace()
                    monitor(false, errorInfo = "prepare and start MediaPlayer failure!")
                    emitEndSignal()
                }
            }

            PlayerState.STARTED ->{}
            PlayerState.RELEASE ->{}
        }
    }

    @WorkerThread
    private fun parseVideoSize() {
        val videoInfo = mediaPlayer.getVideoInfo(0)
        val halfWidth = when (alphaVideoDirection) {
            AlphaVideoDirection.LEFT, AlphaVideoDirection.RIGHT -> true
            AlphaVideoDirection.TOP, AlphaVideoDirection.BOTTOM -> false
        }
        val videoWidth = if (halfWidth) videoInfo.videoWidth / 2 else videoInfo.videoWidth
        val videoHeight = if (halfWidth) videoInfo.videoHeight else videoInfo.videoHeight / 2
        alphaVideoView.measureInternal((videoWidth).toFloat(), (videoHeight).toFloat())

        val scaleType = alphaVideoView.getScaleType()
        mainHandler.post {
            mPlayerAction?.onVideoSizeChanged(videoWidth, videoHeight, scaleType)
        }
    }

    override fun handleMessage(msg: Message): Boolean {
        msg.let {
            when (msg.what) {
                INIT_MEDIA_PLAYER -> {
                    initPlayer()
                }

                SURFACE_PREPARED -> {
                    val surface = msg.obj as Surface
                    mediaPlayer.setSurface(surface, msg.arg1)
                    handleSuspendedEvent()
                }

                SET_DATA_SOURCE -> {
                    val dataSource = msg.obj as DataSource
                    setDataSource(dataSource)
                }

                START -> {
                    try {
                        parseVideoSize()
                        playerState = PlayerState.PREPARED
                        startPlay()
                    } catch (e: Exception) {
                        monitor(
                            false,
                            errorInfo = "start video failure: " + Log.getStackTraceString(e)
                        )
                        emitEndSignal()
                    }
                }

                PAUSE -> {
                    when (playerState) {
                        PlayerState.STARTED -> {
                            mediaPlayer.pause()
                            playerState = PlayerState.PAUSED
                        }

                        else -> {}
                    }
                }

                RESUME -> {
                    if (isPlaying) {
                        startPlay()
                    } else {
                    }
                }

                STOP -> {
                    when (playerState) {
                        PlayerState.STARTED, PlayerState.PAUSED -> {
                            mediaPlayer.pause()
                            playerState = PlayerState.PAUSED
                        }

                        else -> {}
                    }
                }

                DESTROY -> {
                    alphaVideoView.onPause()
                    if (playerState == PlayerState.STARTED) {
                        mediaPlayer.pause()
                        playerState = PlayerState.PAUSED
                    }
                    if (playerState == PlayerState.PAUSED) {
                        mediaPlayer.stop()
                        playerState = PlayerState.STOPPED
                    }
                    mediaPlayer.release()
                    alphaVideoView.release()
                    playerState = PlayerState.RELEASE

                    playThread?.let {
                        it.quit()
                        it.interrupt()
                    }
                }

                RESET -> {
                    mediaPlayer.reset()
                    playerState = PlayerState.NOT_PREPARED
                    isPlaying = false
                }

                SEEK -> {
                    when (playerState) {
                        PlayerState.NOT_PREPARED,PlayerState.PREPARED,PlayerState.STARTED,PlayerState.PAUSED, PlayerState.STOPPED -> {
                            mediaPlayer.seekTo(msg.obj as Long)
                        }
                        else -> {}
                    }
                }

                else -> {}
            }
        }
        return true
    }

    private fun emitEndSignal() {
        isPlaying = false
        mainHandler.post {
            mPlayerAction?.endAction()
        }
    }

    private fun monitor(state: Boolean, what: Int = 0, extra: Int = 0, errorInfo: String) {
        mMonitor?.monitor(state, getPlayerType(), what, extra, errorInfo)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when(event) {
            Lifecycle.Event.ON_RESUME -> resume()
            Lifecycle.Event.ON_PAUSE -> pause()
            Lifecycle.Event.ON_STOP -> stop()
            Lifecycle.Event.ON_DESTROY -> release()
            else -> {}
        }
    }

}