package com.falak.livewallpaper

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * OpenGL ES 3.0 analytic celestial renderer for Falak.
 */
class FalakRenderer(
    private val context: Context,
    private val config: FalakConfig = FalakConfig()
) {
    private var program = 0
    private var vao = 0
    private var vbo = 0

    private var uResolution = -1
    private var uTime = -1
    private var uPulseTime = -1
    private var uCamPos = -1
    private var uCamPitch = -1
    private var uCamYaw = -1
    private var uFovY = -1
    private var uRingWidth = -1
    private var uRingBrightness = -1
    private var uPlanetOrbitR = -1
    private var uPlanetAngles = -1
    private var uPlanetRadii = -1
    private var uPlanetDiffuse = -1
    private var uTerminatorWrap = -1
    private var uRimStrength = -1
    private var uSunCoreR = -1
    private var uSunGlow = -1
    private var uGrainStrength = -1
    private var uStarfieldStrength = -1
    private var uStarSize = -1
    private var uStarDensity = -1
    private var uGlitterSpeed = -1

    fun init(): Boolean {
        val vertSrc = readRawResource(R.raw.falak_vert)
        val fragSrc = readRawResource(R.raw.falak_frag)

        val vertShader = compileShader(GLES30.GL_VERTEX_SHADER, vertSrc)
        if (vertShader == 0) return false
        val fragShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc)
        if (fragShader == 0) {
            GLES30.glDeleteShader(vertShader)
            return false
        }

        program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertShader)
        GLES30.glAttachShader(program, fragShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        GLES30.glDeleteShader(vertShader)
        GLES30.glDeleteShader(fragShader)

        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program link failed: ${GLES30.glGetProgramInfoLog(program)}")
            GLES30.glDeleteProgram(program)
            program = 0
            return false
        }

        cacheUniforms()
        setupFullscreenTriangle()
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        return true
    }

    fun draw(
        width: Int,
        height: Int,
        timeSeconds: Float,
        pulseTimeSeconds: Float,
        planetAnglesRad: FloatArray,
        starfield: FalakPreferences.StarfieldSettings = FalakPreferences.StarfieldSettings()
    ) {
        if (program == 0 || width <= 0 || height <= 0) return

        GLES30.glViewport(0, 0, width, height)
        GLES30.glUseProgram(program)

        GLES30.glUniform2f(uResolution, width.toFloat(), height.toFloat())
        GLES30.glUniform1f(uTime, timeSeconds)
        GLES30.glUniform1f(uPulseTime, pulseTimeSeconds)

        GLES30.glUniform3f(uCamPos, config.camPosX, config.camPosY, config.camPosZ)
        GLES30.glUniform1f(uCamPitch, config.camPitchRad)
        GLES30.glUniform1f(uCamYaw, config.camYawRad)
        GLES30.glUniform1f(uFovY, config.fovYRad)

        GLES30.glUniform1f(uRingWidth, config.ringWidthPxAt1024)
        GLES30.glUniform1f(uRingBrightness, config.ringBrightness)

        GLES30.glUniform1fv(uPlanetOrbitR, 5, config.planetOrbitRs, 0)
        GLES30.glUniform1fv(uPlanetAngles, 5, planetAnglesRad, 0)
        GLES30.glUniform1fv(uPlanetRadii, 5, config.planetRadii, 0)

        GLES30.glUniform1f(uPlanetDiffuse, config.planetDiffuse)
        GLES30.glUniform1f(uTerminatorWrap, config.terminatorWrap)
        GLES30.glUniform1f(uRimStrength, config.rimStrength)

        GLES30.glUniform1f(uSunCoreR, config.sunCoreR)
        GLES30.glUniform1f(uSunGlow, config.sunGlow)
        GLES30.glUniform1f(uGrainStrength, config.grainStrength)
        GLES30.glUniform1f(uStarfieldStrength, config.starfieldStrength)
        GLES30.glUniform1f(uStarSize, starfield.sizeScale)
        GLES30.glUniform1f(uStarDensity, starfield.densityScale)
        GLES30.glUniform1f(uGlitterSpeed, starfield.glitterSpeed)

        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        if (vao != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
            vao = 0
        }
        if (vbo != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
            vbo = 0
        }
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
    }

    private fun setupFullscreenTriangle() {
        val vertices = floatArrayOf(
            -1.0f, -1.0f,
             3.0f, -1.0f,
            -1.0f,  3.0f
        )
        val buffer: FloatBuffer = ByteBuffer
            .allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        buffer.position(0)

        val vaos = IntArray(1)
        val vbos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        GLES30.glGenBuffers(1, vbos, 0)
        vao = vaos[0]
        vbo = vbos[0]

        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertices.size * 4,
            buffer,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun uniformArrayLoc(name: String): Int {
        val loc = GLES30.glGetUniformLocation(program, name)
        return if (loc >= 0) loc else GLES30.glGetUniformLocation(program, "$name[0]")
    }

    private fun cacheUniforms() {
        uResolution = GLES30.glGetUniformLocation(program, "uResolution")
        uTime = GLES30.glGetUniformLocation(program, "uTime")
        uPulseTime = GLES30.glGetUniformLocation(program, "uPulseTime")
        uCamPos = GLES30.glGetUniformLocation(program, "uCamPos")
        uCamPitch = GLES30.glGetUniformLocation(program, "uCamPitch")
        uCamYaw = GLES30.glGetUniformLocation(program, "uCamYaw")
        uFovY = GLES30.glGetUniformLocation(program, "uFovY")
        uRingWidth = GLES30.glGetUniformLocation(program, "uRingWidth")
        uRingBrightness = GLES30.glGetUniformLocation(program, "uRingBrightness")
        uPlanetOrbitR = uniformArrayLoc("uPlanetOrbitR")
        uPlanetAngles = uniformArrayLoc("uPlanetAngles")
        uPlanetRadii = uniformArrayLoc("uPlanetRadii")
        uPlanetDiffuse = GLES30.glGetUniformLocation(program, "uPlanetDiffuse")
        uTerminatorWrap = GLES30.glGetUniformLocation(program, "uTerminatorWrap")
        uRimStrength = GLES30.glGetUniformLocation(program, "uRimStrength")
        uSunCoreR = GLES30.glGetUniformLocation(program, "uSunCoreR")
        uSunGlow = GLES30.glGetUniformLocation(program, "uSunGlow")
        uGrainStrength = GLES30.glGetUniformLocation(program, "uGrainStrength")
        uStarfieldStrength = GLES30.glGetUniformLocation(program, "uStarfieldStrength")
        uStarSize = GLES30.glGetUniformLocation(program, "uStarSize")
        uStarDensity = GLES30.glGetUniformLocation(program, "uStarDensity")
        uGlitterSpeed = GLES30.glGetUniformLocation(program, "uGlitterSpeed")
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Shader compile error ($type): ${GLES30.glGetShaderInfoLog(shader)}")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun readRawResource(resId: Int): String {
        return context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
    }

    companion object {
        private const val TAG = "FalakRenderer"
    }
}
