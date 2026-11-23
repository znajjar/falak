package com.falak.livewallpaper

import android.content.Context
import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Rounded rectangle region in top-left physical screen pixels for real-time frosted-glass blur.
 */
data class BackdropBlurRect(
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float,
    val cornerRadiusPx: Float
)

/**
 * Optional frosted-glass backdrop blur configuration used by MainActivity's floating M3 cards.
 * Bypassed completely (zero FBO allocation, zero extra passes) by FalakWallpaperService.
 */
data class BackdropBlurSpec(
    val alpha: Float = 0f,
    val rects: List<BackdropBlurRect> = emptyList()
)

/**
 * OpenGL ES 3.0 analytic celestial renderer for Falak, with optional lazy Dual-Kawase
 * frosted-glass backdrop blur for in-app floating UI cards.
 */
class FalakRenderer(
    private val context: Context,
    private val config: FalakConfig = FalakConfig()
) {
    private var program = 0
    private var kawaseProgram = 0
    private var compositeProgram = 0
    private var vao = 0
    private var vbo = 0

    // Main celestial shader uniforms
    private var uResolution = -1
    private var uTime = -1
    private var uPulseTime = -1
    private var uPrayerActivity = -1
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

    // Kawase blur uniforms
    private var uKawaseTex = -1
    private var uKawaseTexelSize = -1
    private var uKawaseOffset = -1

    // Composite frosted-glass uniforms
    private var uCompSceneTex = -1
    private var uCompBlurTex = -1
    private var uCompResolution = -1
    private var uCompRectCount = -1
    private var uCompRects = -1
    private var uCompRadii = -1
    private var uCompBlurAlpha = -1

    // Lazily allocated offscreen FBOs (allocated only when in-app frosted glass blur is active)
    private var fboWidth = 0
    private var fboHeight = 0
    private var blurWidth = 0
    private var blurHeight = 0
    private var sceneFbo = 0
    private var sceneTex = 0
    private var blurFboA = 0
    private var blurTexA = 0
    private var blurFboB = 0
    private var blurTexB = 0
    private var blurPipelineReady = false

    private val rectBuf = FloatArray(MAX_BLUR_RECTS * 4)
    private val radiiBuf = FloatArray(MAX_BLUR_RECTS)

    fun init(): Boolean {
        val vertSrc = readRawResource(R.raw.falak_vert)
        val fragSrc = readRawResource(R.raw.falak_frag)

        program = linkProgram(vertSrc, fragSrc)
        if (program == 0) return false

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
        starfield: FalakPreferences.StarfieldSettings = FalakPreferences.StarfieldSettings(),
        prayerActivity: Float = -1.0f,
        blurSpec: BackdropBlurSpec = BackdropBlurSpec()
    ) {
        if (program == 0 || width <= 0 || height <= 0) return

        val wantBlur = blurSpec.alpha > 0.001f && blurSpec.rects.isNotEmpty()
        val useBlur = wantBlur && ensureBlurPipeline(width, height)

        if (!useBlur) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            drawCelestialScene(
                width = width,
                height = height,
                timeSeconds = timeSeconds,
                pulseTimeSeconds = pulseTimeSeconds,
                planetAnglesRad = planetAnglesRad,
                starfield = starfield,
                prayerActivity = prayerActivity
            )
            return
        }

        // 1. Render sharp celestial scene to offscreen full-res FBO
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFbo)
        drawCelestialScene(
            width = width,
            height = height,
            timeSeconds = timeSeconds,
            pulseTimeSeconds = pulseTimeSeconds,
            planetAnglesRad = planetAnglesRad,
            starfield = starfield,
            prayerActivity = prayerActivity
        )

        // 2. Progressive 4-pass Kawase blur at 1/4 resolution for velvety frosted glass
        GLES30.glUseProgram(kawaseProgram)
        GLES30.glViewport(0, 0, blurWidth, blurHeight)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glUniform1i(uKawaseTex, 0)
        GLES30.glBindVertexArray(vao)

        runKawasePass(sceneTex, blurFboA, width, height, 0.75f)
        runKawasePass(blurTexA, blurFboB, blurWidth, blurHeight, 1.50f)
        runKawasePass(blurTexB, blurFboA, blurWidth, blurHeight, 2.50f)
        runKawasePass(blurTexA, blurFboB, blurWidth, blurHeight, 3.75f)

        // 3. Composite sharp wallpaper + frosted-glass blurred cards to screen FBO 0
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glUseProgram(compositeProgram)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneTex)
        GLES30.glUniform1i(uCompSceneTex, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, blurTexB)
        GLES30.glUniform1i(uCompBlurTex, 1)

        GLES30.glUniform2f(uCompResolution, width.toFloat(), height.toFloat())
        GLES30.glUniform1f(uCompBlurAlpha, blurSpec.alpha.coerceIn(0f, 1f))

        val count = blurSpec.rects.size.coerceAtMost(MAX_BLUR_RECTS)
        GLES30.glUniform1i(uCompRectCount, count)
        for (i in 0 until count) {
            val r = blurSpec.rects[i]
            val base = i * 4
            rectBuf[base] = r.leftPx
            rectBuf[base + 1] = r.topPx
            rectBuf[base + 2] = r.rightPx
            rectBuf[base + 3] = r.bottomPx
            radiiBuf[i] = r.cornerRadiusPx
        }
        if (count > 0) {
            GLES30.glUniform4fv(uCompRects, count, rectBuf, 0)
            GLES30.glUniform1fv(uCompRadii, count, radiiBuf, 0)
        }

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glBindVertexArray(0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun runKawasePass(
        srcTex: Int,
        dstFbo: Int,
        srcW: Int,
        srcH: Int,
        offset: Float
    ) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, dstFbo)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTex)
        GLES30.glUniform2f(
            uKawaseTexelSize,
            1.0f / srcW.coerceAtLeast(1).toFloat(),
            1.0f / srcH.coerceAtLeast(1).toFloat()
        )
        GLES30.glUniform1f(uKawaseOffset, offset)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    private fun drawCelestialScene(
        width: Int,
        height: Int,
        timeSeconds: Float,
        pulseTimeSeconds: Float,
        planetAnglesRad: FloatArray,
        starfield: FalakPreferences.StarfieldSettings,
        prayerActivity: Float
    ) {
        GLES30.glViewport(0, 0, width, height)
        GLES30.glUseProgram(program)

        GLES30.glUniform2f(uResolution, width.toFloat(), height.toFloat())
        GLES30.glUniform1f(uTime, timeSeconds)
        GLES30.glUniform1f(uPulseTime, pulseTimeSeconds)
        GLES30.glUniform1f(uPrayerActivity, prayerActivity)

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

    private fun ensureBlurPipeline(width: Int, height: Int): Boolean {
        if (kawaseProgram == 0 || compositeProgram == 0) {
            val vertSrc = readRawResource(R.raw.falak_vert)
            if (kawaseProgram == 0) {
                kawaseProgram = linkProgram(vertSrc, KAWASE_FRAG_SRC)
                if (kawaseProgram == 0) return false
                uKawaseTex = GLES30.glGetUniformLocation(kawaseProgram, "uTex")
                uKawaseTexelSize = GLES30.glGetUniformLocation(kawaseProgram, "uTexelSize")
                uKawaseOffset = GLES30.glGetUniformLocation(kawaseProgram, "uOffset")
            }
            if (compositeProgram == 0) {
                compositeProgram = linkProgram(vertSrc, COMPOSITE_FRAG_SRC)
                if (compositeProgram == 0) return false
                uCompSceneTex = GLES30.glGetUniformLocation(compositeProgram, "uSceneTex")
                uCompBlurTex = GLES30.glGetUniformLocation(compositeProgram, "uBlurTex")
                uCompResolution = GLES30.glGetUniformLocation(compositeProgram, "uResolution")
                uCompRectCount = GLES30.glGetUniformLocation(compositeProgram, "uRectCount")
                uCompRects = uniformArrayLoc(compositeProgram, "uRects")
                uCompRadii = uniformArrayLoc(compositeProgram, "uRadii")
                uCompBlurAlpha = GLES30.glGetUniformLocation(compositeProgram, "uBlurAlpha")
            }
        }

        if (blurPipelineReady && fboWidth == width && fboHeight == height) {
            return true
        }

        releaseBlurFbos()

        val bw = (width / 4).coerceAtLeast(1)
        val bh = (height / 4).coerceAtLeast(1)

        val scenePair = createColorFbo(width, height) ?: return false
        val blurAPair = createColorFbo(bw, bh) ?: run {
            deleteFbo(scenePair.first, scenePair.second)
            return false
        }
        val blurBPair = createColorFbo(bw, bh) ?: run {
            deleteFbo(scenePair.first, scenePair.second)
            deleteFbo(blurAPair.first, blurAPair.second)
            return false
        }

        sceneFbo = scenePair.first
        sceneTex = scenePair.second
        blurFboA = blurAPair.first
        blurTexA = blurAPair.second
        blurFboB = blurBPair.first
        blurTexB = blurBPair.second
        fboWidth = width
        fboHeight = height
        blurWidth = bw
        blurHeight = bh
        blurPipelineReady = true
        return true
    }

    private fun createColorFbo(w: Int, h: Int): Pair<Int, Int>? {
        val texs = IntArray(1)
        val fbos = IntArray(1)
        GLES30.glGenTextures(1, texs, 0)
        GLES30.glGenFramebuffers(1, fbos, 0)
        val tex = texs[0]
        val fbo = fbos[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA8,
            w,
            h,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            tex,
            0
        )

        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "FBO incomplete (${w}x${h}): 0x${Integer.toHexString(status)}")
            deleteFbo(fbo, tex)
            return null
        }
        return Pair(fbo, tex)
    }

    private fun deleteFbo(fbo: Int, tex: Int) {
        if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        if (tex != 0) GLES30.glDeleteTextures(1, intArrayOf(tex), 0)
    }

    private fun releaseBlurFbos() {
        deleteFbo(sceneFbo, sceneTex)
        deleteFbo(blurFboA, blurTexA)
        deleteFbo(blurFboB, blurTexB)
        sceneFbo = 0
        sceneTex = 0
        blurFboA = 0
        blurTexA = 0
        blurFboB = 0
        blurTexB = 0
        fboWidth = 0
        fboHeight = 0
        blurWidth = 0
        blurHeight = 0
        blurPipelineReady = false
    }

    fun release() {
        releaseBlurFbos()
        if (vao != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
            vao = 0
        }
        if (vbo != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
            vbo = 0
        }
        if (kawaseProgram != 0) {
            GLES30.glDeleteProgram(kawaseProgram)
            kawaseProgram = 0
        }
        if (compositeProgram != 0) {
            GLES30.glDeleteProgram(compositeProgram)
            compositeProgram = 0
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

    private fun uniformArrayLoc(prog: Int, name: String): Int {
        val loc = GLES30.glGetUniformLocation(prog, name)
        return if (loc >= 0) loc else GLES30.glGetUniformLocation(prog, "$name[0]")
    }

    private fun cacheUniforms() {
        uResolution = GLES30.glGetUniformLocation(program, "uResolution")
        uTime = GLES30.glGetUniformLocation(program, "uTime")
        uPulseTime = GLES30.glGetUniformLocation(program, "uPulseTime")
        uPrayerActivity = GLES30.glGetUniformLocation(program, "uPrayerActivity")
        uCamPos = GLES30.glGetUniformLocation(program, "uCamPos")
        uCamPitch = GLES30.glGetUniformLocation(program, "uCamPitch")
        uCamYaw = GLES30.glGetUniformLocation(program, "uCamYaw")
        uFovY = GLES30.glGetUniformLocation(program, "uFovY")
        uRingWidth = GLES30.glGetUniformLocation(program, "uRingWidth")
        uRingBrightness = GLES30.glGetUniformLocation(program, "uRingBrightness")
        uPlanetOrbitR = uniformArrayLoc(program, "uPlanetOrbitR")
        uPlanetAngles = uniformArrayLoc(program, "uPlanetAngles")
        uPlanetRadii = uniformArrayLoc(program, "uPlanetRadii")
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

    private fun linkProgram(vertSrc: String, fragSrc: String): Int {
        val vertShader = compileShader(GLES30.GL_VERTEX_SHADER, vertSrc)
        if (vertShader == 0) return 0
        val fragShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc)
        if (fragShader == 0) {
            GLES30.glDeleteShader(vertShader)
            return 0
        }
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vertShader)
        GLES30.glAttachShader(prog, fragShader)
        GLES30.glLinkProgram(prog)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linkStatus, 0)
        GLES30.glDeleteShader(vertShader)
        GLES30.glDeleteShader(fragShader)

        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program link failed: ${GLES30.glGetProgramInfoLog(prog)}")
            GLES30.glDeleteProgram(prog)
            return 0
        }
        return prog
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
        private const val MAX_BLUR_RECTS = 2

        private const val KAWASE_FRAG_SRC = """#version 300 es
precision mediump float;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uTex;
uniform vec2 uTexelSize;
uniform float uOffset;
void main() {
    vec2 d = uTexelSize * (uOffset + 0.5);
    vec3 sum = texture(uTex, vUv).rgb * 0.20;
    sum += texture(uTex, vUv + vec2( d.x,  d.y)).rgb * 0.20;
    sum += texture(uTex, vUv + vec2(-d.x,  d.y)).rgb * 0.20;
    sum += texture(uTex, vUv + vec2( d.x, -d.y)).rgb * 0.20;
    sum += texture(uTex, vUv + vec2(-d.x, -d.y)).rgb * 0.20;
    fragColor = vec4(sum, 1.0);
}
"""

        private const val COMPOSITE_FRAG_SRC = """#version 300 es
precision highp float;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uSceneTex;
uniform sampler2D uBlurTex;
uniform vec2 uResolution;
uniform int uRectCount;
uniform vec4 uRects[2];
uniform float uRadii[2];
uniform float uBlurAlpha;

float sdRoundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec3 sceneCol = texture(uSceneTex, vUv).rgb;
    vec2 px = vec2(gl_FragCoord.x, uResolution.y - gl_FragCoord.y);

    float mask = 0.0;
    for (int i = 0; i < 2; i++) {
        if (i >= uRectCount) break;
        vec4 r = uRects[i];
        vec2 halfSize = max(0.5 * (r.zw - r.xy), vec2(0.0));
        if (halfSize.x <= 0.5 || halfSize.y <= 0.5) continue;
        vec2 center = 0.5 * (r.xy + r.zw);
        float rad = clamp(uRadii[i], 0.0, min(halfSize.x, halfSize.y));
        float d = sdRoundedBox(px - center, halfSize, rad);
        mask = max(mask, 1.0 - smoothstep(-0.75, 0.75, d));
    }

    float blend = mask * clamp(uBlurAlpha, 0.0, 1.0);
    if (blend <= 0.001) {
        fragColor = vec4(sceneCol, 1.0);
        return;
    }

    vec3 blurCol = texture(uBlurTex, vUv).rgb;
    // Soft luminous diffusion + frosted obsidian tint so warm starlight glows cleanly behind text
    vec3 liftedBlur = min(blurCol * 1.18 + vec3(0.014, 0.016, 0.022), vec3(1.0));
    vec3 frosted = mix(liftedBlur, vec3(0.068, 0.076, 0.102), 0.36);
    float dither = (hash12(gl_FragCoord.xy) - 0.5) * 0.004;
    vec3 outCol = mix(sceneCol, clamp(frosted + vec3(dither), 0.0, 1.0), blend);
    fragColor = vec4(outCol, 1.0);
}
"""
    }
}
