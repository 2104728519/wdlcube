#include <jni.h>
#include <string>
#include <android/log.h>
#include <min2phase/min2phase.h>
#include <min2phase/tools.h>

#define LOG_TAG "CubeSolverNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 全局保存 JavaVM 指针，用于子线程动态附着 JVM
static JavaVM* g_vm = nullptr;

// 跨线程 JNI 回调代理包装类
class JniSearchCallback : public min2phase::SearchCallback {
private:
    jobject m_callbackObj; // 全局引用，确保跨线程生命周期安全
    jmethodID m_onProgressId;
    jmethodID m_onNewSolutionFoundId;
    jmethodID m_isCancelledId;

    // 获取或附着当前线程 of the JNIEnv
    JNIEnv* getEnv(bool& mustDetach) {
        JNIEnv* env = nullptr;
        if (g_vm == nullptr) {
            LOGE("JavaVM pointer is null!");
            mustDetach = false;
            return nullptr;
        }

        jint res = g_vm->GetEnv((void**)&env, JNI_VERSION_1_6);
        if (res == JNI_EDETACHED) {
            // 当前线程未附着 JVM 时，尝试动态附着
            res = g_vm->AttachCurrentThread(&env, nullptr);
            if (res == JNI_OK) {
                mustDetach = true;
            } else {
                mustDetach = false;
                LOGE("Failed to attach current thread to JVM");
            }
        } else {
            mustDetach = false;
        }
        return env;
    }

    // 释放当前线程的 JVM 附着
    void detachEnv(bool mustDetach) {
        if (mustDetach && g_vm != nullptr) {
            g_vm->DetachCurrentThread();
        }
    }

public:
    JniSearchCallback(JNIEnv* env, jobject callbackObj) {
        env->GetJavaVM(&g_vm);

        // 创建全局引用，防止跨线程访问时对象被回收
        m_callbackObj = env->NewGlobalRef(callbackObj);

        jclass clazz = env->GetObjectClass(callbackObj);
        m_onProgressId = env->GetMethodID(clazz, "onProgress", "(JI)V");
        m_onNewSolutionFoundId = env->GetMethodID(clazz, "onNewSolutionFound", "(Ljava/lang/String;I)V");
        m_isCancelledId = env->GetMethodID(clazz, "isCancelled", "()Z");
    }

    ~JniSearchCallback() override {
        // 释放全局引用，防止内存泄漏
        bool mustDetach = false;
        JNIEnv* env = getEnv(mustDetach);
        if (env != nullptr) {
            env->DeleteGlobalRef(m_callbackObj);
        }
        detachEnv(mustDetach);
    }

    void onProgress(int64_t nodesSearched, int8_t currentDepth) override {
        bool mustDetach = false;
        JNIEnv* env = getEnv(mustDetach);
        if (env != nullptr) {
            env->CallVoidMethod(m_callbackObj, m_onProgressId, (jlong)nodesSearched, (jint)currentDepth);
        }
        detachEnv(mustDetach);
    }

    void onNewSolutionFound(const std::string& solution, int8_t steps) override {
        bool mustDetach = false;
        JNIEnv* env = getEnv(mustDetach);
        if (env != nullptr) {
            jstring jSol = env->NewStringUTF(solution.c_str());
            env->CallVoidMethod(m_callbackObj, m_onNewSolutionFoundId, jSol, (jint)steps);
            // 释放局部引用，防止引用表溢出
            env->DeleteLocalRef(jSol);
        }
        detachEnv(mustDetach);
    }

    bool isCancelled() override {
        bool mustDetach = false;
        JNIEnv* env = getEnv(mustDetach);
        bool cancelled = false;
        if (env != nullptr) {
            cancelled = (env->CallBooleanMethod(m_callbackObj, m_isCancelledId) == JNI_TRUE);
        }
        detachEnv(mustDetach);
        return cancelled;
    }
};

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_cubesolver_utils_CubeSolver_initNative(JNIEnv *env, jobject thiz, jstring cachePath) {
    const char *path_cstr = env->GetStringUTFChars(cachePath, nullptr);
    std::string path(path_cstr);
    env->ReleaseStringUTFChars(cachePath, path_cstr);

    try {
        bool loaded = min2phase::loadFile(path);
        if (!loaded) {
            min2phase::init();
            min2phase::writeFile(path);
        }
        return JNI_TRUE;
    } catch (...) {
        return JNI_FALSE;
    }
}

JNIEXPORT jstring JNICALL
Java_com_example_cubesolver_utils_CubeSolver_solveNative(
        JNIEnv *env,
        jobject thiz,
        jstring facelets,
        jint maxDepth,
        jlong probeMax,
        jlong probeMin,
        jint verbose,
        jobject callbackObj) {

    const char *facelets_cstr = env->GetStringUTFChars(facelets, nullptr);
    std::string facelets_str(facelets_cstr);
    env->ReleaseStringUTFChars(facelets, facelets_cstr);

    JniSearchCallback* callbackBridge = nullptr;
    if (callbackObj != nullptr) {
        callbackBridge = new JniSearchCallback(env, callbackObj);
    }

    // 调用底层多线程搜索算法
    std::string result = min2phase::solve(
            facelets_str,
            (int8_t) maxDepth,
            (int32_t) probeMax,
            (int32_t) probeMin,
            (int8_t) verbose,
            nullptr,
            callbackBridge
    );

    if (callbackBridge != nullptr) {
        delete callbackBridge;
    }

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_example_cubesolver_utils_CubeSolver_randomCubeNative(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF(min2phase::tools::randomCube().c_str());
}

JNIEXPORT jstring JNICALL
Java_com_example_cubesolver_utils_CubeSolver_superFlipNative(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF(min2phase::tools::superFlip().c_str());
}

} // extern "C"
