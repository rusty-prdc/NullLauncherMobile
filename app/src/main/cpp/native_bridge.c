// Stage B: запуск Java-игры ВНУТРИ процесса приложения через JNI.
// Библиотека libjvm.so из Android-сборки OpenJDK подгружается dlopen'ом,
// создаётся JVM, в рабочем каталоге сборки вызывается main класса игры.
#include <jni.h>
#include <dlfcn.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define TAG "NullLauncher-NB"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#ifndef JNI_VERSION_1_8
#define JNI_VERSION_1_8 0x00010008
#endif

typedef jint (*CreateJavaVM_t)(JavaVM **pvm, void **penv, void *args);

/* Коды возврата (совпадают с NativeBridge.kt) */
enum {
    NB_OK = 0,
    NB_DLOPEN_FAIL = -1,
    NB_NO_SYMBOL = -2,
    NB_VM_FAIL = -3,
    NB_NO_MAIN = -4,
    NB_MAIN_THREW = -5,
    NB_BAD_ARGS = -6,
};

JNIEXPORT jint JNICALL
Java_org_nulllauncher_mobile_data_launch_NativeBridge_launch(
    JNIEnv *env,
    jclass clazz,
    jstring jLibjvm,
    jstring jClasspath,
    jstring jMainClass,
    jobjectArray jArgs,
    jstring jGameDir,
    jstring jNativesDir,
    jstring jLogFile,
    jint jMemoryMb
) {
    (void) clazz;

    const char *libjvm = (*env)->GetStringUTFChars(env, jLibjvm, NULL);
    const char *classpath = (*env)->GetStringUTFChars(env, jClasspath, NULL);
    const char *mainClass = (*env)->GetStringUTFChars(env, jMainClass, NULL);
    const char *gameDir = (*env)->GetStringUTFChars(env, jGameDir, NULL);
    const char *nativesDir = (*env)->GetStringUTFChars(env, jNativesDir, NULL);
    const char *logFile = (*env)->GetStringUTFChars(env, jLogFile, NULL);

    if (!libjvm || !classpath || !mainClass || !gameDir) {
        return NB_BAD_ARGS;
    }

    LOGI("libjvm=%s main=%s mem=%dMB", libjvm, mainClass, jMemoryMb);

    /* вывод игры — в лог-файл, чтобы ловил CrashAnalyzer */
    if (logFile && *logFile) {
        FILE *f = freopen(logFile, "a", stdout);
        if (f) setvbuf(stdout, NULL, _IONBF, 0);
        freopen(logFile, "a", stderr);
    }

    /* рабочий каталог = папка сборки (моды, миры, options.txt) */
    if (chdir(gameDir) != 0) {
        LOGE("chdir failed: %s", gameDir);
    }

    /* GL4ES: трансляция OpenGL -> GLES. Подгружается ДО JVM с RTLD_GLOBAL,
       чтобы символы libGL были видны нативникам LWJGL при dlopen. */
    if (nativesDir && *nativesDir) {
        char gl4es[4096];
        snprintf(gl4es, sizeof(gl4es), "%s/libgl4es.so", nativesDir);
        void *gl = dlopen(gl4es, RTLD_NOW | RTLD_GLOBAL);
        if (gl) {
            LOGI("gl4es preloaded: %s", gl4es);
        } else {
            LOGE("gl4es not loaded (%s) - GLFW will fail", dlerror());
        }
        /* среда трансляции: форсируем GLES-путь */
        setenv("LIBGL_ALWAYS_INDIRECT", "0", 0);
        setenv("LIBGL_NOERROR", "1", 0);
        setenv("MESA_GL_VERSION_OVERRIDE", "2.1", 0);
    }

    void *handle = dlopen(libjvm, RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        LOGE("dlopen libjvm failed: %s", dlerror());
        return NB_DLOPEN_FAIL;
    }

    CreateJavaVM_t createVM = (CreateJavaVM_t) dlsym(handle, "JNI_CreateJavaVM");
    if (!createVM) {
        LOGE("JNI_CreateJavaVM not found: %s", dlerror());
        return NB_NO_SYMBOL;
    }

    char optClasspath[8192];
    char optXmx[64];
    char optLibPath[4096];
    char optBrand[128];
    snprintf(optClasspath, sizeof(optClasspath), "-Djava.class.path=%s", classpath);
    snprintf(optXmx, sizeof(optXmx), "-Xmx%dM", jMemoryMb > 0 ? jMemoryMb : 2048);
    snprintf(optLibPath, sizeof(optLibPath), "-Djava.library.path=%s",
             nativesDir ? nativesDir : gameDir);
    snprintf(optBrand, sizeof(optBrand), "-Dminecraft.launcher.brand=nullLauncher-Mobile");

    JavaVMOption options[4];
    options[0].optionString = optClasspath;
    options[0].extraInfo = NULL;
    options[1].optionString = optXmx;
    options[1].extraInfo = NULL;
    options[2].optionString = optLibPath;
    options[2].extraInfo = NULL;
    options[3].optionString = optBrand;
    options[3].extraInfo = NULL;

    JavaVMInitArgs vmArgs;
    vmArgs.version = JNI_VERSION_1_8;
    vmArgs.nOptions = 4;
    vmArgs.options = options;
    vmArgs.ignoreUnrecognized = JNI_FALSE;

    JavaVM *vm = NULL;
    JNIEnv *jenv = NULL;
    jint rc = createVM(&vm, (void **) &jenv, &vmArgs);
    if (rc != JNI_OK || !jenv) {
        LOGE("JNI_CreateJavaVM failed: %d", rc);
        return NB_VM_FAIL;
    }
    LOGI("JVM created, running main...");

    /* mainClass: com.foo.Bar -> com/foo/Bar */
    char clsName[512];
    snprintf(clsName, sizeof(clsName), "%s", mainClass);
    for (char *p = clsName; *p; ++p) {
        if (*p == '.') *p = '/';
    }

    jclass mainJClass = (*jenv)->FindClass(jenv, clsName);
    if (!mainJClass) {
        (*jenv)->ExceptionClear(jenv);
        LOGE("main class not found: %s", clsName);
        return NB_NO_MAIN;
    }

    jmethodID mainM = (*jenv)->GetStaticMethodID(jenv, mainJClass, "main",
                                                  "([Ljava/lang/String;)V");
    if (!mainM) {
        (*jenv)->ExceptionClear(jenv);
        LOGE("main() not found: %s", clsName);
        return NB_NO_MAIN;
    }

    /* String[] args */
    jint argc = (*env)->GetArrayLength(env, jArgs);
    jclass strCls = (*jenv)->FindClass(jenv, "java/lang/String");
    jobjectArray args = (*jenv)->NewObjectArray(jenv, argc, strCls, NULL);
    for (jint i = 0; i < argc; ++i) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, jArgs, i);
        (*jenv)->SetObjectArrayElement(jenv, args, i, s);
        (*env)->DeleteLocalRef(env, s);
    }

    (*jenv)->CallStaticVoidMethod(jenv, mainJClass, mainM, args);

    jint exitCode = NB_OK;
    if ((*jenv)->ExceptionCheck(jenv)) {
        (*jenv)->ExceptionDescribe(jenv); /* стек — в stderr → лог-файл */
        (*jenv)->ExceptionClear(jenv);
        exitCode = NB_MAIN_THREW;
        LOGE("main() threw");
    }

    LOGI("game finished, destroying JVM");
    (*vm)->DestroyJavaVM(vm);
    dlclose(handle);

    (*env)->ReleaseStringUTFChars(env, jLibjvm, libjvm);
    (*env)->ReleaseStringUTFChars(env, jClasspath, classpath);
    (*env)->ReleaseStringUTFChars(env, jMainClass, mainClass);
    (*env)->ReleaseStringUTFChars(env, jGameDir, gameDir);
    (*env)->ReleaseStringUTFChars(env, jNativesDir, nativesDir);
    (*env)->ReleaseStringUTFChars(env, jLogFile, logFile);
    return exitCode;
}