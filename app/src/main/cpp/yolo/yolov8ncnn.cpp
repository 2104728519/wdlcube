// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>
#include <mutex>
#include <cmath>

#include <platform.h>
#include <benchmark.h>

#include "yolov8.h"

#include "ndkcamera.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#if __ARM_NEON
#include <arm_neon.h>
#endif // __ARM_NEON

// ==========================================
// 1. 全局共享变量：用于跨线程安全传递提取的颜色
// ==========================================
static std::mutex g_color_mutex;
static int g_cube_colors[9] = {0};         // 存放 9 个格子的 ARGB 颜色
static bool g_is_cube_detected = false;    // 当前帧是否成功提取颜色

// ==========================================
// 2. 关键点排序：确保顺序永远是 左上(TL), 右上(TR), 右下(BR), 左下(BL)
// ==========================================
static void sort_cube_points(const std::vector<cv::Point2f>& pts, cv::Point2f* ordered_pts) {
    if (pts.size() != 4) return;
    float sum[4], diff[4];
    for(int i = 0; i < 4; i++) {
        sum[i] = pts[i].x + pts[i].y;
        diff[i] = pts[i].x - pts[i].y;
    }

    int tl = 0, br = 0, tr = 0, bl = 0;
    for(int i = 1; i < 4; i++) {
        if(sum[i] < sum[tl]) tl = i; // x+y 最小是左上
        if(sum[i] > sum[br]) br = i; // x+y 最大是右下
        if(diff[i] > diff[tr]) tr = i; // x-y 最大是右上
        if(diff[i] < diff[bl]) bl = i; // x-y 最小是左下
    }
    ordered_pts[0] = pts[tl];
    ordered_pts[1] = pts[tr];
    ordered_pts[2] = pts[br];
    ordered_pts[3] = pts[bl];
}

// ==========================================
// 3. 透视校正与 9 宫格核均值提色算法
// ==========================================
static void extract_cube_colors(const cv::Mat& rgb, const std::vector<Object>& objects) {
    std::lock_guard<std::mutex> lock(g_color_mutex);
    g_is_cube_detected = false;

    if (objects.empty()) return;

    // 寻找第一个有 4 个合格顶点（置信度>0.3）的魔方对象
    const Object* best_obj = nullptr;
    for (const auto& obj : objects) {
        if (obj.keypoints.size() == 4) {
            bool pts_valid = true;
            for (int i = 0; i < 4; i++) {
                if (obj.keypoints[i].prob < 0.3f) {
                    pts_valid = false;
                    break;
                }
            }
            if (pts_valid) {
                best_obj = &obj;
                break;
            }
        }
    }

    if (!best_obj) return;

    std::vector<cv::Point2f> pts(4);
    for (int i = 0; i < 4; i++) pts[i] = best_obj->keypoints[i].p;

    cv::Point2f ordered_pts[4];
    sort_cube_points(pts, ordered_pts);

    // 映射目标为一个 180x180 的正方形
    cv::Point2f dst_pts[4] = {
        cv::Point2f(0, 0),
        cv::Point2f(180, 0),
        cv::Point2f(180, 180),
        cv::Point2f(0, 180)
    };

    // 执行透视变换
    cv::Mat M = cv::getPerspectiveTransform(ordered_pts, dst_pts);
    cv::Mat warped;
    cv::warpPerspective(rgb, warped, M, cv::Size(180, 180));

    // 划分 3x3 网格，每个网格 60x60，取中心 20x20 区域算平均色以防边缘干扰
    int cell_size = 60;
    int roi_size = 20;
    int offset = (cell_size - roi_size) / 2;

    for (int r = 0; r < 3; r++) {
        for (int c = 0; c < 3; c++) {
            cv::Rect roi(c * cell_size + offset, r * cell_size + offset, roi_size, roi_size);
            cv::Mat cell = warped(roi);
            cv::Scalar mean_color = cv::mean(cell); // rgb 是 CV_8UC3 (RGB格式)

            int r_val = (int)mean_color[0];
            int g_val = (int)mean_color[1];
            int b_val = (int)mean_color[2];

            // 组装成 ARGB 格式的 32 位整型 (Alpha = 0xFF)
            int argb = (0xFF << 24) | (r_val << 16) | (g_val << 8) | b_val;
            g_cube_colors[r * 3 + c] = argb;
        }
    }

    g_is_cube_detected = true;
}

