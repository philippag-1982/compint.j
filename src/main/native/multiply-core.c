#include <jni.h>
//#include <assert.h>

#define BASE32 1000000000

JNIEXPORT void JNICALL Java_philippag_lib_common_math_compint_Int9N_multiplyCore(
		JNIEnv * env,
		jclass cls,
		jintArray resultArray,
		jintArray lhsArray, jint lhsOffset, jint lhsLength,
		jintArray rhsArray, jint rhsOffset, jint rhsLength) {

	int resultLength = lhsLength + rhsLength;
	int lhsMax = lhsOffset + lhsLength - 1;
	int rhsMax = rhsOffset + rhsLength - 1;
	int shift = 1;
	jint carry = 0;
	jboolean isCopy;

	jint * lhs = (*env)->GetPrimitiveArrayCritical(env, lhsArray, &isCopy);
//	assert(!isCopy);
	jint * rhs = (*env)->GetPrimitiveArrayCritical(env, rhsArray, &isCopy);
//	assert(!isCopy);
	jint * result = (*env)->GetPrimitiveArrayCritical(env, resultArray, &isCopy);
//	assert(!isCopy);

	for (int i = rhsMax; i >= rhsOffset; --i, ++shift) {
		jint rhsValue = rhs[i];
		jint k = resultLength - shift;

		for (int j = lhsMax; j >= lhsOffset; --j, --k) {
			jlong lhsValue = lhs[j]; // force multiplication in jlong
			jlong product = carry + lhsValue * rhsValue;

			carry =                 (jint) (product / BASE32);
			jint sum = result[k] + (jint) (product % BASE32);
			if (sum >= BASE32) {
				sum -= BASE32;
//				assert(sum < BASE32);
				carry++;
			}
			result[k] = sum;
		}

		if (carry > 0) {
//			assert(result[k] == 0);
			result[k] = carry;
			carry = 0;
		}
	}

//	assert(carry == 0);

	// these are only needed to end the critical section and let GC continue to work... I guess
	// call them in reverse
	(*env)->ReleasePrimitiveArrayCritical(env, resultArray, result, JNI_ABORT); // it wasn't a copy!
	(*env)->ReleasePrimitiveArrayCritical(env, rhsArray, rhs, JNI_ABORT); // didn't write!
	(*env)->ReleasePrimitiveArrayCritical(env, lhsArray, lhs, JNI_ABORT); // didn't write!
}
