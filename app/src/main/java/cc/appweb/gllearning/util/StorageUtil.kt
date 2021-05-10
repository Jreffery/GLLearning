package cc.appweb.gllearning.util

import java.io.File

class StorageUtil {

    companion object {

        const val PATH_LEARNING = "learning"

        val PATH_LEARNING_PIC = "learning" + File.separator + "picture"

        val PATH_LEARNING_VOICE = "learning" + File.separator + "voice"


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

    }

}