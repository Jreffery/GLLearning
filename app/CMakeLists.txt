# cmake的版本
cmake_minimum_required(VERSION 3.4.1)

# 声明头文件搜索路径
include_directories(src/main/cpp)

# 声明要生成的库的名称--voice
add_library(
        # 库名称
        voice
        # 库的类型，动态库
        SHARED
        # 代码源文件
        src/main/cpp/voice/VoiceJniLoad.cpp
        src/main/cpp/voice/VoicePlayer.cpp
        src/main/cpp/voice/VoiceRecorder.cpp
)
# 声明需要链接的库，起别名
find_library(
        # 别名
        log-lib
        # 真实库名称
        log
)
find_library(
        sl
        OpenSLES
)
#find_library(
#        stdc
#        c++_shared
#)

add_library(
        glrender
        SHARED
        src/main/cpp/render/BgRender.cpp
        src/main/cpp/render/glrenderJniLoad.cpp
)
find_library(
        gl
        GLESv3
)
find_library(
        egl
        EGL
)

# 链接操作
target_link_libraries(
        # 需要链接的库
        voice
        # 被链接的库
        ${log-lib}
        ${sl}
)

target_link_libraries(
        glrender
        # 被链接的库
        ${log-lib}
        ${gl}
        ${egl}
)