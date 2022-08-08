package org.hime.lang

import org.hime.cast
import org.hime.lang.exception.HimeAmbiguousCallException
import org.hime.parse.AstNode
import org.hime.parse.AstType
import org.hime.parse.Token

/**
 * 求值器
 * @param env    环境
 * @param ast    抽象语法树
 * @param symbol 符号表
 * @return       求值返回的值
 */
fun eval(env: Env, ast: AstNode, symbol: SymbolTable): Token {
    var lastId = Token(env.getType("id"), "<anonymous>")
    var temp = ast.tok
    while (true) {
        if (env.isType(temp, env.getType("id")) && symbol.contains(cast<String>(temp.value))) {
            lastId = temp
            temp = symbol.get(cast<String>(temp.value))
        } else break
    }
    ast.tok = temp
    if (ast.isEmpty() && ast.type != AstType.FUNCTION)
        return ast.tok
    // 如果为函数
    if (env.isType(ast.tok, env.getType("function"))) {
        try {
            ast.tok = cast<HimeFunctionScheduler>(ast.tok.value).call(ast, symbol)
        } catch (e: HimeAmbiguousCallException) {
            throw e.formatted(lastId.toString())
        }
        ast.clear()
    }
    return ast.tok
}
