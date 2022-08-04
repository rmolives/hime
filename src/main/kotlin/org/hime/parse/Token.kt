package org.hime.parse

import org.hime.lang.FuncType
import org.hime.lang.HimeFunction
import org.hime.lang.SymbolTable
import org.hime.lang.eval
import org.hime.lang.Env
import org.hime.lang.HimeType
import org.hime.toToken
import java.util.*

/**
 * @param type  类型
 * @param value 内容
 */
class Token(val type: HimeType, val value: Any) {
    override fun equals(other: Any?): Boolean {
        return other.hashCode() == this.hashCode()
    }

    override fun hashCode(): Int {
        return Objects.hash(type, value)
    }

    override fun toString(): String {
        return this.value.toString()
    }
}

/**
 * 建立过程
 * @param parameters 形式参数
 * @param paramTypes 类型
 * @param asts      一系列组合式
 * @param symbol    符号表
 * @return          返回Hime_HimeFunction
 */
fun structureHimeFunction(
    env: Env,
    parameters: List<String>,
    paramTypes: List<HimeType>,
    asts: List<ASTNode>,
    symbol: SymbolTable
): Token {
    return Token(
        env.getType("function"),
        HimeFunction(env, FuncType.USER_DEFINED, fun(args: List<Token>): Token {
            // 判断参数的数量
            assert(args.size >= parameters.size)
            // 新建执行的新环境（继承）
            val newSymbol = symbol.createChild()
            for (i in parameters.indices)
                newSymbol.put(parameters[i], args[i])
            var result = env.himeNil
            for (astNode in asts)
                result = eval(env, astNode.copy(), newSymbol)
            return result
        }, paramTypes, false)
    )
}

/**
 * 建立变长
 * @param parameters 形式参数
 * @param paramTypes 类型
 * @param asts      一系列组合式
 * @param symbol    符号表
 * @return          返回Hime_HimeFunction
 */
fun variableHimeFunction(
    env: Env,
    parameters: List<String>,
    paramTypes: List<HimeType>,
    asts: List<ASTNode>,
    symbol: SymbolTable
): Token {
    return Token(
        env.getType("function"),
        HimeFunction(env, FuncType.USER_DEFINED, fun(args: List<Token>): Token {
            // 判断参数的数量
            assert(args.size >= parameters.size - 1)
            // 新建执行的新环境（继承）
            val newSymbol = symbol.createChild()
            for (i in 0 until parameters.size - 1)
                newSymbol.put(parameters[i], args[i])
            val variableArgs = ArrayList<Token>()
            for (i in parameters.size - 1 until args.size)
                variableArgs.add(args[i])
            newSymbol.put(parameters[parameters.size - 1], variableArgs.toToken(env))
            var result = env.himeNil
            for (astNode in asts)
                result = eval(env, astNode.copy(), newSymbol)
            return result
        }, paramTypes, true)
    )
}