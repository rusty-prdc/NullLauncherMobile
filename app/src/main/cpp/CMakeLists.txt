cmake_minimum_required(VERSION 3.22)
project(nulllauncher C)

add_library(
        native-bridge
        SHARED
        native_bridge.c)

find_library(log-lib log)

target_link_libraries(
        native-bridge
        ${log-lib}
        dl)