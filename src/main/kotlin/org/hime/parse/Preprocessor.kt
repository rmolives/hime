package org.hime.parse

/**
 * 预处理器（主要是把多于的空格、回车之类的删除掉）
 * @param s 代码
 * @return  处理完的代码
 */
fun preprocessor(s: String): String {
    val builder = StringBuilder()
    var index = 0
    // 删除掉空行
    val chars = s.replace(Regex("(\n|\r\n)$\\s*"), "").toCharArray()
    var i = 0
    while (i < chars.size) {
        val c = chars[i]
        // 跳过字符串
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
            // 跳过注释
        } else if (c == ';' || c == '\r') {
            while (i < chars.size - 1 && chars[++i] != '\n');
        } else if (c == '(') {
            ++index
            builder.append(c)
        } else if (c == ')') {
            --index
            builder.append(c)
            // 减少空白字符的数量
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