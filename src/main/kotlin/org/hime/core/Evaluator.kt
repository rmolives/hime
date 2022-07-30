package org.hime.core

import org.hime.cast
import org.hime.lang.getType
import org.hime.lang.isType
import org.hime.parse.ASTNode
import org.hime.parse.AstType
import org.hime.parse.Token

/**
 * 求值器
 * @param ast    抽象语法树
 * @param symbol 符号表
 * @return       求值返回的值
 */
fun eval(ast: ASTNode, symbol: SymbolTable): Token {
    var temp = ast.tok
    while (true)
        temp = if (isType(temp, getType("id")) && symbol.contains(cast<String>(temp.value)))
            symbol.get(cast<String>(temp.value))
        else
            break
    ast.tok = temp
    if (ast.isEmpty() && ast.type != AstType.FUNCTION)
        return ast.tok
    // 如果为函数
    if (isType(ast.tok, getType("function"))) {
        ast.tok = cast<HimeFunction>(ast.tok.value).call(ast, symbol)
        ast.clear()
    }
    return ast.tok
}
