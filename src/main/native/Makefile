BUILD_DIR=../../../build/native
ifeq ($(OS),Windows_NT)
	JDK_MD_INCLUDE_DIR=win32
else
	JDK_MD_INCLUDE_DIR=linux
endif
OPTS=-O3 -Wall -Werror -std=c17 -D_JNI_IMPLEMENTATION_ -I $(JAVA_HOME)/include -I $(JAVA_HOME)/include/$(JDK_MD_INCLUDE_DIR) -fPIC
CC=gcc

all: init multiply-core

init:
	mkdir -p $(BUILD_DIR)
	
clean:
	rm -rf $(BUILD_DIR)

multiply-core: multiply-core.c
	$(CC) $(OPTS) -o $(BUILD_DIR)/multiply-core.o -c multiply-core.c
	$(CC) $(OPTS) -shared -o $(BUILD_DIR)/multiply-core.so $(BUILD_DIR)/multiply-core.o
	rm $(BUILD_DIR)/multiply-core.o
