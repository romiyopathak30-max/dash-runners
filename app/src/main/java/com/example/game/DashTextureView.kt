package com.example.game

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLSurfaceView
import android.view.TextureView
import java.util.concurrent.locks.LockSupport

/**
 * EGL14-backed TextureView host for OpenGL ES 2.0 rendering inside Jetpack Compose.
 * Unlike SurfaceView/GLSurfaceView, TextureView renders into a hardware SurfaceTexture
 * without registering a child SurfaceControl into ViewRootImpl's SurfaceSyncGroup,
 * preventing 1000ms SurfaceSyncGroup transaction timeouts on Android 14+.
 */
class DashTextureView(
  context: Context,
  private val renderer: GLSurfaceView.Renderer,
) : TextureView(context), TextureView.SurfaceTextureListener {

  private var renderThread: GLRenderThread? = null

  init {
    isOpaque = true
    surfaceTextureListener = this
  }

  override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
    renderThread?.requestExitAndWait()
    renderThread =
      GLRenderThread(renderer, surface, width, height).also {
        it.start()
      }
  }

  override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
    renderThread?.onWindowResize(width, height)
  }

  override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
    renderThread?.requestExitAndWait()
    renderThread = null
    return true
  }

  override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    // Frame already presented via eglSwapBuffers
  }

  fun onResume() {
    renderThread?.onResume()
  }

  fun onPause() {
    renderThread?.onPause()
  }

  override fun onDetachedFromWindow() {
    renderThread?.requestExitAndWait()
    renderThread = null
    super.onDetachedFromWindow()
  }

  private class GLRenderThread(
    private val renderer: GLSurfaceView.Renderer,
    private val surfaceTexture: SurfaceTexture,
    initialWidth: Int,
    initialHeight: Int,
  ) : Thread("DashGLThread") {

    private val lock = Object()
    private var width = initialWidth
    private var height = initialHeight
    private var sizeChanged = true
    private var paused = false
    @Volatile
    private var shouldExit = false

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    override fun run() {
      if (!initEgl()) {
        return
      }
      try {
        renderer.onSurfaceCreated(null, null)
        var nextDeadlineNs = System.nanoTime()

        while (true) {
          var currentWidth: Int
          var currentHeight: Int
          var notifyResize: Boolean
          var wasPaused = false

          synchronized(lock) {
            while (!shouldExit && (paused || width <= 0 || height <= 0)) {
              wasPaused = true
              lock.wait()
            }
            if (shouldExit) {
              return
            }
            currentWidth = width
            currentHeight = height
            notifyResize = sizeChanged
            sizeChanged = false
          }

          if (wasPaused) {
            nextDeadlineNs = System.nanoTime()
          }

          if (notifyResize) {
            renderer.onSurfaceChanged(null, currentWidth, currentHeight)
          }

          renderer.onDrawFrame(null)
          if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            val err = EGL14.eglGetError()
            if (err == EGL14.EGL_BAD_SURFACE || err == EGL14.EGL_CONTEXT_LOST) {
              return
            }
          }

          nextDeadlineNs += TARGET_FRAME_NS
          val nowNs = System.nanoTime()
          val remainingNs = nextDeadlineNs - nowNs
          if (remainingNs > 250_000L) {
            LockSupport.parkNanos(remainingNs)
          } else if (nowNs - nextDeadlineNs > TARGET_FRAME_NS) {
            nextDeadlineNs = nowNs
          }
        }
      } catch (_: InterruptedException) {
        // Exit cleanly
      } finally {
        releaseEgl()
      }
    }

    fun onWindowResize(newWidth: Int, newHeight: Int) {
      synchronized(lock) {
        width = newWidth
        height = newHeight
        sizeChanged = true
        lock.notifyAll()
      }
    }

    fun onPause() {
      synchronized(lock) {
        paused = true
        lock.notifyAll()
      }
    }

    fun onResume() {
      synchronized(lock) {
        paused = false
        lock.notifyAll()
      }
    }

    fun requestExitAndWait() {
      synchronized(lock) {
        shouldExit = true
        lock.notifyAll()
      }
      LockSupport.unpark(this)
      try {
        join(500L)
      } catch (_: InterruptedException) {
        currentThread().interrupt()
      }
    }

    private fun initEgl(): Boolean {
      eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
      if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false

      val version = IntArray(2)
      if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return false

      val config = chooseEglConfig() ?: return false
      val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
      eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
      if (eglContext == EGL14.EGL_NO_CONTEXT) return false

      val surfAttribs = intArrayOf(EGL14.EGL_NONE)
      eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surfaceTexture, surfAttribs, 0)
      if (eglSurface == EGL14.EGL_NO_SURFACE) return false

      if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return false
      EGL14.eglSwapInterval(eglDisplay, 1)
      return true
    }

    private fun chooseEglConfig(): EGLConfig? {
      val configs = arrayOfNulls<EGLConfig>(1)
      val numConfigs = IntArray(1)

      val opaqueAttribs =
        intArrayOf(
          EGL14.EGL_RENDERABLE_TYPE,
          EGL14.EGL_OPENGL_ES2_BIT,
          EGL14.EGL_SURFACE_TYPE,
          EGL14.EGL_WINDOW_BIT,
          EGL14.EGL_RED_SIZE,
          8,
          EGL14.EGL_GREEN_SIZE,
          8,
          EGL14.EGL_BLUE_SIZE,
          8,
          EGL14.EGL_ALPHA_SIZE,
          0,
          EGL14.EGL_DEPTH_SIZE,
          16,
          EGL14.EGL_NONE,
        )
      if (EGL14.eglChooseConfig(eglDisplay, opaqueAttribs, 0, configs, 0, 1, numConfigs, 0) &&
        numConfigs[0] > 0 &&
        configs[0] != null
      ) {
        return configs[0]
      }

      val rgbaAttribs =
        intArrayOf(
          EGL14.EGL_RENDERABLE_TYPE,
          EGL14.EGL_OPENGL_ES2_BIT,
          EGL14.EGL_SURFACE_TYPE,
          EGL14.EGL_WINDOW_BIT,
          EGL14.EGL_RED_SIZE,
          8,
          EGL14.EGL_GREEN_SIZE,
          8,
          EGL14.EGL_BLUE_SIZE,
          8,
          EGL14.EGL_ALPHA_SIZE,
          8,
          EGL14.EGL_DEPTH_SIZE,
          16,
          EGL14.EGL_NONE,
        )
      if (EGL14.eglChooseConfig(eglDisplay, rgbaAttribs, 0, configs, 0, 1, numConfigs, 0) &&
        numConfigs[0] > 0
      ) {
        return configs[0]
      }
      return null
    }

    private fun releaseEgl() {
      if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
        EGL14.eglMakeCurrent(
          eglDisplay,
          EGL14.EGL_NO_SURFACE,
          EGL14.EGL_NO_SURFACE,
          EGL14.EGL_NO_CONTEXT,
        )
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
          EGL14.eglDestroySurface(eglDisplay, eglSurface)
        }
        if (eglContext != EGL14.EGL_NO_CONTEXT) {
          EGL14.eglDestroyContext(eglDisplay, eglContext)
        }
        EGL14.eglTerminate(eglDisplay)
      }
      eglSurface = EGL14.EGL_NO_SURFACE
      eglContext = EGL14.EGL_NO_CONTEXT
      eglDisplay = EGL14.EGL_NO_DISPLAY
    }
  }

  companion object {
    const val TARGET_FPS = 120
    const val TARGET_FRAME_NS = 1_000_000_000L / TARGET_FPS // 8_333_333 ns (120 FPS exact)
  }
}
