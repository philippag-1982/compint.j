#include <jni.h>
//#include <assert.h>

#define BASE 1000000000 // 1E9

JNIEXPORT void JNICALL Java_philippag_lib_common_math_compint_Int9N_multiplyCore(
        JNIEnv * env,
        jclass cls,
        jintArray resultArray, jint resultLength, jint shift,
        jintArray lhsArray, jint lhsOffset, jint lhsMax,
        jintArray rhsArray, jint rhsOffset, jint rhsMax) {

    jint carry = 0;
    jboolean isCopy;

    jint * lhs = (*env)->GetPrimitiveArrayCritical(env, lhsArray, &isCopy);
    //assert(!isCopy);
    jint * rhs = (*env)->GetPrimitiveArrayCritical(env, rhsArray, &isCopy);
    //assert(!isCopy);
    jint * result = (*env)->GetPrimitiveArrayCritical(env, resultArray, &isCopy);
    //assert(!isCopy);

    for (int i = rhsMax; i >= rhsOffset; --i, ++shift) {
        jint rhsValue = rhs[i];
        int k = resultLength - shift;

        for (int j = lhsMax; j >= lhsOffset; --j, --k) {
            jlong lhsValue = lhs[j]; // force multiplication in jlong
            jlong product = carry + lhsValue * rhsValue;

            carry =                (jint) (product / BASE);
            jint sum = result[k] + (jint) (product % BASE);
            if (sum >= BASE) {
                sum -= BASE;
                //assert(sum < BASE);
                carry++;
            }
            result[k] = sum;
        }

        if (carry > 0) {
            //assert(result[k] == 0);
            result[k] = carry;
            carry = 0;
        }
    }

    //assert(carry == 0);

    // these are only needed to end the critical section and let GC continue to work... I guess
    // call them in reverse
    (*env)->ReleasePrimitiveArrayCritical(env, resultArray, result, JNI_ABORT); // it wasn't a copy!
    (*env)->ReleasePrimitiveArrayCritical(env, rhsArray, rhs, JNI_ABORT); // didn't write!
    (*env)->ReleasePrimitiveArrayCritical(env, lhsArray, lhs, JNI_ABORT); // didn't write!
}