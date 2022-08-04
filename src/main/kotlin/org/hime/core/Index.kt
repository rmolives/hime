package org.hime.core

val module = mutableMapOf(
    "util.file" to ::initFile,
    "util.time" to ::initTime,
    "util.table" to ::initTable,
    "util.thread" to ::initThread,
    "util.regex" to ::initRegex
)
