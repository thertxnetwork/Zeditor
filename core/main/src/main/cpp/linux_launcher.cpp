#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <sys/wait.h>
#include <vector>
#include <cstdlib>
#include <fcntl.h>

#define TAG "LinuxLauncher"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

/**
 * Execute a Linux binary directly without using Android shell.
 * This uses execve() to replace the current process with the Linux binary.
 *
 * @param binary Path to the Linux binary to execute
 * @param args Array of arguments (including argv[0])
 * @param envp Array of environment variables
 * @return Process ID of the child process, or -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_rk_terminal_LinuxLauncher_nativeExec(
        JNIEnv *env,
        jobject /* this */,
        jstring binary,
        jobjectArray args,
        jobjectArray envp) {

    const char *binaryPath = env->GetStringUTFChars(binary, nullptr);
    LOGD("Launching Linux binary: %s", binaryPath);

    // Convert Java string arrays to C arrays
    int argc = env->GetArrayLength(args);
    char **argv = new char*[argc + 1];
    for (int i = 0; i < argc; i++) {
        jstring jstr = (jstring) env->GetObjectArrayElement(args, i);
        const char *str = env->GetStringUTFChars(jstr, nullptr);
        argv[i] = strdup(str);
        env->ReleaseStringUTFChars(jstr, str);
        LOGD("Arg[%d]: %s", i, argv[i]);
    }
    argv[argc] = nullptr;

    int envc = env->GetArrayLength(envp);
    char **envp_c = new char*[envc + 1];
    for (int i = 0; i < envc; i++) {
        jstring jstr = (jstring) env->GetObjectArrayElement(envp, i);
        const char *str = env->GetStringUTFChars(jstr, nullptr);
        envp_c[i] = strdup(str);
        env->ReleaseStringUTFChars(jstr, str);
    }
    envp_c[envc] = nullptr;

    // Fork and exec
    pid_t pid = fork();

    if (pid < 0) {
        LOGE("Fork failed");
        env->ReleaseStringUTFChars(binary, binaryPath);
        return -1;
    }

    if (pid == 0) {
        // Child process
        LOGD("Child process, executing binary...");

        // Execute the binary
        execve(binaryPath, argv, envp_c);

        // If execve returns, it failed
        LOGE("execve failed: %s", strerror(errno));
        _exit(1);
    }

    // Parent process
    LOGD("Parent process, child PID: %d", pid);
    env->ReleaseStringUTFChars(binary, binaryPath);

    // Clean up
    for (int i = 0; i < argc; i++) {
        free(argv[i]);
    }
    delete[] argv;

    for (int i = 0; i < envc; i++) {
        free(envp_c[i]);
    }
    delete[] envp_c;

    return (jint) pid;
}

/**
 * Wait for a process to finish
 */
JNIEXPORT jint JNICALL
Java_com_rk_terminal_LinuxLauncher_nativeWaitFor(
        JNIEnv *env,
        jobject /* this */,
        jint pid) {

    int status;
    pid_t result = waitpid((pid_t) pid, &status, 0);

    if (result < 0) {
        LOGE("waitpid failed: %s", strerror(errno));
        return -1;
    }

    if (WIFEXITED(status)) {
        int exitCode = WEXITSTATUS(status);
        LOGD("Process %d exited with code %d", pid, exitCode);
        return exitCode;
    } else if (WIFSIGNALED(status)) {
        int signal = WTERMSIG(status);
        LOGD("Process %d killed by signal %d", pid, signal);
        return 128 + signal;
    }

    return -1;
}

/**
 * Kill a process
 */
JNIEXPORT jboolean JNICALL
Java_com_rk_terminal_LinuxLauncher_nativeKill(
        JNIEnv *env,
        jobject /* this */,
        jint pid,
        jint signal) {

    int result = kill((pid_t) pid, signal);
    if (result < 0) {
        LOGE("kill failed: %s", strerror(errno));
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/**
 * Execute the Linux dynamic linker directly to run a binary
 * This bypasses the need for any shell by directly invoking ld-linux
 */
JNIEXPORT jint JNICALL
Java_com_rk_terminal_LinuxLauncher_nativeExecWithLinker(
        JNIEnv *env,
        jobject /* this */,
        jstring linker,
        jstring libraryPath,
        jstring binary,
        jobjectArray args,
        jobjectArray envp) {

    const char *linkerPath = env->GetStringUTFChars(linker, nullptr);
    const char *libPath = env->GetStringUTFChars(libraryPath, nullptr);
    const char *binaryPath = env->GetStringUTFChars(binary, nullptr);

    LOGD("Launching with linker: %s", linkerPath);
    LOGD("Library path: %s", libPath);
    LOGD("Binary: %s", binaryPath);

    // Build arguments: linker --library-path <path> <binary> <args...>
    int originalArgc = env->GetArrayLength(args);
    int argc = originalArgc + 4; // linker, --library-path, path, binary
    char **argv = new char*[argc + 1];

    argv[0] = strdup(linkerPath);
    argv[1] = strdup("--library-path");
    argv[2] = strdup(libPath);
    argv[3] = strdup(binaryPath);

    for (int i = 0; i < originalArgc; i++) {
        jstring jstr = (jstring) env->GetObjectArrayElement(args, i);
        const char *str = env->GetStringUTFChars(jstr, nullptr);
        argv[4 + i] = strdup(str);
        env->ReleaseStringUTFChars(jstr, str);
        LOGD("Arg[%d]: %s", 4 + i, argv[4 + i]);
    }
    argv[argc] = nullptr;

    // Build environment
    int envc = env->GetArrayLength(envp);
    char **envp_c = new char*[envc + 1];
    for (int i = 0; i < envc; i++) {
        jstring jstr = (jstring) env->GetObjectArrayElement(envp, i);
        const char *str = env->GetStringUTFChars(jstr, nullptr);
        envp_c[i] = strdup(str);
        env->ReleaseStringUTFChars(jstr, str);
    }
    envp_c[envc] = nullptr;

    // Fork and exec
    pid_t pid = fork();

    if (pid < 0) {
        LOGE("Fork failed");
        env->ReleaseStringUTFChars(linker, linkerPath);
        env->ReleaseStringUTFChars(libraryPath, libPath);
        env->ReleaseStringUTFChars(binary, binaryPath);
        return -1;
    }

    if (pid == 0) {
        // Child process
        LOGD("Child process, executing with linker...");

        // Execute using the linker
        execve(linkerPath, argv, envp_c);

        // If execve returns, it failed
        LOGE("execve failed: %s", strerror(errno));
        _exit(1);
    }

    // Parent process
    LOGD("Parent process, child PID: %d", pid);

    env->ReleaseStringUTFChars(linker, linkerPath);
    env->ReleaseStringUTFChars(libraryPath, libPath);
    env->ReleaseStringUTFChars(binary, binaryPath);

    // Clean up
    for (int i = 0; i < argc; i++) {
        free(argv[i]);
    }
    delete[] argv;

    for (int i = 0; i < envc; i++) {
        free(envp_c[i]);
    }
    delete[] envp_c;

    return (jint) pid;
}

} // extern "C"
