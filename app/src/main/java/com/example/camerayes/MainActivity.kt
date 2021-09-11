package com.example.camerayes

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.Image
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
import androidx.lifecycle.MutableLiveData
import com.example.camerayes.databinding.ActivityMainBinding
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.*
import java.io.ByteArrayOutputStream


class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    private var mSensorOrientation = 0
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

    val ff=MutableLiveData<Image>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PathUtil.initVar(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.fuck.surfaceTextureListener=object:TextureView.SurfaceTextureListener{
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

        binding.button.setOnClickListener {
            wantImg = true

        }



    }


    override fun onResume() {
        super.onResume()

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
                Log.e("fuckCamera","a1")
                camera.close()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e("fuckCamera","a2")
                camera.close()
            }

            override fun onClosed(camera: CameraDevice) {
                Log.e("fuckCamera","a3")
                camera.close()
            }
        }

    private fun openCamera() {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            mPreviewSize = Size(PREVIEW_WIDTH, PREVIEW_HEIGHT)

            val characteristics = cameraManager.getCameraCharacteristics(mCameraId)
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
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
            ImageFormat.YUV_420_888,
            2 /*最大的图片数，mImageReader里能获取到图片数，但是实际中是2+1张图片，就是多一张*/
        )
        val texture = binding.fuck.surfaceTexture

//      这里设置的就是预览大小
        texture!!.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
        val surface = Surface(texture)

        mPreviewBuilder.set(CaptureRequest.JPEG_ORIENTATION,mSensorOrientation)
        mPreviewBuilder.addTarget(mImageReader.surface)
        mPreviewBuilder.addTarget(surface)
        mImageReader.setOnImageAvailableListener(
            { reader ->
                mHandler.post(ImageSaver(reader))
            }, mHandler
        )


        camera.createCaptureSession(
            Arrays.asList(mImageReader.surface,surface),
            mSessionStateCallback,
            mHandler
        )
    }


    private fun YUV_420_888toNV21(image: Image): ByteArray {
        val nv21: ByteArray
        val yBuffer = image.planes[0].buffer
        val vuBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()
        nv21 = ByteArray(ySize + vuSize)
        yBuffer[nv21, 0, ySize]
        vuBuffer[nv21, ySize, vuSize]
        return nv21
    }
    private fun NV21toJPEG(nv21: ByteArray, width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        yuv.compressToJpeg(Rect(0, 0, width, height), 60, out)
        return out.toByteArray()
    }

    private inner class ImageSaver(var reader: ImageReader) : Runnable {
        override fun run() {
            val image = reader.acquireLatestImage() ?: return

            if(wantImg){
                wantImg=false
                val   data = NV21toJPEG(
                    YUV_420_888toNV21(image),
                    image.getWidth(), image.getHeight());
                File(PathUtil.getPathX(System.currentTimeMillis().toString()+".jpg")).writeBytes(data)
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
        closeCamera()
        super.onPause()

    }

    private fun closeCamera() {
//        mCaptureSession.stopRepeating()
        mCaptureSession.close()
        mCameraDevice!!.close()
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