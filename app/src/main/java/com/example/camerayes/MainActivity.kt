package com.example.camerayes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.app.ActivityCompat
import com.example.camerayes.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding


    private val mCameraId = "0" // gu 0: back 1: front camera
    lateinit var mPreviewSize: Size
    private val PREVIEW_WIDTH = 1920
    private val PREVIEW_HEIGHT = 1080
    private var mCameraDevice: CameraDevice? = null
    lateinit var mHandler: Handler
    lateinit var mCaptureSession: CameraCaptureSession
    lateinit var mPreviewBuilder: CaptureRequest.Builder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.ture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                startBackgroundThread()
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {

            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {

            }

        }
    }

    private fun startBackgroundThread() {
        val mHandlerThread = HandlerThread("fuck")
        mHandlerThread.start()
        mHandler = Handler(mHandlerThread.looper)
    }


    private val mCameraDeviceStateCallback: CameraDevice.StateCallback =
        object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                try {
                    mCameraDevice = camera
                    startPreview(camera)
                } catch (e: CameraAccessException) {
                    e.printStackTrace()
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                mCameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                mCameraDevice = null
            }

            override fun onClosed(camera: CameraDevice) {
                camera.close()
                mCameraDevice = null
            }
        }

    private fun openCamera() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            mPreviewSize = Size(PREVIEW_WIDTH, PREVIEW_HEIGHT)
            cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }


    private val mSessionStateCallback: CameraCaptureSession.StateCallback =
        object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                mCaptureSession = session
                updatePreview()

            }

            override fun onConfigureFailed(session: CameraCaptureSession) {}
        }

    private fun startPreview(camera: CameraDevice) {
        val texture = binding.ture.surfaceTexture
        if (texture == null) {
            Log.e("fuckfuck", "fuck")
        }
        texture!!.setDefaultBufferSize(mPreviewSize.width, mPreviewSize.height)
        val surface = Surface(texture)

        mPreviewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)


        val mImageReader = ImageReader.newInstance(
            mPreviewSize.width,
            mPreviewSize.height,
            ImageFormat.YUV_420_888,
            2
        )
        mPreviewBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0)
        mPreviewBuilder.addTarget(surface)
        mPreviewBuilder.addTarget(mImageReader.surface)

        camera.createCaptureSession(
            Arrays.asList(surface, mImageReader.surface),
            mSessionStateCallback,
            mHandler
        )
    }


    private fun updatePreview() {
        mHandler.post(Runnable {
            try {
                mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null, mHandler)
            } catch (e: CameraAccessException) {
                e.printStackTrace()
            }
        })
    }

}