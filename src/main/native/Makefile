BUILD_DIR=../resources
DEPLOY_DIR=../../../build/libs
ifeq ($(OS),Windows_NT)
	JNI_MD_INCLUDE_DIR=win32
else
	JNI_MD_INCLUDE_DIR=linux
endif
FLAGS=
#FLAGS=-D_USE_ASSERT _USE_ARRAY_HACK
OPTS=$(FLAGS) -O3 -Wall -Werror -pedantic -std=c17 -D_JNI_IMPLEMENTATION_ -I $(JAVA_HOME)/include -I $(JAVA_HOME)/include/$(JNI_MD_INCLUDE_DIR) -fPIC
CC=gcc

all: int9

init:
	mkdir -p $(DEPLOY_DIR)

clean:
	rm -f $(BUILD_DIR)/*.so
	rm -f $(DEPLOY_DIR)/*.so

int9: int9.c
	$(CC) $(OPTS) -nostdlib -o $(BUILD_DIR)/int9.o -c int9.c
	$(CC) $(OPTS) -shared -o $(BUILD_DIR)/int9.so $(BUILD_DIR)/int9.o
	rm $(BUILD_DIR)/int9.o
	file $(BUILD_DIR)/int9.so
	cp $(BUILD_DIR)/int9.so $(DEPLOY_DIR)
