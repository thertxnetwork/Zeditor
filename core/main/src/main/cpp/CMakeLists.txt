cmake_minimum_required(VERSION 3.22.1)

project("linuxlauncher")

# Create the library
add_library(${CMAKE_PROJECT_NAME} SHARED
        linux_launcher.cpp)

# Find and link the log library
find_library(log-lib log)

target_link_libraries(${CMAKE_PROJECT_NAME}
        ${log-lib})
