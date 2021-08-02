//
// Created by 龚健飞 on 2021/7/6.
//

#include "BgRender.h"
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <EGL/eglplatform.h>
#include <GLES3/gl3.h>
#include <GLES3/gl3ext.h>
#include <GLES3/gl3platform.h>
#include "myutils.h"
#include <cstring>
#include <cstdio>

#define TAG "BgRender"

BgRender::BgRender(unsigned int width, unsigned int height, const char *imageData) {
    LOGD(TAG, "BgRender constructor width=%d height=%d", width, height);
    mWidth = width;
    mHeight = height;
    mEglDisplay = nullptr;
    mEglConfig = nullptr;
    mEglSurface = nullptr;
    mEglContext = nullptr;
    mFboTextureId = 0;
    mTextureId = 0;
    mFboId = 0;
    mFboProgramId = 0;
    mVertexShader = 0;
    mFragmentShader = 0;
    mVboIds = new GLuint[3];
    mVaoId = 0;
    mImageRawData = new GLbyte[width * height * 4];
    memcpy((void *) mImageRawData, imageData, width * height * 4);
//    FILE* file = fopen("/sdcard/test.raw", "w");
//    fwrite(mImageRawData, sizeof(char), mWidth * mHeight * 4, file);
//    fflush(file);
//    fclose(file);
    mDrawData = new GLbyte[width * height * 4];
}

BgRender::~BgRender() {
    LOGD(TAG, "BgRender un constructor");
}

// 创建 GLES 环境，一般步骤如下：
// 1. 获取 EGLDisplay 对象，建立与本地窗口系统的连接
//        调用 eglGetDisplay 方法得到 EGLDisplay。
// 2. 初始化 EGL 方法
//        打开连接之后，调用 eglInitialize 方法初始化。
// 3. 获取 EGLConfig 对象，确定渲染表面的配置信息
//        调用 eglChooseConfig 方法得到 EGLConfig。
// 4. 创建渲染表面 EGLSurface
//        通过 EGLDisplay 和 EGLConfig ，调用 eglCreateWindowSurface 或 eglCreatePbufferSurface 方法创建渲染表面，得到 EGLSurface，其中 eglCreateWindowSurface 用于创建屏幕上渲染区域，eglCreatePbufferSurface 用于创建屏幕外渲染区域。
// 5. **创建渲染上下文 EGLContext **
//        通过 EGLDisplay 和 EGLConfig ，调用 eglCreateContext 方法创建渲染上下文，得到 EGLContext。
// 6. 绑定上下文
//        通过 eglMakeCurrent 方法将 EGLSurface、EGLContext、EGLDisplay 三者绑定，绑定成功之后 OpenGLES 环境就创建好了，接下来便可以进行渲染。
// 7. 交换缓冲
//        OpenGLES 绘制结束后，使用 eglSwapBuffers 方法交换前后缓冲，将绘制内容显示到屏幕上，而屏幕外的渲染不需要调用此方法。
// 8. 释放 EGL 环境
//        绘制结束后，不再需要使用 EGL 时，需要取消 eglMakeCurrent 的绑定，销毁 EGLDisplay、EGLSurface、EGLContext 三个对象。
void BgRender::CreateGlesEnv() {
    LOGD(TAG, "CreateGlesEnv");
    // EGL config 属性
    const EGLint confAttr[] = {
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
            // EGL_WINDOW_BIT EGL_PBUFFER_BIT we will create a pixelbuffer surface
            EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,// if you need the alpha channel
            EGL_DEPTH_SIZE, 8,// if you need the depth buffer
            EGL_STENCIL_SIZE, 8,
            EGL_NONE
    };

    // EGL context 属性
    const EGLint ctxAttr[] = {
            EGL_CONTEXT_CLIENT_VERSION, 2, // 使用EGL 2.0
            EGL_NONE
    };

    // surface 属性
    // the surface size is set to the input frame size
    const EGLint surfaceAttr[] = {
            EGL_WIDTH, 1,
            EGL_HEIGHT, 1,
            EGL_NONE
    };

    EGLint eglMajVers, eglMinVers; // 版本信息
    EGLint numConfigs;

    // 1.获取EGLDisplay对象，建立与本地窗口系统的连接
    mEglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    LOGD(TAG, "mEglDisplay == EGL_NO_DISPLAY ? %d", (mEglDisplay == EGL_NO_DISPLAY));

    // 2. 初始化 EGL 方法
    EGLBoolean ret = eglInitialize(mEglDisplay, &eglMajVers, &eglMinVers);
    LOGD(TAG, "eglInitialize ret=%d, eglMajVers=%d, eglMinVers=%d", ret, eglMajVers, eglMinVers);

    // 3. 获取EGLConfig对象，确定渲染表面的配置信息
    ret = eglChooseConfig(mEglDisplay, confAttr, &mEglConfig, 1, &numConfigs);
    LOGD(TAG, "eglChooseConfig ret=%d, numConfigs=%d", ret, numConfigs);

    // 4. 创建渲染表面EGLSurface，使用eglCreatePbufferSurface创建屏幕外渲染区域
    mEglSurface = eglCreatePbufferSurface(mEglDisplay, mEglConfig, surfaceAttr);
    LOGD(TAG, "mEglSurface == EGL_NO_SURFACE ? %d eglError=%d", (mEglSurface == EGL_NO_SURFACE),
         eglGetError());

    // 5. 创建渲染上下文 EGLContext
    mEglContext = eglCreateContext(mEglDisplay, mEglConfig, EGL_NO_CONTEXT, ctxAttr);
    LOGD(TAG, "mEglContext == EGL_NO_CONTEXT ? %d eglError=%d", (mEglContext == EGL_NO_CONTEXT),
         eglGetError());

    // 6. 绑定上下文
    ret = eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext);
    LOGD(TAG, "eglMakeCurrent ret=%d, eglError=%d", ret, eglGetError());

    createFBO();
    initShader();
}

