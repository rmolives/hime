package ink.hime.core

import ink.hime.cast
import ink.hime.parse.ASTNode
import ink.hime.parse.AstType
import ink.hime.parse.Token
import ink.hime.parse.Type

/**
 * 求值器
 * @param ast    抽象语法树
 * @param symbol 符号表
 * @return       求值返回的值
 */
fun eval(ast: ASTNode, symbol: SymbolTable): Token {
    var temp = ast.tok
    while (true)
        temp = if (temp.type == Type.ID && symbol.contains(cast<String>(temp.value)))
            symbol.get(cast<String>(temp.value))
        else
            break
    ast.tok = temp
    if (ast.isEmpty() && ast.type != AstType.FUNCTION)
        return ast.tok
    // 如果为函数
    if (ast.tok.type == Type.FUNCTION) {
        ast.tok = cast<HimeFunction>(ast.tok.value).call(ast, symbol)
        ast.clear()
    }
    return ast.tok
}
