package cc.appweb.gllearning.util

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

class StorageUtil {

    companion object {

        // 总目录
        private const val PATH_LEARNING = "learning"

        // 拍照后保存图片的目录
        val PATH_LEARNING_PIC = PATH_LEARNING + File.separator + "picture"

        // 录音后保存pcm文件的目录
        val PATH_LEARNING_VOICE = PATH_LEARNING + File.separator + "voice"

        // 视频录制后保存的目录
        val PATH_LEARNING_MP4 = PATH_LEARNING + File.separator + "mp4"

        // pcm编码成aac文件保存的目录
        val PATH_LEARNING_AAC = PATH_LEARNING + File.separator + "aac"

        // 音频封装文件
        val PATH_LEARNING_M4A = PATH_LEARNING + File.separator + "m4a"

        val PATH_LEARNING_RAW = PATH_LEARNING + File.separator + "raw"

        fun getFile(relativePath: String): File {
            val path = relativePath.split(File.separator)
            var rootPath = AppUtil.getContext().getExternalFilesDir(null)!!.absolutePath
            for (i in 0 until path.size -1) {
                rootPath += File.separator + path[i]
            }
            val subPath = File(rootPath)
            if (!subPath.exists() || !subPath.isDirectory) {
                subPath.mkdirs()
            }

            return File(rootPath + File.separator + path[path.size -1])
        }

        fun writeBufferIntoFile(fileName: String, buffer: ByteBuffer) {
            val randomAccessFile = RandomAccessFile(fileName, "rw")
            val fileChannel = randomAccessFile.channel
            fileChannel.write(buffer, 0)
            fileChannel.close()
        }

    }

}