static int draw_unsupported(cv::Mat& rgb)
{
    const char text[] = "unsupported";

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 1.0, 1, &baseLine);

    int y = (rgb.rows - label_size.height) / 2;
    int x = (rgb.cols - label_size.width) / 2;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 1.0, cv::Scalar(0, 0, 0));

    return 0;
}

static int draw_fps(cv::Mat& rgb)
{
    // resolve moving average
    float avg_fps = 0.f;
    {
        static double t0 = 0.f;
        static float fps_history[10] = {0.f};

        double t1 = ncnn::get_current_time();
        if (t0 == 0.f)
        {
            t0 = t1;
            return 0;
        }

        float fps = 1000.f / (t1 - t0);
        t0 = t1;

        for (int i = 9; i >= 1; i--)
        {
            fps_history[i] = fps_history[i - 1];
        }
        fps_history[0] = fps;

        if (fps_history[9] == 0.f)
        {
            return 0;
        }

        for (int i = 0; i < 10; i++)
        {
            avg_fps += fps_history[i];
        }
        avg_fps /= 10.f;
    }

    char text[32];
    sprintf(text, "FPS=%.2f", avg_fps);

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);

    int y = 0;
    int x = rgb.cols - label_size.width;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(0, 0, 0));

    return 0;
}

static YOLOv8* g_yolov8 = 0;
static ncnn::Mutex lock;

static std::vector<Object> g_last_objects;
static double g_last_inference_time = 0.0;
static int g_fps_cap = 15;

// 【新增】：全局置信度阈值变量，默认 0.50
float g_prob_threshold = 0.50f;

class MyNdkCamera : public NdkCameraWindow
{
public:
    virtual void on_image_render(cv::Mat& rgb) const;
};

void MyNdkCamera::on_image_render(cv::Mat& rgb) const
{
    // yolov8
    {
        ncnn::MutexLockGuard g(lock);

        if (g_yolov8)
        {
            // 【修改】：增加时间戳截流逻辑
            double current_time = ncnn::get_current_time();
            // 计算当前限制下的帧间隔（毫秒）
            double interval = 1000.0 / g_fps_cap;

            // 只有距离上次推理时间超过了间隔，才进行新的推理
            if (current_time - g_last_inference_time >= interval) {
                g_yolov8->detect(rgb, g_last_objects); // 结果存入全局缓存
                g_last_inference_time = current_time;
            }

            // 【执行透视校正与颜色提取】
            extract_cube_colors(rgb, g_last_objects);

            // 画框（如果是跳过的帧，就用上一次缓存的框）
            g_yolov8->draw(rgb, g_last_objects);
        }
        else
        {
            draw_unsupported(rgb);
        }
    }

    draw_fps(rgb); // 这里的 FPS 显示的将是真实的 UI 渲染流畅度
}

static MyNdkCamera* g_camera = 0;

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnLoad");

    g_camera = new MyNdkCamera;

    ncnn::create_gpu_instance();

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnUnload");

    {
        ncnn::MutexLockGuard g(lock);

        delete g_yolov8;
        g_yolov8 = 0;
    }

    ncnn::destroy_gpu_instance();

    delete g_camera;
    g_camera = 0;
}

