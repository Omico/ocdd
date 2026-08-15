cmake_minimum_required(VERSION 3.22.1)

project(ocdd_io_posix LANGUAGES C)

add_library(ocdd_io_posix SHARED posix_attributes.c)

target_compile_features(ocdd_io_posix PRIVATE c_std_11)
target_compile_options(ocdd_io_posix PRIVATE -Wall -Wextra -Werror)
