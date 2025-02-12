# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Make include file that sets the build environment.  The external
# environment variables are defined by Xcode, allowing this build
# to be used within Xcode.
#
# Author: Tom Ball

J2OBJC_ROOT = ..

include ../make/common.mk
include ../make/j2objc_deps.mk
include ../java_deps/jars.mk

INCLUDE_DIR = $(BUILD_DIR)/include
SOURCE_BASE = src/main
JAVA_SRC_DIR = $(BUILD_DIR)/java
CLASSES_DIR = $(BUILD_DIR)/classes

JSR305_SRC_JAR = $(JAVA_DEPS_JAR_DIR)/$(JSR305_SOURCE_JAR)
JSR305_JAR_FULL = $(JAVA_DEPS_JAR_DIR)/$(JSR305_JAR)

OBJCFLAGS := $(CC_WARNINGS) $(DEBUGFLAGS)

# J2ObjC settings
J2OBJC = $(DIST_DIR)/j2objc -classpath $(JSR305_JAR_FULL) -d $(BUILD_DIR)
J2OBJCC = $(ARCH_BIN_DIR)/j2objcc -c $(OBJCFLAGS) -I$(GEN_OBJC_DIR)

vpath %.java $(JAVA_SRC_DIR)

ifdef CLANG_ENABLE_OBJC_ARC
J2OBJC := $(J2OBJC) -use-arc
OBJCFLAGS := $(OBJCFLAGS) -fobjc-arc -fobjc-arc-exceptions
endif
ifdef J2OBJC_USE_GC
J2OBJC := $(J2OBJC) -Xuse-gc
OBJCFLAGS := $(OBJCFLAGS) -fobjc-arc -fobjc-arc-exceptions
endif
