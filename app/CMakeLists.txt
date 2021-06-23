# cmake的版本
cmake_minimum_required(VERSION 3.4.1)

# 声明头文件搜索路径
include_directories(src/main/cpp)

# 声明要生成的库的名称
add_library(
        # 库名称
        voice
        # 库的类型，动态库
        SHARED
        # 代码源文件
        src/main/cpp/JniLoad.cpp
        src/main/cpp/VoicePlayer.cpp
        src/main/cpp/VoiceRecorder.cpp
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
find_library(
        stdc
        c++_shared
)

# 链接操作
target_link_libraries(
        # 需要链接的库
        voice
        # 被链接的库
        ${log-lib}
        ${sl}
        ${stdc}
)