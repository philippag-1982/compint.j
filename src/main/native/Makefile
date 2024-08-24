BUILD_DIR=../../../build/native
JDK_DIR=/opt/java64/jdk-22
OPTS=-O3 -Wall -Werror -std=c17 -D_JNI_IMPLEMENTATION_ -I $(JDK_DIR)/include -I $(JDK_DIR)/include/linux -fPIC
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
