BUILD_DIR=../../../build/native
ifeq ($(OS),Windows_NT)
	JNI_MD_INCLUDE_DIR=win32
else
	JNI_MD_INCLUDE_DIR=linux
endif
OPTS_ASSERT=
#OPTS_ASSERT=-D_USE_ASSERT
OPTS=$(OPTS_ASSERT) -O3 -Wall -Werror -pedantic -std=c17 -D_JNI_IMPLEMENTATION_ -I $(JAVA_HOME)/include -I $(JAVA_HOME)/include/$(JNI_MD_INCLUDE_DIR) -fPIC
CC=gcc

all: init int-9

init:
	mkdir -p $(BUILD_DIR)
	
clean:
	rm -rf $(BUILD_DIR)

int-9: int-9.c
	$(CC) $(OPTS) -nostdlib -o $(BUILD_DIR)/int-9.o -c int-9.c
	$(CC) $(OPTS) -shared -o $(BUILD_DIR)/int-9.so $(BUILD_DIR)/int-9.o
	rm $(BUILD_DIR)/int-9.o
	file $(BUILD_DIR)/int-9.so