// 渲染
void BgRender::Draw() {
    LOGD(TAG, "Draw");
    glViewport(0, 0, mWidth, mHeight);

    // 以rgba来清空缓冲区当前的所有颜色
    glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
    // 以glClearColor设置的值来清除颜色缓冲以及深度缓冲
    // 清除颜色缓冲区
    glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);

    // 使用程序
    glUseProgram(mFboProgramId);
    // 绑定FBO
    glBindFramebuffer(GL_FRAMEBUFFER, mFboId);
    // 激活纹理单元
    glActiveTexture(GL_TEXTURE0);
//    // 绑定纹理ID
//    glBindTexture(GL_TEXTURE_2D, mFboTextureId);
//    // 上传纹理
//    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, mWidth, mHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE,
//                 mImageRawData);

    glBindTexture(GL_TEXTURE_2D, mTextureId);
    LOGD(TAG, "glBindTexture GL_TEXTURE_2D error=%d", glGetError());
    // 绑定VAO
    glBindVertexArray(mVaoId);
    // 绘制
    glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_SHORT, (const void *) 0);
    LOGD(TAG, "glDrawElements error=%d", glGetError());

    // 读取渲染好的数据
    glReadPixels(0, 0, mWidth, mHeight, GL_RGBA, GL_UNSIGNED_BYTE, mDrawData);

//    char *print = new char [mWidth * mHeight * 4];
//    memcpy(print, mDrawData, mWidth * mHeight * 4);
//    print[mWidth * mHeight * 4 -1] = '\0';
//    LOGD(TAG, "glReadPixels str=%s", print);

    // 解绑
    glBindVertexArray(GL_NONE);
    glBindTexture(GL_TEXTURE_2D, GL_NONE);
}

