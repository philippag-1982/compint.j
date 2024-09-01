# User input ARGS:
# ARGS="<lhs-size>? <rhs-size>?" make test
# ARGS="<lhs-size>? <rhs-size>? -b" make test # to use java.math.BigInteger
# ARGS="<lhs-size>? <rhs-size>? -j" make test # to Java Int9 (not native Int9N)

ifndef VERBOSE
.SILENT:
endif

ROOT_DIR=../../..
JAR_DIR=$(ROOT_DIR)/build/libs
BUILD_DIR=$(ROOT_DIR)/src/main/resources
ifeq ($(OS),Windows_NT)
	JNI_MD_INCLUDE_DIR=win32
else
	JNI_MD_INCLUDE_DIR=linux
endif
FLAGS=
#FLAGS=-D_USE_ASSERT -D_USE_ARRAY_HACK
OPTS=$(FLAGS) -O3 -Wall -Werror -pedantic -std=c17 -D_JNI_IMPLEMENTATION_ -I $(JAVA_HOME)/include -I $(JAVA_HOME)/include/$(JNI_MD_INCLUDE_DIR) -fPIC
CC=gcc

all: int9

clean:
	rm -f $(BUILD_DIR)/*.so

int9: int9.c
	$(CC) $(OPTS) -nostdlib -o $(BUILD_DIR)/int9.o -c int9.c
	$(CC) $(OPTS) -shared -o $(BUILD_DIR)/int9.so $(BUILD_DIR)/int9.o
	rm $(BUILD_DIR)/int9.o
	file $(BUILD_DIR)/int9.so

test:
	java -ea -cp $(JAR_DIR)/compint.j.jar Demo.java $(ARGS)