JNIEXPORT jboolean JNICALL Java_com_example_cubesolver_camera_YoloNcnn_loadModel(JNIEnv* env, jobject thiz, jobject assetManager, jint cpugpu, jint size_id, jstring model_name_jstr)
{
    if (cpugpu < 0 || cpugpu > 2 || size_id < 0 || size_id > 1)
    {
        return JNI_FALSE;
    }

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    // 【新增】：将 Kotlin 传来的 jstring 转成 C++ 的 std::string
    const char* model_name_c_str = env->GetStringUTFChars(model_name_jstr, NULL);
    std::string model_name = model_name_c_str;
    env->ReleaseStringUTFChars(model_name_jstr, model_name_c_str); // 释放内存防止泄露

    // 动态拼接文件名
    std::string parampath = model_name + ".ncnn.param";
    std::string modelpath = model_name + ".ncnn.bin";

    bool use_gpu = (int)cpugpu == 1;
    bool use_turnip = (int)cpugpu == 2;

    {
        ncnn::MutexLockGuard g(lock);

        // 【修改】：加入 model_name 作为触发热重载的条件
        static int old_cpugpu = -1;
        static int old_size_id = -1;
        static std::string old_model_name = "";

        if (cpugpu != old_cpugpu || size_id != old_size_id || model_name != old_model_name)
        {
            delete g_yolov8;
            g_yolov8 = 0;
        }

        old_cpugpu = cpugpu;
        old_size_id = size_id;
        old_model_name = model_name;

        ncnn::destroy_gpu_instance();

        if (use_turnip) {
            ncnn::create_gpu_instance("libvulkan_freedreno.so");
        } else if (use_gpu) {
            ncnn::create_gpu_instance();
        }

        if (!g_yolov8)
        {
            g_yolov8 = new YOLOv8_pose;
            // 使用动态拼接的路径进行加载
            g_yolov8->load(mgr, parampath.c_str(), modelpath.c_str(), use_gpu || use_turnip);
        }

        int target_size = (size_id == 0) ? 320 : 640;
        g_yolov8->set_det_target_size(target_size);
    }

    return JNI_TRUE;
}

// public native boolean openCamera(int facing);
JNIEXPORT jboolean JNICALL Java_com_example_cubesolver_camera_YoloNcnn_openCamera(JNIEnv* env, jobject thiz, jint facing)
{
    if (facing < 0 || facing > 1)
        return JNI_FALSE;

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "openCamera %d", facing);

    g_camera->open((int)facing);

    return JNI_TRUE;
}

// public native boolean closeCamera();
JNIEXPORT jboolean JNICALL Java_com_example_cubesolver_camera_YoloNcnn_closeCamera(JNIEnv* env, jobject thiz)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "closeCamera");

    g_camera->close();

    return JNI_TRUE;
}

// public native boolean setOutputWindow(Surface surface);
JNIEXPORT jboolean JNICALL Java_com_example_cubesolver_camera_YoloNcnn_setOutputWindow(JNIEnv* env, jobject thiz, jobject surface)
{
    ANativeWindow* win = 0;
    if (surface != NULL) {
        win = ANativeWindow_fromSurface(env, surface);
    }

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "setOutputWindow %p", win);

    if (g_camera) {
        g_camera->set_window(win);
    }

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_example_cubesolver_camera_YoloNcnn_setFpsCap(JNIEnv* env, jobject thiz, jint fps)
{
    g_fps_cap = fps;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_example_cubesolver_camera_YoloNcnn_setProbThreshold(JNIEnv* env, jobject thiz, jfloat threshold)
{
    // 接收从 Kotlin 传过来的浮点数并赋值给全局变量
    g_prob_threshold = threshold;
    return JNI_TRUE;
}

// ==========================================
// 获取当前帧提取的 9 个 ARGB 颜色
// 如果当前没有成功提取（魔方出画或角度不好），返回 null
// ==========================================
JNIEXPORT jintArray JNICALL Java_com_example_cubesolver_camera_YoloNcnn_getLatestColors(JNIEnv* env, jobject thiz)
{
    std::lock_guard<std::mutex> lock(g_color_mutex);

    if (!g_is_cube_detected) {
        return NULL; // 返回 null 告诉 Kotlin 当前无可用的提色结果
    }

    // 创建一个长度为 9 的 jintArray
    jintArray result = env->NewIntArray(9);
    // 将 g_cube_colors 的内容复制到 jintArray 中
    env->SetIntArrayRegion(result, 0, 9, (const jint*)g_cube_colors);

    return result;
}

// ==========================================
// 获取当前帧是否成功检测到符合透视提色条件的魔方
// ==========================================
JNIEXPORT jboolean JNICALL Java_com_example_cubesolver_camera_YoloNcnn_isCubeDetected(JNIEnv* env, jobject thiz)
{
    std::lock_guard<std::mutex> lock(g_color_mutex);
    return g_is_cube_detected ? JNI_TRUE : JNI_FALSE;
}

}
