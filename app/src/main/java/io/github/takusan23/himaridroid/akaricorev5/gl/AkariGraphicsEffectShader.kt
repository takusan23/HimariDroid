package io.github.takusan23.himaridroid.akaricorev5.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AkariGraphicsEffectShader(
    private val width: Int,
    private val height: Int,
    private val xStart: Float,
    private val xEnd: Float,
    private val yStart: Float,
    private val yEnd: Float,
    private val fragmentShaderCode: String
) {

    private val mTriangleVertices = ByteBuffer.allocateDirect(mTriangleVerticesData.size * FLOAT_SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var mProgram = 0
    private var maPositionHandle = 0

    // Uniform 変数のハンドル
    private var vResolutionHandle = 0
    private var vCropLocationHandle = 0
    private var sVideoFrameTextureHandle = 0

    init {
        mTriangleVertices.put(mTriangleVerticesData).position(0)
    }

    /** 準備する */
    fun prepareShader() {
        // シェーダーのコンパイル
        mProgram = createProgram(VERTEX_SHADER, fragmentShaderCode)
        if (mProgram == 0) {
            throw RuntimeException("failed creating program")
        }

        // Uniform 変数へのハンドル
        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "a_position")
        checkGlError("glGetAttribLocation maPositionHandle")
        if (maPositionHandle == -1) {
            throw RuntimeException("Could not get attrib location for maPositionHandle")
        }
        vResolutionHandle = GLES20.glGetUniformLocation(mProgram, "vResolution")
        checkGlError("glGetUniformLocation vResolution")
        if (vResolutionHandle == -1) {
            throw RuntimeException("Could not get uniform location for vResolution")
        }
        vCropLocationHandle = GLES20.glGetUniformLocation(mProgram, "vCropLocation")
        checkGlError("glGetUniformLocation vCropLocation")
        if (vCropLocationHandle == -1) {
            throw RuntimeException("Could not get uniform location for vCropLocation")
        }
        sVideoFrameTextureHandle = GLES20.glGetUniformLocation(mProgram, "sVideoFrameTexture")
        checkGlError("glGetUniformLocation sVideoFrameTexture")
        if (sVideoFrameTextureHandle == -1) {
            throw RuntimeException("Could not get uniform location for sVideoFrameTexture")
        }

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        checkGlError("glEnable GLES20.GL_BLEND")
    }

    /**
     * エフェクトを適用する
     *
     * @param fboTextureUnit フレームバッファオブジェクトのテクスチャユニット
     */
    fun applyEffect(fboTextureUnit: Int) {
        // glUseProgram する
        GLES20.glUseProgram(mProgram)
        checkGlError("glUseProgram")

        // Uniform 変数に渡していく
        // テクスチャ ID
        GLES20.glUniform1i(sVideoFrameTextureHandle, fboTextureUnit)
        // 解像度
        GLES20.glUniform2f(vResolutionHandle, width.toFloat(), height.toFloat())
        // エフェクトの範囲
        // Y 座標はテクスチャ座標が反転しているため、意図的にやっている。コーディングミスではない
        GLES20.glUniform4f(vCropLocationHandle, xStart, xEnd, 1f - yEnd, 1f - yStart)
        checkGlError("glUniform1i glUniform2f glUniform4f")

        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices)
        checkGlError("glVertexAttribPointer maPosition")
        GLES20.glEnableVertexAttribArray(maPositionHandle)
        checkGlError("glEnableVertexAttribArray maPositionHandle")
        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
        // 描画する
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")
        GLES20.glFinish()
        checkGlError("glFinish")
    }

    /** 破棄時に呼び出す */
    fun destroy() {
        GLES20.glDeleteProgram(mProgram)
    }

    /**
     * GLSL（フラグメントシェーダー・バーテックスシェーダー）をコンパイルして、OpenGL ES とリンクする
     *
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
            shader = 0
        }
        return shader
    }

    private fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            throw RuntimeException("$op: glError $error")
        }
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

        private const val VERTEX_SHADER = """
attribute vec4 a_position;

void main() {
  gl_Position = a_position;
}
"""
    }

}