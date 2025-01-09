package com.ss.ugc.android.alpha_player.render

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import com.ss.ugc.android.alpha_player.model.ScaleType
import com.ss.ugc.android.alpha_player.utils.ShaderUtil
import com.ss.ugc.android.alpha_player.utils.TextureCropUtil
import com.ss.ugc.android.alpha_player.widget.IAlphaVideoView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MultiVideoRenderer(
    private val alphaVideoView: IAlphaVideoView,
) : IRender {

    private val TAG = "XB.MultiVideoRenderer"

    private val FLOAT_SIZE_BYTES = 4

    private var vecData = floatArrayOf(
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f, 1.0f,
        1.0f, 1.0f
    )

    private var useTextureData = floatArrayOf(
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 1f
    )

    private var vecAttrArray: FloatBuffer
    private var textureCoordArray: FloatBuffer

    private val mVPMatrix = FloatArray(16)
    private val sTMatrix = FloatArray(16)

    private var programID: Int = 0
    private var textureID: Int = 0
    private var texture2ID: Int = 0
    private var uMVPMatrixHandle: Int = 0
    private var uSTMatrixHandle: Int = 0
    private var aPositionHandle: Int = 0
    private var aTextureHandle: Int = 0
    private var sTextureHandle: Int = 0
    private var mTextureHandle: Int = 0

    /**
     * After mediaPlayer call onCompletion, GLSurfaceView still will call
     * {@link GLSurfaceView#requestRender} in some special case, so cause
     * the media source last frame be drawn again. So we add this flag to
     * avoid this case.
     */
    private val canDraw = AtomicBoolean(false)
    private val updateSurface = AtomicBoolean(false)

    private var surfaceTextures = mutableListOf<SurfaceTexture>()
    private var surfaceListener: IRender.SurfaceListener? = null
    private var scaleType = ScaleType.ScaleAspectFill

    init {
        vecAttrArray = ByteBuffer.allocateDirect(vecData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vecAttrArray.put(vecData)
        textureCoordArray = ByteBuffer.allocateDirect(useTextureData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        textureCoordArray.put(useTextureData)
        //triangleVertices.put(useVertData).position(0)
        Matrix.setIdentityM(sTMatrix, 0)
    }

    override fun setScaleType(scaleType: ScaleType) {
        this.scaleType = scaleType
    }

    override fun measureInternal(
        viewWidth: Float, viewHeight: Float,
        videoWidth: Float, videoHeight: Float
    ) {
        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return
        }

        val matrixVertAndTextureCoordData = TextureCropUtil.calculateVerticeData(
            vecData, useTextureData, scaleType,
            viewWidth, viewHeight, videoWidth, videoHeight
        )
        val cropVecData = matrixVertAndTextureCoordData[0]
        val cropMatrixTexture = matrixVertAndTextureCoordData[1]
        vecAttrArray = ByteBuffer.allocateDirect(cropVecData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vecAttrArray.put(cropVecData)
        textureCoordArray = ByteBuffer.allocateDirect(cropMatrixTexture.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        textureCoordArray.put(cropMatrixTexture)
    }

    override fun setSurfaceListener(surfaceListener: IRender.SurfaceListener) {
        this.surfaceListener = surfaceListener
    }

    override fun onDrawFrame(glUnused: GL10) {
        // 画面好像被压缩了两遍，需要排查
        if (updateSurface.compareAndSet(true, false)) {
            try {
                surfaceTextures.forEach { it.updateTexImage() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            surfaceTextures.forEach { it.getTransformMatrix(sTMatrix) }
        }

        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        if (!canDraw.get()) {
            GLES20.glFinish()
            return
        }
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glUseProgram(programID)
        checkGlError("glUseProgram")

        vecAttrArray.position(0)
        GLES20.glVertexAttribPointer(
            aPositionHandle, 2, GLES20.GL_FLOAT, false,
            0, vecAttrArray
        )
        checkGlError("glVertexAttribPointer maPosition")
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        checkGlError("glEnableVertexAttribArray aPositionHandle")

        textureCoordArray.position(0)
        GLES20.glVertexAttribPointer(
            aTextureHandle, 2, GLES20.GL_FLOAT, false,
            0, textureCoordArray
        )
        checkGlError("glVertexAttribPointer aTextureHandle")
        GLES20.glEnableVertexAttribArray(aTextureHandle)
        checkGlError("glEnableVertexAttribArray aTextureHandle")

        Matrix.setIdentityM(mVPMatrix, 0)
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mVPMatrix, 0)
        GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, sTMatrix, 0)

        GLES20.glUniform1i(sTextureHandle, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID)

        GLES20.glUniform1i(mTextureHandle, 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture2ID)


        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")

        GLES20.glFinish()
    }

    override fun onSurfaceChanged(glUnused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onSurfaceCreated(glUnused: GL10, config: EGLConfig) {
        programID = createProgram()
        if (programID == 0) {
            return
        }
        aPositionHandle = GLES20.glGetAttribLocation(programID, "aPosition")
        checkGlError("glGetAttribLocation aPosition")
        if (aPositionHandle == -1) {
            throw RuntimeException("Could not get attrib location for aPosition")
        }
        aTextureHandle = GLES20.glGetAttribLocation(programID, "aTextureCoord")
        checkGlError("glGetAttribLocation aTextureCoord")
        if (aTextureHandle == -1) {
            throw RuntimeException("Could not get attrib location for aTextureCoord")
        }

        uMVPMatrixHandle = GLES20.glGetUniformLocation(programID, "uMVPMatrix")
        checkGlError("glGetUniformLocation uMVPMatrix")
        if (uMVPMatrixHandle == -1) {
            throw RuntimeException("Could not get attrib location for uMVPMatrix")
        }

        uSTMatrixHandle = GLES20.glGetUniformLocation(programID, "uSTMatrix")
        checkGlError("glGetUniformLocation uSTMatrix")
        if (uSTMatrixHandle == -1) {
            throw RuntimeException("Could not get attrib location for uSTMatrix")
        }
        sTextureHandle = GLES20.glGetUniformLocation(programID, "sTexture")
        checkGlError("glGetUniformLocation sTexture")
        if (sTextureHandle == -1) {
            throw RuntimeException("Could not get attrib location for sTexture")
        }
        mTextureHandle = GLES20.glGetUniformLocation(programID, "mTexture")
        checkGlError("glGetUniformLocation mTexture")
        if (mTextureHandle == -1) {
            throw RuntimeException("Could not get attrib location for mTexture")
        }
        prepareSurface()
    }

    override fun onSurfaceDestroyed(gl: GL10?) {
        surfaceListener?.onSurfaceDestroyed()
    }

    private fun genTexture(): Pair<Int, SurfaceTexture> {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)

        val newTextureID = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, newTextureID)
        checkGlError("glBindTexture textureID")

        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )

        val surfaceTexture = SurfaceTexture(newTextureID)
        surfaceTexture.setDefaultBufferSize(
            alphaVideoView.getMeasuredWidth(),
            alphaVideoView.getMeasuredHeight()
        )
        surfaceTexture.setOnFrameAvailableListener(this)
        return Pair(newTextureID, surfaceTexture)
    }

    private fun prepareSurface() {
        surfaceTextures.clear()
        val surface1 = genTexture()
        textureID = surface1.first
        val surface2 = genTexture()
        texture2ID = surface2.first

        surfaceTextures.add(surface1.second)
        var surface = Surface(surface1.second)
        surfaceListener?.onSurfacePrepared(surface, 0)

        surfaceTextures.add(surface2.second)
        surface = Surface(surface2.second)
        surfaceListener?.onSurfacePrepared(surface, 1)
        updateSurface.compareAndSet(true, false)
    }

    override fun onFrameAvailable(surface: SurfaceTexture) {
        updateSurface.compareAndSet(false, true)
        alphaVideoView.requestRender()
    }

    override fun onFirstFrame() {
        canDraw.compareAndSet(false, true)
        Log.i(TAG, "onFirstFrame:    canDraw = " + canDraw.get())
        alphaVideoView.requestRender()
    }

    override fun onCompletion() {
        canDraw.compareAndSet(true, false)
        Log.i(TAG, "onCompletion:   canDraw = " + canDraw.get())
        alphaVideoView.requestRender()
    }

    /**
     * load shader by OpenGL ES, if compile shader success, it will return shader handle,
     * else return 0.
     *
     * @param shaderType shader type, {@link GLES20.GL_VERTEX_SHADER} and
     * {@link GLES20.GL_FRAGMENT_SHADER}
     * @param source   shader source
     *
     * @return shaderID If compile shader success, it will return shader handle, else return 0.
     */
    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        if (shader != 0) {
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader $shaderType:")
                Log.e(TAG, GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
                shader = 0
            }
        }
        return shader
    }

    /**
     * create program with {@link vertex.sh} and {@link frag.sh}. If attach shader or link
     * program, it will return 0, else return program handle
     *
     * @return programID If link program success, it will return program handle, else return 0.
     */
    private fun createProgram(): Int {
        val vertexSource =
            ShaderUtil.loadFromAssetsFile("multi_vertex.sh", alphaVideoView.getView().resources)
        val fragmentSource =
            ShaderUtil.loadFromAssetsFile("multi_frag.sh", alphaVideoView.getView().resources)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) {
            return 0
        }
        val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) {
            return 0
        }
        var program = GLES20.glCreateProgram()
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader)
            checkGlError("glAttachShader")
            GLES20.glAttachShader(program, pixelShader)
            checkGlError("glAttachShader")
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link programID: ")
                Log.e(TAG, GLES20.glGetProgramInfoLog(program))
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }
        return program
    }

    private fun checkGlError(op: String) {
        val error: Int = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError $error")
        }
    }
}