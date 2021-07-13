//
// Created by 龚健飞 on 2021/7/6.
//

#ifndef GLLEARNING_BGRENDER_H
#define GLLEARNING_BGRENDER_H

#include <EGL/egl.h>
#include <GLES3/gl3.h>

#define ROTATE_0 0
#define ROTATE_90 1
#define ROTATE_180 2
#define ROTATE_270 3

#define MIRROR_NONE 0
#define MIRROR_HORIZONTAL 1
#define MIRROR_VERTICAL 2

/**
 * 背景渲染器
 * */
class BgRender {

private:
    // Display 对象
    EGLDisplay mEglDisplay;
    // config信息
    EGLConfig mEglConfig;
    // 渲染绘制表面
    EGLSurface mEglSurface;
    // 渲染上下文
    EGLContext mEglContext;

    // 背景的宽高
    unsigned int mWidth;
    unsigned int mHeight;

    // 用于连接FBO的纹理id
    GLuint mFboTextureId;
    // FBO id
    GLuint mFboId;
    // 创建并初始化FBO，Frame Buffer Object帧缓冲对象，用作离屏渲染
    void createFBO();
    // 初始化OpenGL shader
    void initShader();
    // 纹理贴图
    GLuint mTextureId;

    // FBO渲染程序id
    GLuint mFboProgramId;
    // VBO IDs Vertex Buffer Object 顶点缓冲区对象
    GLuint* mVboId;
    // VAO ID Vertex Array Object 顶点数组对象
    // VAO 的主要作用是用于管理 VBO 或 EBO，减少 glBindBuffer 、glEnableVertexAttribArray、 glVertexAttribPointer 这些调用操作
    // 高效地实现在顶点数组配置之间切换。
    GLuint mVaoId;

    // 原图数据
    const GLbyte *mImageRawData;

    // 绘制后的数据
    GLbyte *mDrawData;

public:

    // 构造函数
    BgRender(unsigned int width, unsigned int height, const char *imageData);

    // 析构函数
    ~BgRender();

    // 创建OpenGL ES运行环境
    void CreateGlesEnv();

    // 获取绘制后的数据
    void GetData(const signed char *addr);

    // 开始渲染
    void Draw();

    // 设置旋转角度
    void SetRotate(int type);

    void SetMirrorType(int type);

    // 销毁OpenGL ES运行环境
    void DestroyGlesEnv();

};


#endif //GLLEARNING_BGRENDER_H
