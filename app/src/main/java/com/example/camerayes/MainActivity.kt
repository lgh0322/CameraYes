package com.example.camerayes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
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
    var wantImg = true
    private var mHandlerThread: HandlerThread? = null
    lateinit var mImageReader: ImageReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PathUtil.initVar(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)



        startBackgroundThread()
        openCamera()

        binding.button.setOnClickListener {
            wantImg=true
        }

    }

    private fun startBackgroundThread() {
        mHandlerThread = HandlerThread("fuck")
        mHandlerThread!!.start()
        mHandler = Handler(mHandlerThread!!.looper)
    }


    private val mCameraDeviceStateCallback: CameraDevice.StateCallback =
        object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                mCameraDevice = camera
                startPreview(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
            }

            override fun onClosed(camera: CameraDevice) {
                camera.close()
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
        mPreviewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        mImageReader = ImageReader.newInstance(
            mPreviewSize.width,
            mPreviewSize.height,
            ImageFormat.JPEG,
            2 /*最大的图片数，mImageReader里能获取到图片数，但是实际中是2+1张图片，就是多一张*/
        )


        mPreviewBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0)
        mPreviewBuilder.addTarget(mImageReader.surface)
        mImageReader.setOnImageAvailableListener(
            { reader ->
                mHandler.post(ImageSaver(reader))
            }, mHandler
        )


        camera.createCaptureSession(
            Arrays.asList(mImageReader.surface),
            mSessionStateCallback,
            mHandler
        )
    }


    private inner class ImageSaver(var reader: ImageReader) : Runnable {
        override fun run() {
            val image = reader.acquireLatestImage() ?: return
            val width = image.width
            val height = image.height
            if(wantImg){
                wantImg=false
                val buffer: ByteBuffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val fuck=System.currentTimeMillis().toString()+".jpg"
                File(PathUtil.getPathX(fuck)).writeBytes(bytes)

            }

            image.close()
        }
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

    override fun onPause() {
        super.onPause()
        closeCamera()
    }

    private fun closeCamera() {
        mCaptureSession.stopRepeating()
        mCaptureSession.close()
        if (mCameraDevice != null) { //注意关闭顺序，先
            mCameraDevice!!.close()
            mCameraDevice = null
        }
        mImageReader.close()
        stopBackgroundThread()
    }

    private fun stopBackgroundThread() {
        try {
            if (mHandlerThread != null) {
                mHandlerThread!!.quitSafely()
                mHandlerThread!!.join()
                mHandlerThread = null
            }
            mHandler.removeCallbacksAndMessages(null)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

}