// 释放GLES环境
void BgRender::DestroyGlesEnv() {
    LOGD(TAG, "DestroyGlesEnv");
    // 8. 释放EGL环境
    if (mEglDisplay != EGL_NO_DISPLAY) {
        // 解绑上下文
        eglMakeCurrent(mEglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroyContext(mEglDisplay, mEglContext);
        eglDestroySurface(mEglDisplay, mEglSurface);
        eglReleaseThread();
        eglTerminate(mEglDisplay);
    }

    mEglDisplay = nullptr;
    mEglConfig = nullptr;
    mEglSurface = nullptr;
    mEglContext = nullptr;

    LOGD(TAG, "mFboTextureId=%d mFboId=%d mFboProgramId=%d mVertexShader=%d mFragmentShader=%d mTextureId=%d mVaoId=%d",
         mFboTextureId, mFboId, mFboProgramId, mVertexShader, mFragmentShader, mTextureId, mVaoId);
    if (mFboTextureId != 0) {
        glDeleteTextures(1, &mFboTextureId);
    }
    mFboTextureId = 0;
    if (mFboId != 0) {
        glDeleteFramebuffers(1, & mFboId);
    }
    mFboId = 0;
    if (mFboProgramId != 0) {
        glDeleteProgram(mFboProgramId);
    }
    mFboProgramId = 0;
    if (mTextureId != 0) {
        glDeleteTextures(1, &mTextureId);
    }
    mTextureId = 0;
    if (mVboIds != nullptr) {
        glDeleteBuffers(3, mVboIds);
    }
    delete mVboIds;
    mVboIds = nullptr;
    if (mVaoId != 0) {
        glDeleteVertexArrays(1, &mVaoId);
    }
    mVaoId = 0;
    if (mVertexShader != 0) {
        glDeleteShader(mVertexShader);
    }
    mVertexShader = 0;
    if (mFragmentShader != 0) {
        glDeleteShader(mFragmentShader);
    }
    mFragmentShader = 0;
    LOGD(TAG, "glDelete error=%d", glGetError());
    delete mImageRawData;
    mImageRawData = nullptr;
    delete mDrawData;
    mDrawData = nullptr;

}

void BgRender::createFBO() {
    LOGD(TAG, "createFBO");
    // 创建一个2D纹理用于连接FBO的颜色附着
    glGenTextures(1, &mFboTextureId);
    // 设置该纹理ID为2D纹理
    glBindTexture(GL_TEXTURE_2D, mFboTextureId);
    // 设置该纹理的填充属性
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glBindTexture(GL_TEXTURE_2D, GL_NONE);
    LOGD(TAG, "GenTexture error=%d", glGetError());

    // 创建FBO
    glGenFramebuffers(1, &mFboId);
    // 绑定FBO
    glBindFramebuffer(GL_FRAMEBUFFER, mFboId);
    // 绑定FBO纹理
    glBindTexture(GL_TEXTURE_2D, mFboTextureId);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, mWidth, mHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                 nullptr);
    // 将纹理连接到FBO附着，颜色附着
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mFboTextureId, 0);
    LOGD(TAG, "Gen FBO error=%d", glGetError());
    // 检查FBO完整性状态
    LOGD(TAG, "glCheckFramebufferStatus=%d", glCheckFramebufferStatus(GL_FRAMEBUFFER));
    // 解绑纹理
    glBindTexture(GL_TEXTURE_2D, GL_NONE);
    // 解绑FBO
    glBindFramebuffer(GL_FRAMEBUFFER, GL_NONE);

    // 创建一个2D纹理用于渲染
    glGenTextures(1, &mTextureId);
    // 设置该纹理ID为2D纹理
    glBindTexture(GL_TEXTURE_2D, mTextureId);
    // 设置该纹理的填充属性
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    LOGD(TAG, "GenTexture mTextureId error=%d", glGetError());
    // 分配内存大小，上传纹理数据
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, mWidth, mHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                 mImageRawData);
    glBindTexture(GL_TEXTURE_2D, GL_NONE);
    LOGD(TAG, "glTexImage2D error=%d", glGetError());
}

