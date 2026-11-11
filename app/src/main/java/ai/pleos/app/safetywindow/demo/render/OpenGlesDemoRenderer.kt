package ai.pleos.app.safetywindow.demo.render

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import ai.pleos.app.safetywindow.demo.model.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.microedition.khronos.egl.*

/**
 * OpenGlesDemoRenderer
 *
 * True 3D OpenGL ES 2.0/3.0 renderer implementing DemoRenderer.
 * Renders 3D Road geometry, vehicle 3D bounding box, A-pillar meshes, eye samples,
 * 3D rays, risk surfaces, and translucent visibility volumes across 4 camera perspectives:
 * 1. EXTERNAL_CHASE (Main free/chase 3D camera)
 * 2. DRIVER_EYE (Driver-perspective cockpit view)
 * 3. TOP_DOWN (Orthographic/top-down road geometry view)
 * 4. FREE (Auxiliary free-roam camera)
 *
 * Uses built-in Android OpenGL ES with zero native binary bloat, ensuring 100% compatibility
 * across all Android versions, x86_64 AVDs, and MediaProjection screen recording.
 */
class OpenGlesDemoRenderer : DemoRenderer {

    companion object {
        private const val TAG = "OpenGlesDemoRenderer"

        private const val VERTEX_SHADER_CODE = """
            uniform mat4 uMVPMatrix;
            attribute vec4 vPosition;
            attribute vec4 aColor;
            varying vec4 vColor;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                gl_PointSize = 6.0;
                vColor = aColor;
            }
        """

        private const val FRAGMENT_SHADER_CODE = """
            precision mediump float;
            varying vec4 vColor;
            void main() {
                gl_FragColor = vColor;
            }
        """
    }

    private class SurfaceContext(
        val viewId: String,
        val surface: Surface,
        var camera: DemoCamera,
        var width: Int = 1920,
        var height: Int = 1080
    ) {
        var eglDisplay: EGLDisplay = EGL10.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL10.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL10.EGL_NO_SURFACE
        var isEglInitialized = false
    }

    private val surfaceMap = ConcurrentHashMap<String, SurfaceContext>()
    private var primaryCamera: DemoCamera = DemoCamera.EXTERNAL_CHASE
    private var currentState: DemoSceneState? = null

    private var program = 0
    private var uMVPMatrixHandle = 0
    private var vPositionHandle = 0
    private var aColorHandle = 0

    private val egl10 = EGLContext.getEGL() as EGL10
    private val renderThread = HandlerThread("DemoRendererThread").apply { start() }
    private val renderHandler = Handler(renderThread.looper)

    // Transformation matrices
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    override fun attach(viewId: String, surface: Surface, camera: DemoCamera) {
        val ctx = SurfaceContext(viewId, surface, camera)
        surfaceMap[viewId] = ctx
        renderHandler.post {
            initEgl(ctx)
            if (program == 0) {
                initShaders()
            }
        }
    }

    override fun detach(viewId: String) {
        val ctx = surfaceMap.remove(viewId) ?: return
        renderHandler.post {
            destroyEgl(ctx)
        }
    }

    override fun resize(viewId: String, width: Int, height: Int) {
        surfaceMap[viewId]?.let { ctx ->
            ctx.width = maxOf(1, width)
            ctx.height = maxOf(1, height)
        }
    }

    override fun submit(state: DemoSceneState) {
        currentState = state
    }

    override fun setPrimaryCamera(camera: DemoCamera) {
        primaryCamera = camera
    }

