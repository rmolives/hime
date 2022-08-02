package org.hime

fun generateRandomString(len: Int = 16): String{
    return (0 until len).map {
        CharArray(26) { (it + 97).toChar() }.toSet()
            .union(CharArray(9) { (it + 48).toChar() }.toSet()).toList().random()
    }.joinToString("")
}