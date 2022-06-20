package org.hime.parse

fun preprocessor(s: String): String {
    val builder = StringBuilder()
    var index = 0
    val chars = s.replace("(?m)^\\s*$(\\n|\\r\\n)".toRegex(), "").toCharArray()
    var i = 0
    while (i < chars.size) {
        val c = chars[i]
        if (index >= 1 && c == '\"') {
            builder.append('\"')
            var j = i
            var skip = false
            while (true) {
                val peek = chars[++j]
                if ((chars[j + 1] == '\\' || chars[j + 1] == '\"') && peek == '\\') {
                    skip = !skip
                } else if (peek == '\"') {
                    skip = if (!skip) {
                        break
                    } else
                        false
                }
                builder.append(peek)
                ++i
            }
            builder.append("\"")
            ++i
        } else if (c == ';' || c == '\r') {
            while (i < chars.size - 1 && chars[++i] != '\n');
        } else if (c == '(') {
            ++index
            builder.append(c)
        } else if (c == ')') {
            --index
            builder.append(c)
        } else if (index >= 1 && (c == ' ' || c == '\n' || c == '\t')) {
            var j = i + 1
            while (chars[j] == ' ' || chars[j] == '\n' || chars[j] == '\t') {
                ++i
                ++j
            }
            builder.append(' ')
        } else builder.append(c)
        ++i
    }
    return builder.toString()
}