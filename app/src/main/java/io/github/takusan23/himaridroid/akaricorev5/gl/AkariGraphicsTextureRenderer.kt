package io.github.takusan23.himaridroid.akaricorev5.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * [io.github.takusan23.akaricore.v5.video.AkariGraphicsProcessor]で描画を担当。フラグメントシェーダーとかを使ってる。
 * インスタンス化はライブラリ側でやるため internal constructor になります。
 * クラス自体も public ですが、[drawCanvas]や[drawSurfaceTexture]のためなので、ライブラリ利用側から使われたくない関数はすべて internal fun にしています。
 */
class AkariGraphicsTextureRenderer internal constructor(
    private val width: Int,
    private val height: Int,
    private val isEnableTenBitHdr: Boolean
) {
    private val mTriangleVertices = ByteBuffer.allocateDirect(mTriangleVerticesData.size * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val mMVPMatrix = FloatArray(16)
    private val mSTMatrix = FloatArray(16)
    private var mProgram = 0
    private var muMVPMatrixHandle = 0
    private var muSTMatrixHandle = 0
    private var maPositionHandle = 0
    private var maTextureHandle = 0

    // Uniform 変数のハンドル
    private var sSurfaceTextureHandle = 0
    private var sCanvasTextureHandle = 0
    private var sFboTextureHandle = 0
    private var iDrawModeHandle = 0

    // テクスチャ ID
    private var surfaceTextureTextureId = 0
    private var canvasTextureTextureId = 0

    // フレームバッファオブジェクト
    private var fboTextureId = 0
    private var framebuffer = 0
    private var depthBuffer = 0

    // Canvas 描画のため Bitmap
    private val canvasBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(canvasBitmap)

    init {
        mTriangleVertices.put(mTriangleVerticesData).position(0)
    }

    /**
     * Canvas に書く。
     * GL スレッドから呼び出すこと。
     */
    suspend fun drawCanvas(draw: suspend Canvas.() -> Unit) {
        // 前回のを消す
        canvas.drawColor(0, PorterDuff.Mode.CLEAR)
        // 書く
        draw(canvas)

        // 多分いる
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, canvasTextureTextureId)

        // テクスチャを転送
        // texImage2D、引数違いがいるので注意
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, canvasBitmap, 0)
        checkGlError("GLUtils.texImage2D")

        // 描画する
        // glError 1282 の原因とかになる
        GLES20.glUseProgram(mProgram)
        checkGlError("glUseProgram")

        // テクスチャの ID をわたす
        GLES20.glUniform1i(sSurfaceTextureHandle, 0) // GLES20.GL_TEXTURE0
        GLES20.glUniform1i(sCanvasTextureHandle, 1) // GLES20.GL_TEXTURE1
        GLES20.glUniform1i(sFboTextureHandle, 2) // GLES20.GL_TEXTURE2
        // モード切替
        GLES20.glUniform1i(iDrawModeHandle, FRAGMENT_SHADER_DRAW_MODE_CANVAS_BITMAP)
        checkGlError("glUniform1i sSurfaceTextureHandle sCanvasTextureHandle iDrawModeHandle")

        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices)
        checkGlError("glVertexAttribPointer maPosition")
        GLES20.glEnableVertexAttribArray(maPositionHandle)
        checkGlError("glEnableVertexAttribArray maPositionHandle")
        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices)
        checkGlError("glVertexAttribPointer maTextureHandle")
        GLES20.glEnableVertexAttribArray(maTextureHandle)
        checkGlError("glEnableVertexAttribArray maTextureHandle")

        Matrix.setIdentityM(mSTMatrix, 0)
        Matrix.setIdentityM(mMVPMatrix, 0)

        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0)
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")
        GLES20.glFinish()
    }

    /**
     * SurfaceTexture を描画する。
     * GL スレッドから呼び出すこと。
     *
     * @param isAwaitTextureUpdate カメラ映像等、テクスチャが更新されるまで描画しない場合は true。false はテクスチャが更新されてなくても描画します。
     */
    suspend fun drawSurfaceTexture(
        akariSurfaceTexture: AkariGraphicsSurfaceTexture,
        isAwaitTextureUpdate: Boolean = false,
        onTransform: ((mvpMatrix: FloatArray) -> Unit)? = null
    ) {
        // attachGlContext の前に呼ぶ必要あり。多分
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, surfaceTextureTextureId)

        // 映像を OpenGL ES で使う準備
        akariSurfaceTexture.detachGl()
        akariSurfaceTexture.attachGl(surfaceTextureTextureId)
        if (isAwaitTextureUpdate) {
            akariSurfaceTexture.awaitUpdateTexImage()
        } else {
            akariSurfaceTexture.checkAndUpdateTexImage()
        }
        akariSurfaceTexture.getTransformMatrix(mSTMatrix)

        // 描画する
        // glError 1282 の原因とかになる
        GLES20.glUseProgram(mProgram)
        checkGlError("glUseProgram")

        // テクスチャの ID をわたす
        GLES20.glUniform1i(sSurfaceTextureHandle, 0) // GLES20.GL_TEXTURE0
        GLES20.glUniform1i(sCanvasTextureHandle, 1) // GLES20.GL_TEXTURE1
        GLES20.glUniform1i(sFboTextureHandle, 2) // GLES20.GL_TEXTURE2
        // モード切替
        GLES20.glUniform1i(iDrawModeHandle, FRAGMENT_SHADER_DRAW_MODE_SURFACE_TEXTURE)
        checkGlError("glUniform1i sSurfaceTextureHandle sCanvasTextureHandle iDrawModeHandle")

        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices)
        checkGlError("glVertexAttribPointer maPosition")
        GLES20.glEnableVertexAttribArray(maPositionHandle)
        checkGlError("glEnableVertexAttribArray maPositionHandle")
        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices)
        checkGlError("glVertexAttribPointer maTextureHandle")
        GLES20.glEnableVertexAttribArray(maTextureHandle)
        checkGlError("glEnableVertexAttribArray maTextureHandle")

        // 行列を適用したい場合
        Matrix.setIdentityM(mMVPMatrix, 0)
        if (onTransform != null) {
            onTransform(mMVPMatrix)
        }

        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0)
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")
        GLES20.glFinish()
    }

    /**
     * エフェクトを適用する。
     * GL スレッドから呼び出すこと。
     */
    fun applyEffect(akariEffectShader: AkariGraphicsEffectShader) {
        // FBO のテクスチャユニットを渡して描画
        akariEffectShader.applyEffect(2) // GLES20.GL_TEXTURE2

        // プログラム（シェーダー）を戻す
        GLES20.glUseProgram(mProgram)
        checkGlError("glUseProgram")
    }

    /**
     * バーテックスシェーダ、フラグメントシェーダーをコンパイルする。
     * GL スレッドから呼び出すこと。
     */
    internal fun prepareShader() {
        mProgram = createProgram(
            vertexSource = VERTEX_SHADER,
            // TODO HLG だろうと samplerExternalOES から HDR のフレームが取れてそう
            fragmentSource = if (isEnableTenBitHdr) FRAGMENT_SHADER_10BIT_HDR else FRAGMENT_SHADER
        )
        if (mProgram == 0) {
            throw RuntimeException("failed creating program")
        }
        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition")
        checkGlError("glGetAttribLocation aPosition")
        if (maPositionHandle == -1) {
            throw RuntimeException("Could not get attrib location for aPosition")
        }
        maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord")
        checkGlError("glGetAttribLocation aTextureCoord")
        if (maTextureHandle == -1) {
            throw RuntimeException("Could not get attrib location for aTextureCoord")
        }
        muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        checkGlError("glGetUniformLocation uMVPMatrix")
        if (muMVPMatrixHandle == -1) {
            throw RuntimeException("Could not get attrib location for uMVPMatrix")
        }
        muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix")
        checkGlError("glGetUniformLocation uSTMatrix")
        if (muSTMatrixHandle == -1) {
            throw RuntimeException("Could not get attrib location for uSTMatrix")
        }
        sSurfaceTextureHandle = GLES20.glGetUniformLocation(mProgram, "sSurfaceTexture")
        checkGlError("glGetUniformLocation sSurfaceTexture")
        if (sSurfaceTextureHandle == -1) {
            throw RuntimeException("Could not get attrib location for sSurfaceTexture")
        }
        sCanvasTextureHandle = GLES20.glGetUniformLocation(mProgram, "sCanvasTexture")
        checkGlError("glGetUniformLocation sCanvasTexture")
        if (sCanvasTextureHandle == -1) {
            throw RuntimeException("Could not get attrib location for sCanvasTexture")
        }
        sFboTextureHandle = GLES20.glGetUniformLocation(mProgram, "sFboTexture")
        checkGlError("glGetUniformLocation sFboTexture")
        if (sFboTextureHandle == -1) {
            throw RuntimeException("Could not get attrib location for sFboTexture")
        }
        iDrawModeHandle = GLES20.glGetUniformLocation(mProgram, "iDrawMode")
        checkGlError("glGetUniformLocation iDrawMode")
        if (iDrawModeHandle == -1) {
            throw RuntimeException("Could not get attrib location for iDrawMode")
        }

        // テクスチャ ID を払い出してもらう
        // SurfaceTexture / Canvas Bitmap 用
        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)

        surfaceTextureTextureId = textures[0]
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, surfaceTextureTextureId)
        checkGlError("glBindTexture cameraTextureId")
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        checkGlError("glTexParameter")

        canvasTextureTextureId = textures[1]
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, canvasTextureTextureId)
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        checkGlError("glTexParameter")

        // アルファブレンディング
        // Canvas で書いた際に、透明な部分は透明になるように
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        checkGlError("glEnable GLES20.GL_BLEND")

        // フレームバッファオブジェクトの用意
        prepareFbo()
    }

    /**
     * 描画前に呼び出す。描画先を FBO にします。
     * GL スレッドから呼び出すこと。
     */
    internal fun prepareDraw() {
        // 多分いる
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        // 描画先をフレームバッファオブジェクトに
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        checkGlError("glBindFramebuffer")
    }

    /**
     * テクスチャ ID を払い出す。
     * [AkariGraphicsSurfaceTexture]はコンストラクタでテクスチャ ID を必要とするが、描画時には[surfaceTextureTextureId]に切り替える。作成のためだけに必要。
     * 破棄する場合は使う側で呼び出してください。
     *
     * @param T 返り値
     * @param action 関数
     */
    internal fun <T> genTextureId(action: (texId: Int) -> T): T {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        return action(textures.first())
    }

    /** [AkariGraphicsEffectShader]を作りやすくするためだけのやつ */
    internal fun <T> genEffect(action: (width: Int, height: Int) -> T): T {
        return action(width, height)
    }

    /**
     * 最後に呼び出す。
     * フレームバッファオブジェクトのテクスチャを描画します。これでオフスクリーンで描画されてた内容が画面に表示されるはず。
     */
    internal fun drawEnd() {
        // 描画先をデフォルトの FBO にして、Surface に描画されるように
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        checkGlError("glBindFramebuffer")

        // テクスチャの ID をわたす
        GLES20.glUniform1i(sSurfaceTextureHandle, 0) // GLES20.GL_TEXTURE0
        GLES20.glUniform1i(sCanvasTextureHandle, 1) // GLES20.GL_TEXTURE1
        GLES20.glUniform1i(sFboTextureHandle, 2) // GLES20.GL_TEXTURE2
        // モード切替
        GLES20.glUniform1i(iDrawModeHandle, FRAGMENT_SHADER_DRAW_MODE_FBO)
        checkGlError("glUniform1i sSurfaceTextureHandle sCanvasTextureHandle iDrawModeHandle")

        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices)
        checkGlError("glVertexAttribPointer maPosition")
        GLES20.glEnableVertexAttribArray(maPositionHandle)
        checkGlError("glEnableVertexAttribArray maPositionHandle")
        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices)
        checkGlError("glVertexAttribPointer maTextureHandle")
        GLES20.glEnableVertexAttribArray(maTextureHandle)
        checkGlError("glEnableVertexAttribArray maTextureHandle")

        Matrix.setIdentityM(mSTMatrix, 0)
        Matrix.setIdentityM(mMVPMatrix, 0)

        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0)
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")
        GLES20.glFinish()
    }

    /** 破棄時に呼び出す */
    internal fun destroy() {
        //
    }

    private fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            throw RuntimeException("$op: glError $error")
        }
    }

    /**
     * フレームバッファオブジェクトの用意。やってる中身は grafika と同じ。
     * OpenGL ES の SurfaceView / MediaCodec のサイズと同じ大きさで FBO のテクスチャを作ります
     * [android.view.SurfaceHolder.setFixedSize]や[android.media.MediaFormat.KEY_WIDTH]参照
     */
    private fun prepareFbo() {
        // フレームバッファオブジェクトの保存先になるテクスチャを作成
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        fboTextureId = textures.first()
        checkGlError("fbo glGenTextures")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId)
        checkGlError("fbo glActiveTexture glBindTexture")

        // テクスチャ ストレージの作成
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)

        // テクスチャの補完とか
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        checkGlError("fbo glTexParameter");

        // フレームバッファオブジェクトを作り、テクスチャをバインドする
        val frameBuffers = IntArray(1)
        GLES20.glGenFramebuffers(1, frameBuffers, 0)
        checkGlError("fbo glGenFramebuffers")
        framebuffer = frameBuffers.first()
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        checkGlError("fbo glBindFramebuffer ")

        // 深度バッファを作りバインドする
        val depthBuffers = IntArray(1)
        GLES20.glGenRenderbuffers(1, depthBuffers, 0)
        checkGlError("fbo glGenRenderbuffers")
        depthBuffer = depthBuffers.first()
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthBuffer)
        checkGlError("fbo glBindRenderbuffer")

        // 深度バッファ用のストレージを作る
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height)
        checkGlError("fbo glRenderbufferStorage")

        // 深度バッファとテクスチャ (カラーバッファ) をフレームバッファオブジェクトにアタッチする
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depthBuffer)
        checkGlError("fbo glFramebufferRenderbuffer")
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTextureId, 0)
        checkGlError("fbo glFramebufferTexture2D")

        // 完了したか確認
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer not complete, status = $status")
        }

        // デフォルトのフレームバッファに戻す
        // 描画の際には glBindFramebuffer で FBO に描画できる
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    /**
     * GLSL（フラグメントシェーダー・バーテックスシェーダー）をコンパイルして、OpenGL ES とリンクする
     *
     * @throws GlslSyntaxErrorException 構文エラーの場合に投げる
     * @throws RuntimeException それ以外
     * @return 0 以外で成功
     */
    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) {
            return 0
        }
        val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) {
            return 0
        }
        var program = GLES20.glCreateProgram()
        checkGlError("glCreateProgram")
        if (program == 0) {
            return 0
        }
        GLES20.glAttachShader(program, vertexShader)
        checkGlError("glAttachShader")
        GLES20.glAttachShader(program, pixelShader)
        checkGlError("glAttachShader")
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        return program
    }

    /**
     * GLSL（フラグメントシェーダー・バーテックスシェーダー）のコンパイルをする
     *
     * @throws GlslSyntaxErrorException 構文エラーの場合に投げる
     * @throws RuntimeException それ以外
     * @return 0 以外で成功
     */
    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        checkGlError("glCreateShader type=$shaderType")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            // 失敗したら例外を投げる。その際に構文エラーのメッセージを取得する
            val syntaxErrorMessage = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw GlslSyntaxErrorException(syntaxErrorMessage)
            // ここで return 0 しても例外を投げるので意味がない
            // shader = 0
        }
        return shader
    }

    companion object {
        private const val FLOAT_SIZE_BYTES = 4
        private const val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES
        private const val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
        private const val TRIANGLE_VERTICES_DATA_UV_OFFSET = 3

        private val mTriangleVerticesData = floatArrayOf(
            -1.0f, -1.0f, 0f, 0f, 0f,
            1.0f, -1.0f, 0f, 1f, 0f,
            -1.0f, 1.0f, 0f, 0f, 1f,
            1.0f, 1.0f, 0f, 1f, 1f
        )

        private const val VERTEX_SHADER = """#version 300 es
in vec4 aPosition;
in vec4 aTextureCoord;

uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;

out vec2 vTextureCoord;

void main() {
  gl_Position = uMVPMatrix * aPosition;
  vTextureCoord = (uSTMatrix * aTextureCoord).xy;
}
"""

        // iDrawMode に渡す定数
        private const val FRAGMENT_SHADER_DRAW_MODE_SURFACE_TEXTURE = 1
        private const val FRAGMENT_SHADER_DRAW_MODE_CANVAS_BITMAP = 2
        private const val FRAGMENT_SHADER_DRAW_MODE_FBO = 3

        private const val FRAGMENT_SHADER_10BIT_HDR = """#version 300 es
#extension GL_EXT_YUV_target : require
precision mediump float;

in vec2 vTextureCoord;
uniform sampler2D sCanvasTexture;
uniform sampler2D sFboTexture;
uniform __samplerExternal2DY2YEXT sSurfaceTexture;

// 何を描画するか
// 1 SurfaceTexture（カメラや動画のデコード映像）
// 2 Bitmap（テキストや画像を描画した Canvas）
// 3 FBO
uniform int iDrawMode;

// 出力色
out vec4 FragColor;

// https://github.com/android/camera-samples/blob/a07d5f1667b1c022dac2538d1f553df20016d89c/Camera2Video/app/src/main/java/com/example/android/camera2/video/HardwarePipeline.kt#L107
vec3 yuvToRgb(vec3 yuv) {
  const vec3 yuvOffset = vec3(0.0625, 0.5, 0.5);
  const mat3 yuvToRgbColorTransform = mat3(
    1.1689f, 1.1689f, 1.1689f,
    0.0000f, -0.1881f, 2.1502f,
    1.6853f, -0.6530f, 0.0000f
  );
  return clamp(yuvToRgbColorTransform * (yuv - yuvOffset), 0.0, 1.0);
}

void main() {   
  vec4 outColor = vec4(0.0, 0.0, 0.0, 1.0);

  if (iDrawMode == 1) {
    outColor.rgb = yuvToRgb(texture(sSurfaceTexture, vTextureCoord).rgb);
  } else if (iDrawMode == 2) {
    // テクスチャ座標なので Y を反転
    outColor = texture(sCanvasTexture, vec2(vTextureCoord.x, 1.0 - vTextureCoord.y));
  } else if (iDrawMode == 3) {
    outColor = texture(sFboTexture, vTextureCoord);
  }

  FragColor = outColor;
}
"""

        private const val FRAGMENT_SHADER = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;

in vec2 vTextureCoord;
uniform sampler2D sCanvasTexture;
uniform sampler2D sFboTexture;
uniform samplerExternalOES sSurfaceTexture;

// 何を描画するか
// 1 SurfaceTexture（カメラや動画のデコード映像）
// 2 Bitmap（テキストや画像を描画した Canvas）
// 3 FBO
uniform int iDrawMode;

// 出力色
out vec4 FragColor;

void main() {   
  vec4 outColor = vec4(0.0, 0.0, 0.0, 1.0);

  if (iDrawMode == 1) {
    outColor = texture(sSurfaceTexture, vTextureCoord);
  } else if (iDrawMode == 2) {
    // テクスチャ座標なので Y を反転
    outColor = texture(sCanvasTexture, vec2(vTextureCoord.x, 1.0 - vTextureCoord.y));
  } else if (iDrawMode == 3) {
    outColor = texture(sFboTexture, vTextureCoord);
  }

  FragColor = outColor;
}
"""
    }

}