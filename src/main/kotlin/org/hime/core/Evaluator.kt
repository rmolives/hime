package org.hime.core

import org.hime.cast
import org.hime.lang.Env
import org.hime.parse.ASTNode
import org.hime.parse.AstType
import org.hime.parse.Token

/**
 * 求值器
 * @param ast    抽象语法树
 * @param symbol 符号表
 * @return       求值返回的值
 */
fun eval(env: Env, ast: ASTNode, symbol: SymbolTable): Token {
    var temp = ast.tok
    while (true)
        temp = if (env.isType(temp, env.getType("id")) && symbol.contains(cast<String>(temp.value)))
            symbol.get(cast<String>(temp.value))
        else
            break
    ast.tok = temp
    if (ast.isEmpty() && ast.type != AstType.FUNCTION)
        return ast.tok
    // 如果为函数
    if (env.isType(ast.tok, env.getType("function"))) {
        ast.tok = cast<HimeFunction>(ast.tok.value).call(ast, symbol)
        ast.clear()
    }
    return ast.tok
}
