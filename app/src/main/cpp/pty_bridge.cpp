/**
 * ═══════════════════════════════════════════════════════════════
 * ELYSIUM CODE — PTY (Pseudo-Terminal) Native Bridge
 * ═══════════════════════════════════════════════════════════════
 * 
 * Provides native PTY allocation for real terminal emulation
 * on Android. Uses forkpty() to create genuine shell sessions
 * with full I/O multiplexing.
 */

#include <jni.h>
#include <android/log.h>
#include <cstdlib>
#include <cstring>
#include <cerrno>
#include <unistd.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <termios.h>
#include <pty.h>
#include <poll.h>
#include <string>
#include <vector>
#include <map>
#include <mutex>

#define LOG_TAG "ElysiumPTY"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ═══════════════════════════════════════════════════════════════
// PTY Session Management
// ═══════════════════════════════════════════════════════════════

struct PtySession {
    int masterFd;
    pid_t childPid;
    bool alive;
    std::string shellPath;
};

static std::map<int, PtySession> g_sessions;
static std::mutex g_pty_mutex;
static int g_next_session_id = 1;

extern "C" {

/**
 * Create a new PTY session and fork a shell process
 * Returns session ID (>0) on success, -1 on failure
 */
JNIEXPORT jint JNICALL
Java_com_elysium_code_terminal_PtyProcess_nativeCreateSession(
    JNIEnv* env, jobject thiz,
    jstring shellPath,
    jint rows,
    jint cols,
    jobjectArray envVars
) {
    std::lock_guard<std::mutex> lock(g_pty_mutex);

    std::string shell = "/system/bin/sh";
    if (shellPath) {
        const char* path = env->GetStringUTFChars(shellPath, nullptr);
        shell = path;
        env->ReleaseStringUTFChars(shellPath, path);
    }

    // Setup terminal size
    struct winsize ws;
    ws.ws_row = rows > 0 ? rows : 24;
    ws.ws_col = cols > 0 ? cols : 80;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;

    int masterFd;
    pid_t pid;

    // Create PTY and fork
    pid = forkpty(&masterFd, nullptr, nullptr, &ws);

    if (pid < 0) {
        LOGE("forkpty() failed: %s", strerror(errno));
        return -1;
    }

    if (pid == 0) {
        // ═══ CHILD PROCESS ═══

        // Setup environment variables
        if (envVars) {
            int envCount = env->GetArrayLength(envVars);
            for (int i = 0; i < envCount; i++) {
                jstring jEnv = (jstring) env->GetObjectArrayElement(envVars, i);
                const char* envStr = env->GetStringUTFChars(jEnv, nullptr);
                putenv(strdup(envStr));
                env->ReleaseStringUTFChars(jEnv, envStr);
            }
        }

        // Set basic environment
        setenv("TERM", "xterm-256color", 1);
        setenv("COLORTERM", "truecolor", 1);
        setenv("HOME", "/data/data/com.elysium.code/files/home", 1);
        setenv("SHELL", shell.c_str(), 1);
        setenv("LANG", "en_US.UTF-8", 1);
        setenv("ELYSIUM", "1", 1);

        // Change to home directory
        const char* home = getenv("HOME");
        if (home) {
            chdir(home);
        }

        // Execute shell
        execlp(shell.c_str(), shell.c_str(), "-l", nullptr);

        // If exec fails
        LOGE("exec(%s) failed: %s", shell.c_str(), strerror(errno));
        _exit(1);
    }

    // ═══ PARENT PROCESS ═══
    
    // Set master fd to non-blocking
    int flags = fcntl(masterFd, F_GETFL, 0);
    fcntl(masterFd, F_SETFL, flags | O_NONBLOCK);

    int sessionId = g_next_session_id++;
    g_sessions[sessionId] = {masterFd, pid, true, shell};

    LOGI("PTY session %d created (pid=%d, shell=%s, %dx%d)",
         sessionId, pid, shell.c_str(), ws.ws_col, ws.ws_row);

    return sessionId;
}

/**
 * Read available data from PTY
 * Returns the data read as a byte array, or null if no data
 */
JNIEXPORT jbyteArray JNICALL
Java_com_elysium_code_terminal_PtyProcess_nativeRead(
    JNIEnv* env, jobject thiz,
    jint sessionId,
    jint maxBytes
) {
    std::lock_guard<std::mutex> lock(g_pty_mutex);

    auto it = g_sessions.find(sessionId);
    if (it == g_sessions.end() || !it->second.alive) {
        return nullptr;
    }

    int bufSize = maxBytes > 0 ? maxBytes : 8192;
    std::vector<char> buffer(bufSize);

    // Poll for data with 50ms timeout
    struct pollfd pfd;
    pfd.fd = it->second.masterFd;
    pfd.events = POLLIN;

    int pollResult = poll(&pfd, 1, 50);
    if (pollResult <= 0) {
        return nullptr;
    }

    ssize_t bytesRead = read(it->second.masterFd, buffer.data(), bufSize);
    if (bytesRead <= 0) {
        if (bytesRead == 0 || (errno != EAGAIN && errno != EWOULDBLOCK)) {
            it->second.alive = false;
        }
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(bytesRead);
    env->SetByteArrayRegion(result, 0, bytesRead, (jbyte*)buffer.data());
    return result;
}

/**
 * Write data to PTY (send input to shell)
 */
JNIEXPORT jint JNICALL
Java_com_elysium_code_terminal_PtyProcess_nativeWrite(
    JNIEnv* env, jobject thiz,
    jint sessionId,
    jbyteArray data
) {
    std::lock_guard<std::mutex> lock(g_pty_mutex);

    auto it = g_sessions.find(sessionId);
    if (it == g_sessions.end() || !it->second.alive) {
        return -1;
    }

    int len = env->GetArrayLength(data);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);

    ssize_t written = write(it->second.masterFd, bytes, len);

    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    return (jint)written;
}

/**
 * Resize PTY terminal
 */
JNIEXPORT void JNICALL
Java_com_elysium_code_terminal_PtyProcess_nativeResize(
    JNIEnv* env, jobject thiz,
    jint sessionId,
    jint rows,
    jint cols
) {
    std::lock_guard<std::mutex> lock(g_pty_mutex);

    auto it = g_sessions.find(sessionId);
    if (it == g_sessions.end() || !it->second.alive) return;

    struct winsize ws;
    ws.ws_row = rows;
    ws.ws_col = cols;
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;

    ioctl(it->second.masterFd, TIOCSWINSZ, &ws);
    // Send SIGWINCH to notify the shell of resize
    kill(it->second.childPid, SIGWINCH);

    LOGI("Session %d resized to %dx%d", sessionId, cols, rows);
}

/**
 * Send signal to PTY child process
 */
JNIEXPORT void JNICALL
Java_com_elysium_code_terminal_PtyProcess_nativeSendSignal(
    JNIEnv* env, jobject thiz,
    jint sessionId,
    jint signal
) {
    std::lock_guard<std::mutex> lock(g_pty_mutex);

    auto it = g_sessions.find(sessionId);
    if (it == g_sessions.end() || !it->second.alive) return;

    kill(it->second.childPid, signal);
    LOGI("Signal %d sent to session %d (pid=%d)", signal, sessionId, it->second.childPid);
}

/**
 * Check if session is still alive
 */
JNIEXPORT jboolean JNICALL
Java_com_elysium_code_terminal_PtyProcess_nativeIsAlive(
    JNIEnv* env, jobject thiz,
    jint sessionId
) {
    std::lock_guard<std::mutex> lock(g_pty_mutex);

    auto it = g_sessions.find(sessionId);
    if (it == g_sessions.end()) return JNI_FALSE;

    // Check if child process is still running
    int status;
    pid_t result = waitpid(it->second.childPid, &status, WNOHANG);
    if (result != 0) {
        it->second.alive = false;
        return JNI_FALSE;
    }

    return it->second.alive ? JNI_TRUE : JNI_FALSE;
}

/**
 * Destroy a PTY session
 */
JNIEXPORT void JNICALL
Java_com_elysium_code_terminal_PtyProcess_nativeDestroySession(
    JNIEnv* env, jobject thiz,
    jint sessionId
) {
    std::lock_guard<std::mutex> lock(g_pty_mutex);

    auto it = g_sessions.find(sessionId);
    if (it == g_sessions.end()) return;

    // Kill child process
    kill(it->second.childPid, SIGTERM);
    usleep(100000); // 100ms grace
    kill(it->second.childPid, SIGKILL);

    // Close master fd
    close(it->second.masterFd);

    // Reap child
    waitpid(it->second.childPid, nullptr, 0);

    g_sessions.erase(it);
    LOGI("Session %d destroyed", sessionId);
}

/**
 * Get the exit code of a finished session
 */
JNIEXPORT jint JNICALL
Java_com_elysium_code_terminal_PtyProcess_nativeGetExitCode(
    JNIEnv* env, jobject thiz,
    jint sessionId
) {
    std::lock_guard<std::mutex> lock(g_pty_mutex);

    auto it = g_sessions.find(sessionId);
    if (it == g_sessions.end()) return -1;

    int status;
    pid_t result = waitpid(it->second.childPid, &status, WNOHANG);
    if (result > 0 && WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    return -1;
}

} // extern "C"
