package org.hime

fun generateRandomString(len: Int = 16): String{
    val alphanumerics = CharArray(26) { it -> (it + 97).toChar() }.toSet()
        .union(CharArray(9) { it -> (it + 48).toChar() }.toSet())
    return (0 until len).map {
        alphanumerics.toList().random()
    }.joinToString("")
}