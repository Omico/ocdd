#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <sys/stat.h>

enum {
    RESULT_ERROR = 0,
    RESULT_MODE = 1,
    RESULT_SIZE = 2,
    RESULT_MODIFIED_SECONDS = 3,
    RESULT_MODIFIED_NANOSECONDS = 4,
    RESULT_ACCESS_SECONDS = 5,
    RESULT_ACCESS_NANOSECONDS = 6,
    RESULT_SIZE_COUNT = 7,
};

static char *copy_path(JNIEnv *environment, jbyteArray path_bytes) {
    const jsize size = (*environment)->GetArrayLength(environment, path_bytes);
    char *path = malloc((size_t) size + 1U);
    if (path == NULL) return NULL;
    (*environment)->GetByteArrayRegion(environment, path_bytes, 0, size, (jbyte *) path);
    if ((*environment)->ExceptionCheck(environment)) {
        free(path);
        return NULL;
    }
    path[size] = '\0';
    return path;
}

JNIEXPORT jlongArray

JNICALL
Java_me_omico_ocdd_io_AndroidPosixAttributesNative_readAttributes(
        JNIEnv *environment,
        jclass type,
        jbyteArray path_bytes,
        jboolean no_follow_links
) {
    (void) type;
    jlong values[RESULT_SIZE_COUNT] = {0};
    char *path = copy_path(environment, path_bytes);
    if (path == NULL) {
        if ((*environment)->ExceptionCheck(environment)) return NULL;
        values[RESULT_ERROR] = ENOMEM;
    } else {
        struct stat status;
        const int result = no_follow_links ? lstat(path, &status) : stat(path, &status);
        const int error = result == 0 ? 0 : errno;
        free(path);
        values[RESULT_ERROR] = error;
        if (result == 0) {
            values[RESULT_MODE] = status.st_mode;
            values[RESULT_SIZE] = status.st_size;
            values[RESULT_MODIFIED_SECONDS] = status.st_mtim.tv_sec;
            values[RESULT_MODIFIED_NANOSECONDS] = status.st_mtim.tv_nsec;
            values[RESULT_ACCESS_SECONDS] = status.st_atim.tv_sec;
            values[RESULT_ACCESS_NANOSECONDS] = status.st_atim.tv_nsec;
        }
    }

    jlongArray result = (*environment)->NewLongArray(environment, RESULT_SIZE_COUNT);
    if (result != NULL) {
        (*environment)->SetLongArrayRegion(environment, result, 0, RESULT_SIZE_COUNT, values);
    }
    return result;
}

JNIEXPORT jint

JNICALL
Java_me_omico_ocdd_io_AndroidPosixAttributesNative_setLastModifiedTime(
        JNIEnv *environment,
        jclass type,
        jbyteArray path_bytes,
        jlong seconds,
        jint nanoseconds
) {
    (void) type;
    char *path = copy_path(environment, path_bytes);
    if (path == NULL) {
        return (*environment)->ExceptionCheck(environment) ? 0 : ENOMEM;
    }
    const time_t modified_seconds = (time_t) seconds;
    if ((jlong) modified_seconds != seconds) {
        free(path);
        return EOVERFLOW;
    }
    const struct timespec times[2] = {
            {.tv_sec = 0, .tv_nsec = UTIME_OMIT},
            {.tv_sec = modified_seconds, .tv_nsec = nanoseconds},
    };
    const int result = utimensat(AT_FDCWD, path, times, 0);
    const int error = result == 0 ? 0 : errno;
    free(path);
    return error;
}