void BgRender::initShader() {
    LOGD(TAG, "initShader");
    // 顶点坐标，OpenGL坐标系，三维，原点在中间
    GLfloat vVertices[] = {
            -1.0f, -1.0f, 0.0f, // 左下 （屏幕左上角）
            1.0f, -1.0f, 0.0f, // 右下 （屏幕右上角）
            -1.0f, 1.0f, 0.0f, // 左上 （屏幕左下角）
            1.0f, 1.0f, 0.0f, // 右上 （屏幕右下角）
    };

    //fbo 纹理坐标与正常纹理方向不同，原点位于左下角
    GLfloat vFboTexCoors[] = {
            0.0f, 0.0f,  // 左下
            1.0f, 0.0f, // 右下
            0.0f, 1.0f,  // 左上
            1.0f, 1.0f, // 右上
    };

    // 绘制顺序
    GLushort indices[] = {0, 1, 2, 1, 2, 3};

    // GLSL语言基础 https://my.oschina.net/sweetdark/blog/208024
    // 顶点着色器 shader
    const char *vShaderStr[1] = {
            "#version 300 es                            \n"  // 声明使用OpenGLES 3.0
            "layout(location = 0) in vec4 a_position;   \n"  // 声明输入四维向量
            "layout(location = 1) in vec2 a_texCoord;   \n"  // 声明输入二维向量
            "out vec2 v_texCoord;                       \n"  // 声明输出二维向量，纹理坐标
            "void main()                                \n"
            "{                                          \n"
            "   gl_Position = a_position;               \n"  // 内建变量赋值，不需要变换，gl_Position描述三维空间里变换后的位置
            "   v_texCoord = a_texCoord;                \n"  // 输出向量赋值，纹理的坐标
            "}                                          \n"
    };

    // 用于FBO渲染的片段着色器shader，取每个像素的灰度值
    const char *fFboShaderStr[1] = {
            "#version 300 es                            \n"
            "precision mediump float;                   \n"  // 设置默认的精度限定符
            "in vec2 v_texCoord;                        \n"  // 导入纹理坐标，描述片段
            "layout(location = 0) out vec4 outColor;    \n"  // 提供片段着色器输出变量的声明，这将是传递到下一阶段的颜色
            "uniform sampler2D s_TextureMap;            \n"  // 声明GL_TEXTURE_2D绑定的空间变量，取出纹理数据
            "void main()                                \n"
            "{                                          \n"
            "    vec4 tempColor = texture(s_TextureMap, v_texCoord);   \n" // 通过纹理和纹理坐标采样颜色值
            "    float luminance = tempColor.r * 0.299 + tempColor.g * 0.587 + tempColor.b * 0.114;  \n"
            "    outColor = vec4(vec3(luminance), 1.0);         \n"
            "}"
    };

    // 创建一个顶点shader
    mVertexShader = glCreateShader(GL_VERTEX_SHADER);
    // 替换着色器对象的源代码
    GLint sourceLen = strlen(vShaderStr[0]);
    glShaderSource(mVertexShader, 1, vShaderStr, &sourceLen);
    // 编译shader
    glCompileShader(mVertexShader);

    // 创建一个片元shader
    mFragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
    // 替换着色器对象的源代码
    sourceLen = strlen(fFboShaderStr[0]);
    glShaderSource(mFragmentShader, 1, fFboShaderStr, &sourceLen);
    // 编译shader
    glCompileShader(mFragmentShader);

    // 创建程序
    mFboProgramId = glCreateProgram();
    // 绑定shader
    glAttachShader(mFboProgramId, mVertexShader);
    glAttachShader(mFboProgramId, mFragmentShader);
    // 链接程序
    glLinkProgram(mFboProgramId);
    LOGD(TAG, "glLinkProgram error=%d", glGetError());

    // 生成 VBO ，加载顶点数据和索引数据
    glGenBuffers(3, mVboIds);
    // 载入vVertices
    glBindBuffer(GL_ARRAY_BUFFER, mVboIds[0]);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vVertices), vVertices, GL_STATIC_DRAW);

    // 载入vFboTexCoors
    glBindBuffer(GL_ARRAY_BUFFER, mVboIds[1]);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vFboTexCoors), vFboTexCoors, GL_STATIC_DRAW);

    // 载入indices
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mVboIds[2]);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(indices), indices, GL_STATIC_DRAW);
    LOGD(TAG, "Gen VBO error=%d", glGetError());

    // 生成VAO，用于离屏渲染
    glGenVertexArrays(1, &mVaoId);
    // 在绑定 VAO 之后，操作 VBO ，当前 VAO 会记录 VBO 的操作
    glBindVertexArray(mVaoId);
    // 以下VBO操作会被VAO记录
    glBindBuffer(GL_ARRAY_BUFFER, mVboIds[0]);
    // 顶点坐标
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 3 * sizeof(GLfloat), nullptr);

    // 纹理坐标
    glBindBuffer(GL_ARRAY_BUFFER, mVboIds[1]);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(GLfloat), nullptr);

    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, mVboIds[2]);
    // 解绑VAO
    glBindVertexArray(GL_NONE);
    LOGD(TAG, "Gen VAO error=%d", glGetError());
}

