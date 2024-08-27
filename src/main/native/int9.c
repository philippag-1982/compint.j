#include <jni.h>

#ifdef _USE_ASSERT
    #include <assert.h>
    #define ASSERT(statement) assert(statement)
#else
    #define ASSERT(statement)
#endif

#define BASE 1000000000 // 1E9

// "gradle school" multiplication algorithm aka "long multiplication"
JNIEXPORT void JNICALL Java_philippag_lib_common_math_compint_Int9N_multiplyCore(
        JNIEnv * env, jclass cls,
        jintArray resultArray, jint resultLength, jint shift,
        jintArray lhsArray, jint lhsOffset, jint lhsMax,
        jintArray rhsArray, jint rhsOffset, jint rhsMax) {

#ifdef _USE_ARRAY_HACK
	// this works, but is a hack, and curiously performs a bit worse
    struct as_jint_ptr {
        jint * ptr;
    };
    #define get_jint_ptr(jintArray) (((struct as_jint_ptr *) (jintArray))->ptr + sizeof(jint))

    jint * lhs = get_jint_ptr(lhsArray);
    jint * rhs = get_jint_ptr(rhsArray);
    jint * result = get_jint_ptr(resultArray);
#else
    jint * lhs = (*env)->GetPrimitiveArrayCritical(env, lhsArray, /*isCopy*/ NULL);
    jint * rhs = (*env)->GetPrimitiveArrayCritical(env, rhsArray, /*isCopy*/ NULL);
    jint * result = (*env)->GetPrimitiveArrayCritical(env, resultArray, /*isCopy*/ NULL);
#endif

    ASSERT(lhs && rhs && result);

    for (jint i = rhsMax; i >= rhsOffset; --i, ++shift) {
        jint carry = 0;
        jint rhsValue = rhs[i];
        jint k = resultLength - shift;

        for (jint j = lhsMax; j >= lhsOffset; --j, --k) {
            jlong lhsValue = lhs[j]; // force multiplication in jlong
            jlong product = carry + lhsValue * rhsValue;
            carry =    (jint) (product / BASE);
            jint sum = (jint) (product % BASE) + result[k];
            if (sum >= BASE) {
                sum -= BASE;
                ASSERT(sum < BASE);
                carry++;
            }
            result[k] = sum;
        }

        ASSERT(result[k] == 0);
        result[k] = carry;
    }

    // these are only needed to end the critical section and let GC continue to work... I guess
    // call them in reverse
#ifndef _USE_ARRAY_HACK
    (*env)->ReleasePrimitiveArrayCritical(env, resultArray, result, JNI_ABORT); // (hope) it wasn't a copy!
    (*env)->ReleasePrimitiveArrayCritical(env, rhsArray, rhs, JNI_ABORT); // didn't write!
    (*env)->ReleasePrimitiveArrayCritical(env, lhsArray, lhs, JNI_ABORT); // didn't write!
#endif
}
