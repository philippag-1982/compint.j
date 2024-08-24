#include <jni.h>
//#include <assert.h>

#define BASE 10000000000000000 // 1E16

JNIEXPORT void JNICALL Java_philippag_lib_common_math_compint_Int16N_multiplyCore(
        JNIEnv * env,
        jclass cls,
        jlongArray resultArray,
        jlongArray lhsArray, jint lhsOffset, jint lhsLength,
        jlongArray rhsArray, jint rhsOffset, jint rhsLength) {

    int resultLength = lhsLength + rhsLength;
    int lhsMax = lhsOffset + lhsLength - 1;
    int rhsMax = rhsOffset + rhsLength - 1;
    int shift = 1;
    jlong carry = 0;
    jboolean isCopy;

    jlong * lhs = (*env)->GetPrimitiveArrayCritical(env, lhsArray, &isCopy);
//    assert(!isCopy);
    jlong * rhs = (*env)->GetPrimitiveArrayCritical(env, rhsArray, &isCopy);
//    assert(!isCopy);
    jlong * result = (*env)->GetPrimitiveArrayCritical(env, resultArray, &isCopy);
//    assert(!isCopy);

    for (int i = rhsMax; i >= rhsOffset; --i, ++shift) {
        jlong rhsValue = rhs[i];
        int k = resultLength - shift;

        for (int j = lhsMax; j >= lhsOffset; --j, --k) {
            __int128_t lhsValue = lhs[j]; // force multiplication in __int128_t
            __int128_t product = carry + lhsValue * rhsValue;

            carry =                 (jlong) (product / BASE);
            jlong sum = result[k] + (jlong) (product % BASE);
            if (sum >= BASE) {
                sum -= BASE;
//                assert(sum < BASE);
                carry++;
            }
            result[k] = sum;
        }

        if (carry > 0) {
//            assert(result[k] == 0);
            result[k] = carry;
            carry = 0;
        }
    }

//    assert(carry == 0);

    // these are only needed to end the critical section and let GC continue to work... I guess
    // call them in reverse
    (*env)->ReleasePrimitiveArrayCritical(env, resultArray, result, JNI_ABORT); // it wasn't a copy!
    (*env)->ReleasePrimitiveArrayCritical(env, rhsArray, rhs, JNI_ABORT); // didn't write!
    (*env)->ReleasePrimitiveArrayCritical(env, lhsArray, lhs, JNI_ABORT); // didn't write!
}
