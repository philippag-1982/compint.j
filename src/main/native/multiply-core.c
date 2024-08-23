#include <jni.h>
//#include <assert.h>

#ifdef _WIN32
	#define INT128  __int128_t
	#define INT64   __int64
	#define INT32   __int32
//	#define INT64   long long
//	#define INT32   long
#else
	#define INT128  __int128_t
	#define INT64   __int64_t
	#define INT32   __int32_t
//	#define INT64   jlong
//	#define INT32   jint
#endif

#define WIDTH 40

#define BASE32 1000000000
//#define BASE64 1000000000000000000

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
	INT32 carry = 0;
	jboolean isCopy;

	jint * lhs = (*env)->GetPrimitiveArrayCritical(env, lhsArray, &isCopy);
//	assert(!isCopy);
	jint * rhs = (*env)->GetPrimitiveArrayCritical(env, rhsArray, &isCopy);
//	assert(!isCopy);
	jint * result = (*env)->GetPrimitiveArrayCritical(env, resultArray, &isCopy);
//	assert(!isCopy);

	for (int i = rhsMax; i >= rhsOffset; --i, ++shift) {
		INT32 rhsValue = rhs[i];
		INT32 k = resultLength - shift;

		for (int j = lhsMax; j >= lhsOffset; --j, --k) {
			INT64 lhsValue = lhs[j]; // force multiplication in INT64
			INT64 product = carry + lhsValue * rhsValue;

			carry =                 (INT32) (product / BASE32);
			INT32 sum = result[k] + (INT32) (product % BASE32);
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