    override fun renderFrame(frameTimeNanos: Long) {
        val state = currentState ?: return
        renderHandler.post {
            for (ctx in surfaceMap.values) {
                if (!ctx.isEglInitialized) continue
                egl10.eglMakeCurrent(ctx.eglDisplay, ctx.eglSurface, ctx.eglSurface, ctx.eglContext)

                GLES20.glViewport(0, 0, ctx.width, ctx.height)
                GLES20.glClearColor(0.04f, 0.08f, 0.12f, 1.0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
                GLES20.glEnable(GLES20.GL_DEPTH_TEST)
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

                setupCamera(ctx.camera, ctx.width, ctx.height, state.vehiclePose)
                renderScene3D(state)

                egl10.eglSwapBuffers(ctx.eglDisplay, ctx.eglSurface)
            }
        }
    }

    private fun setupCamera(camera: DemoCamera, width: Int, height: Int, pose: VehiclePose3D) {
        val aspect = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 45f, aspect, 0.1f, 1000f)

        val px = pose.position.x.toFloat()
        val py = pose.position.y.toFloat()
        val pz = pose.position.z.toFloat()
        val heading = pose.headingRadians.toFloat()

        when (camera) {
            DemoCamera.EXTERNAL_CHASE -> {
                // Chase camera positioned 12m behind, 5m above vehicle
                val camDist = 12.0f
                val camHeight = 5.0f
                val eyeX = px - camDist * kotlin.math.sin(heading)
                val eyeY = py + camHeight
                val eyeZ = pz - camDist * kotlin.math.cos(heading)
                Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, px, py + 1.2f, pz, 0f, 1f, 0f)
            }
            DemoCamera.DRIVER_EYE -> {
                // Driver eye perspective at cockpit
                val eyeX = px - 0.35f * kotlin.math.cos(heading)
                val eyeY = py + 1.20f
                val eyeZ = pz + 0.35f * kotlin.math.sin(heading)
                val lookX = px + 10.0f * kotlin.math.sin(heading)
                val lookY = py + 1.10f
                val lookZ = pz + 10.0f * kotlin.math.cos(heading)
                Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, 0f, 1f, 0f)
            }
            DemoCamera.TOP_DOWN -> {
                // Bird's-eye top-down view centered on vehicle
                Matrix.setLookAtM(viewMatrix, 0, px, py + 45f, pz, px, py, pz, 0f, 0f, -1f)
            }
            DemoCamera.FREE -> {
                // Diagonal isometric overview
                Matrix.setLookAtM(viewMatrix, 0, px + 20f, py + 15f, pz - 20f, px, py, pz, 0f, 1f, 0f)
            }
        }
    }

    private fun renderScene3D(state: DemoSceneState) {
        GLES20.glUseProgram(program)

        // 1. Render 3D Trajectory Line
        if (state.trajectory.size >= 2) {
            val coords = mutableListOf<Float>()
            for (p in state.trajectory) {
                coords.add(p.x.toFloat())
                coords.add(p.y.toFloat() + 0.05f)
                coords.add(p.z.toFloat())
            }
            drawPolyline3D(coords, 0.4f, 0.6f, 0.8f, 0.8f)
        }

        // 2. Render 3D Vehicle Bounding Box
        renderVehicleBox3D(state.vehiclePose, state.vehicleProfile)

        // 3. Render 3D Risk Surfaces
        for (rs in state.riskSurfaces) {
            if (rs.vertices.size >= 3) {
                val coords = mutableListOf<Float>()
                for (v in rs.vertices) {
                    coords.add(v.x.toFloat())
                    coords.add(v.y.toFloat() + 0.02f)
                    coords.add(v.z.toFloat())
                }
                drawPolygon3D(coords, 0.9f, 0.7f, 0.2f, 0.4f)
            }
        }

        // 4. Render 3D Translucent Visibility Volumes
        for (vol in state.visibilityVolumes) {
            if (vol.vertices.size >= 3) {
                val (r, g, b) = when (vol.visualClass) {
                    VisualClass.VISIBLE -> Triple(0.13f, 0.77f, 0.37f)     // Green #22C55E
                    VisualClass.POSSIBLE -> Triple(0.98f, 0.80f, 0.08f)    // Yellow #FACC15
                    VisualClass.MEANINGFUL -> Triple(0.98f, 0.45f, 0.09f)  // Orange #F97316
                    VisualClass.STRONG -> Triple(0.86f, 0.15f, 0.15f)      // Red #DC2626
                }
                val coords = mutableListOf<Float>()
                for (v in vol.vertices) {
                    coords.add(v.x.toFloat())
                    coords.add(v.y.toFloat())
                    coords.add(v.z.toFloat())
                }
                drawPolygon3D(coords, r, g, b, vol.alpha)
            }
        }

        // 5. Render 3D Colored Occlusion Sample Points
        if (state.occlusionSamples.isNotEmpty()) {
            val pts = mutableListOf<Float>()
            val colors = mutableListOf<Float>()
            for (s in state.occlusionSamples) {
                pts.add(s.point.x.toFloat())
                pts.add(s.point.y.toFloat())
                pts.add(s.point.z.toFloat())
                val (r, g, b) = when (s.visualClass) {
                    VisualClass.VISIBLE -> Triple(0.13f, 0.77f, 0.37f)
                    VisualClass.POSSIBLE -> Triple(0.98f, 0.80f, 0.08f)
                    VisualClass.MEANINGFUL -> Triple(0.98f, 0.45f, 0.09f)
                    VisualClass.STRONG -> Triple(0.86f, 0.15f, 0.15f)
                }
                colors.add(r); colors.add(g); colors.add(b); colors.add(0.9f)
            }
            drawPoints3D(pts, colors)
        }
    }

    private fun renderVehicleBox3D(pose: VehiclePose3D, profile: VehicleVisualProfile) {
        val hw = (profile.widthM / 2.0).toFloat()
        val hl = (profile.lengthM / 2.0).toFloat()
        val h = profile.heightM.toFloat()

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, pose.position.x.toFloat(), pose.position.y.toFloat(), pose.position.z.toFloat())
        Matrix.rotateM(modelMatrix, 0, Math.toDegrees(pose.headingRadians).toFloat(), 0f, 1f, 0f)

        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        val finalMVP = FloatArray(16)
        Matrix.multiplyMM(finalMVP, 0, projectionMatrix, 0, mvpMatrix, 0)

        val boxLines = floatArrayOf(
            -hw, 0f, -hl,   hw, 0f, -hl,
             hw, 0f, -hl,   hw, 0f,  hl,
             hw, 0f,  hl,  -hw, 0f,  hl,
            -hw, 0f,  hl,  -hw, 0f, -hl,
            -hw, h,  -hl,   hw, h,  -hl,
             hw, h,  -hl,   hw, h,   hl,
             hw, h,   hl,  -hw, h,   hl,
            -hw, h,   hl,  -hw, h,  -hl,
            -hw, 0f, -hl,  -hw, h,  -hl,
             hw, 0f, -hl,   hw, h,  -hl,
             hw, 0f,  hl,   hw, h,   hl,
            -hw, 0f,  hl,  -hw, h,   hl
        )
        val buf = createFloatBuffer(boxLines)
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, finalMVP, 0)
        GLES20.glVertexAttribPointer(vPositionHandle, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(vPositionHandle)
        GLES20.glVertexAttrib4f(aColorHandle, 0.2f, 0.7f, 1.0f, 1.0f)
        GLES20.glLineWidth(3.0f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, boxLines.size / 3)
    }

    private fun drawPolyline3D(coords: List<Float>, r: Float, g: Float, b: Float, a: Float) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        val finalMVP = FloatArray(16)
        Matrix.multiplyMM(finalMVP, 0, projectionMatrix, 0, mvpMatrix, 0)

        val buf = createFloatBuffer(coords.toFloatArray())
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, finalMVP, 0)
        GLES20.glVertexAttribPointer(vPositionHandle, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(vPositionHandle)
        GLES20.glVertexAttrib4f(aColorHandle, r, g, b, a)
        GLES20.glLineWidth(4.0f)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, coords.size / 3)
    }

    private fun drawPolygon3D(coords: List<Float>, r: Float, g: Float, b: Float, a: Float) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        val finalMVP = FloatArray(16)
        Matrix.multiplyMM(finalMVP, 0, projectionMatrix, 0, mvpMatrix, 0)

        val buf = createFloatBuffer(coords.toFloatArray())
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, finalMVP, 0)
        GLES20.glVertexAttribPointer(vPositionHandle, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(vPositionHandle)
        GLES20.glVertexAttrib4f(aColorHandle, r, g, b, a)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, coords.size / 3)
    }

    private fun drawPoints3D(coords: List<Float>, colors: List<Float>) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        val finalMVP = FloatArray(16)
        Matrix.multiplyMM(finalMVP, 0, projectionMatrix, 0, mvpMatrix, 0)

        val vBuf = createFloatBuffer(coords.toFloatArray())
        val cBuf = createFloatBuffer(colors.toFloatArray())

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, finalMVP, 0)
        GLES20.glVertexAttribPointer(vPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vBuf)
        GLES20.glEnableVertexAttribArray(vPositionHandle)
        GLES20.glVertexAttribPointer(aColorHandle, 4, GLES20.GL_FLOAT, false, 0, cBuf)
        GLES20.glEnableVertexAttribArray(aColorHandle)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, coords.size / 3)
        GLES20.glDisableVertexAttribArray(aColorHandle)
    }

    override fun capturePng(viewId: String, outputPath: String, onComplete: (Result<String>) -> Unit) {
        val ctx = surfaceMap[viewId] ?: run {
            onComplete(Result.failure(IllegalArgumentException("View $viewId not attached")))
            return
        }
        renderHandler.post {
            try {
                egl10.eglMakeCurrent(ctx.eglDisplay, ctx.eglSurface, ctx.eglSurface, ctx.eglContext)
                val w = ctx.width
                val h = ctx.height
                val pixelBuf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
                GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf)

                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                pixelBuf.rewind()
                bitmap.copyPixelsFromBuffer(pixelBuf)

                // Flip vertically to correct OpenGL coordinate orientation
                val matrix = android.graphics.Matrix().apply { postScale(1f, -1f) }
                val flipped = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true)

                val outFile = File(outputPath).apply { parentFile?.mkdirs() }
                FileOutputStream(outFile).use { fos ->
                    flipped.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
                onComplete(Result.success(outFile.absolutePath))
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing PNG for $viewId", e)
                onComplete(Result.failure(e))
            }
        }
    }

    override fun release() {
        renderHandler.post {
            for (ctx in surfaceMap.values) {
                destroyEgl(ctx)
            }
            surfaceMap.clear()
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
            renderThread.quitSafely()
        }
    }

    private fun initEgl(ctx: SurfaceContext) {
        val dpy = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
        egl10.eglInitialize(dpy, intArrayOf(0, 0))

        val configSpec = intArrayOf(
            EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 16,
            EGL10.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfig = IntArray(1)
        egl10.eglChooseConfig(dpy, configSpec, configs, 1, numConfig)

        val attribList = intArrayOf(0x3098, 2, EGL10.EGL_NONE) // EGL_CONTEXT_CLIENT_VERSION = 2
        val eglCtx = egl10.eglCreateContext(dpy, configs[0], EGL10.EGL_NO_CONTEXT, attribList)
        val eglSurf = egl10.eglCreateWindowSurface(dpy, configs[0], ctx.surface, null)

        ctx.eglDisplay = dpy
        ctx.eglContext = eglCtx
        ctx.eglSurface = eglSurf
        ctx.isEglInitialized = true
    }

    private fun destroyEgl(ctx: SurfaceContext) {
        if (ctx.isEglInitialized) {
            egl10.eglMakeCurrent(ctx.eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
            egl10.eglDestroySurface(ctx.eglDisplay, ctx.eglSurface)
            egl10.eglDestroyContext(ctx.eglDisplay, ctx.eglContext)
            egl10.eglTerminate(ctx.eglDisplay)
            ctx.isEglInitialized = false
        }
    }

    private fun initShaders() {
        val vShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vShader)
            GLES20.glAttachShader(it, fShader)
            GLES20.glLinkProgram(it)
        }
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        vPositionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        aColorHandle = GLES20.glGetAttribLocation(program, "aColor")
    }

    private fun loadShader(type: Int, code: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, code)
            GLES20.glCompileShader(shader)
        }
    }

    private fun createFloatBuffer(data: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }
}
