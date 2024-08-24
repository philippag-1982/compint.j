BUILD_DIR=../../../build/native
ifeq ($(OS),Windows_NT)
	JDK_MD_INCLUDE_DIR=win32
else
	JDK_MD_INCLUDE_DIR=linux
endif
OPTS=-O3 -Wall -Werror -std=c17 -D_JNI_IMPLEMENTATION_ -I $(JAVA_HOME)/include -I $(JAVA_HOME)/include/$(JDK_MD_INCLUDE_DIR) -fPIC
CC=gcc

all: init multiply-core-9 multiply-core-16

init:
	mkdir -p $(BUILD_DIR)
	
clean:
	rm -rf $(BUILD_DIR)

multiply-core-9: multiply-core-9.c
	$(CC) $(OPTS) -nostdlib -o $(BUILD_DIR)/multiply-core-9.o -c multiply-core-9.c
	$(CC) $(OPTS) -nostdlib -shared -o $(BUILD_DIR)/multiply-core-9.so $(BUILD_DIR)/multiply-core-9.o
	rm $(BUILD_DIR)/multiply-core-9.o

multiply-core-16: multiply-core-16.c
	$(CC) $(OPTS) -nostdlib -o $(BUILD_DIR)/multiply-core-16.o -c multiply-core-16.c
	$(CC) $(OPTS) -shared -o $(BUILD_DIR)/multiply-core-16.so $(BUILD_DIR)/multiply-core-16.o
	rm $(BUILD_DIR)/multiply-core-16.o