void BgRender::GetData(const signed char *addr) {
    LOGD(TAG, "GetData");
    memcpy((void *) addr, mDrawData, mWidth * mHeight * 4);
}

void BgRender::SetRotate(int type) {
    LOGD(TAG, "SetRotate type=%d", type);
    GLfloat *replaceFboVertex;
    switch (type) {
        case ROTATE_0:
            replaceFboVertex = new GLfloat[8]{
                    0.0f, 0.0f,  // 左下
                    1.0f, 0.0f, // 右下
                    0.0f, 1.0f,  // 左上
                    1.0f, 1.0f, // 右上
            };
            break;
        case ROTATE_90:
            replaceFboVertex = new GLfloat[8]{
                    0.0f, 1.0f,
                    0.0f, 0.0f,
                    1.0f, 1.0f,
                    1.0f, 0.0f,
            };
            break;
        case ROTATE_180:
            replaceFboVertex = new GLfloat[8]{
                    1.0f, 1.0f,
                    0.0f, 1.0f,
                    1.0f, 0.0f,
                    0.0f, 0.0f,
            };
            break;
        case ROTATE_270:
            replaceFboVertex = new GLfloat[8]{
                    1.0f, 0.0f,
                    1.0f, 1.0f,
                    0.0f, 0.0f,
                    0.0f, 1.0f,
            };
            break;
        default:
            return;
    }
    // 重新载入
    glBindBuffer(GL_ARRAY_BUFFER, mVboIds[1]);
    glBufferData(GL_ARRAY_BUFFER, sizeof(replaceFboVertex[0]) * 8, replaceFboVertex, GL_STATIC_DRAW);

    delete replaceFboVertex;
}

void BgRender::SetMirrorType(int type) {
    LOGD(TAG, "SetMirrorType type=%d", type);
    GLfloat *replaceFboVertex;
    switch (type) {
        case MIRROR_HORIZONTAL:
            replaceFboVertex = new GLfloat[8]{
                    1.0f, 0.0f, // 右下
                    0.0f, 0.0f,  // 左下
                    1.0f, 1.0f, // 右上
                    0.0f, 1.0f,  // 左上
            };
            break;
        case MIRROR_VERTICAL:
            replaceFboVertex = new GLfloat[8]{
                    0.0f, 1.0f,
                    1.0f, 1.0f,
                    0.0f, 0.0f,
                    1.0f, 0.0f,
            };
            break;
        case MIRROR_NONE:
        default:
            replaceFboVertex = new GLfloat[8]{
                    0.0f, 0.0f,  // 左下
                    1.0f, 0.0f, // 右下
                    0.0f, 1.0f,  // 左上
                    1.0f, 1.0f, // 右上
            };
    }
    // 重新载入
    glBindBuffer(GL_ARRAY_BUFFER, mVboIds[1]);
    glBufferData(GL_ARRAY_BUFFER, sizeof(replaceFboVertex[0]) * 8, replaceFboVertex, GL_STATIC_DRAW);

    delete replaceFboVertex;
}