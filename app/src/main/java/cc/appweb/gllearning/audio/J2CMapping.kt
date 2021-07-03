package cc.appweb.gllearning.audio

// 声道类型
const val CHANNEL_OUT_MONO = 1    // 左声道，单声道时默认
const val CHANNEL_OUT_STEREO = 2  // 立体声，双声道

// 编码位数
const val ENCODING_8BIT = 1  // 每个采样点占8位
const val ENCODING_16BIT = 2  // 每个采样点占16位

// 大小端
const val ENDIANNESS_BIG = 1 // 大端
const val ENDIANNESS_LETTER = 2 // 小端