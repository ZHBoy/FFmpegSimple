package com.zhhh.binder.ui

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.zhhh.binder.R
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


/**
 * Client端的实例获取和绑定
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private var btStart: Button? = null

    //当前选择的视频文件
    private var currentUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btStart = findViewById(R.id.btStart)
        btStart?.setOnClickListener {
            if (currentUri == null) return@setOnClickListener

            val file = copyFileFromUri(this, currentUri!!)
            if (file != null) {
                val filePath = file.absolutePath
                Log.d(TAG, "inputVidePath->$filePath")

                //内部存储临时存储视频帧文件
                val outputFile = File(this@MainActivity.cacheDir, "output.jpg")
                Log.d(TAG, "outputFile->${outputFile.absolutePath}")

                val session =
                    FFmpegKit.execute("-i $filePath -ss 00:00:08 -vframes 1 ${outputFile.absolutePath}")
                if (ReturnCode.isSuccess(session.returnCode)) {
                    // SUCCESS
                    Log.d(TAG, "提取视频帧成功->$filePath")
                    saveToGallery(
                        this@MainActivity,
                        outputFile.absolutePath
                    )
                    //删除内部存储提取的视频帧和临时存储的视频文件
                    file.delete()
                    outputFile.delete()
                } else if (ReturnCode.isCancel(session.returnCode)) {
                    // CANCEL
                } else {
                    // FAILURE
                    Log.d(TAG, String.format(
                            "Command failed with state %s and rc %s.%s",
                            session.state,
                            session.returnCode,
                            session.failStackTrace
                        )
                    )
                }
            }

        }

        findViewById<Button>(R.id.btSelect).setOnClickListener {
            openFilePicker()
        }

    }

    private val REQUEST_CODE_OPEN_DOCUMENT = 1

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*" // 选择视频文件
        }
        startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // 处理选中的文件
                startVideo(uri)
                currentUri = uri
            }
        }
    }

    private fun startVideo(uri: Uri) {
        val videoView: VideoView = findViewById(R.id.videoView)

        // 设置视频路径
        videoView.setVideoURI(uri)
        // 添加媒体控制器（播放/暂停/进度条）
        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)
        // 开始播放
        videoView.start()
    }

    /**
     * 10以上的版本 需要放到内部存储
     */
    private fun copyFileFromUri(context: Context, uri: Uri): File? {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        if (inputStream != null) {
            val file = File(context.cacheDir, "temp_file")
            val outputStream = FileOutputStream(file)
            try {
                val buffer = ByteArray(4 * 1024) // 4KB buffer
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
                return file
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                inputStream.close()
                outputStream.close()
            }
        }
        return null
    }

    /**
     * 保存图片到相册
     */
    private fun saveToGallery(context: Context, inputFilePath: String): Uri? {
        val contentResolver: ContentResolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "output.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
            }
        }
        val uri =
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                val inputStream = FileInputStream(inputFilePath)
                val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                    inputStream.close()
                    outputStream.close()
                    return uri
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return null
    }
}