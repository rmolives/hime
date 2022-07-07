package org.hime.parse

fun preprocessor(s: String): String {
    val builder = StringBuilder()
    var index = 0
    // Replace carriage returns and split the code into single lines.
    val chars = s.replace(Regex("(\n|\r\n)$\\s*"), "").toCharArray()
    var i = 0
    while (i < chars.size) {
        val c = chars[i]
        // Skip strings in parentheses.
        if (index >= 1 && c == '\"') {
            builder.append('\"')
            var j = i
            var skip = false
            while (true) {
                val peek = chars[++j]
                // Skip escape sequences in strings.
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
        // Skip single-line comments preceded by semicolons.
        } else if (c == ';' || c == '\r') {
            while (i < chars.size - 1 && chars[++i] != '\n');
        // Once getting into a pair of parentheses, index will increment.
        } else if (c == '(') {
            ++index
            builder.append(c)
        // Once getting out of a pair of parentheses, index will decrement.
        } else if (c == ')') {
            --index
            builder.append(c)
        // Lessen the number of blank characters.
        } else if (index >= 1 && (c == ' ' || c == '\n' || c == '\t')) {
            var j = i + 1
            while (chars[j] == ' ' || chars[j] == '\n' || chars[j] == '\t') {
                ++i
                ++j
            }
            builder.append(' ')
        } else
            builder.append(c)
        ++i
    }
    return builder.toString()
}