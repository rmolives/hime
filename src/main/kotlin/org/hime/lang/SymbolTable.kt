package org.hime.lang

import org.hime.cast
import org.hime.lang.exception.HimeRuntimeException
import org.hime.parse.Token
import org.hime.toToken

/**
 * @param env    从属环境
 * @param table  一系列绑定
 * @param father 所属的父SymbolTable
 */
class SymbolTable(
    private var env: Env,
    var table: MutableMap<String, Token>,
    private var father: SymbolTable? = null
) {
    /**
     * 删除绑定
     * @param key binding key
     */
    fun remove(key: String) {
        var data = this
        while (data.father != null && !data.table.containsKey(key))
            data = data.father!!
        data.table.remove(key)
    }

    /**
     * 更改绑定
     * @param key   binding key
     * @param value binding value
     */
    fun set(key: String, value: Token) {
        var data = this
        while (data.father != null && !data.table.containsKey(key))
            data = data.father!!
        data.table[key] = value
    }

    /**
     * 建立绑定
     * @param key   binding key
     * @param value binding value
     */
    fun put(key: String, value: Token) {
        table[key] = value
    }

    /**
     * 判断是否包含key
     * @param key binding key
     */
    fun contains(key: String): Boolean {
        if (env.types.containsKey(key))
            return true
        var data = this
        while (data.father != null && !data.table.containsKey(key))
            data = data.father!!
        return data.table.containsKey(key)
    }

    /**
     * 获取key对应的值
     * @param key binding key
     * @return    binding value
     */
    fun get(key: String): Token {
        var data = this
        while (data.father != null && !data.table.containsKey(key))
            data = data.father!!
        return data.table[key] ?: if (env.types.containsKey(key)) env.types[key]?.toToken(env)
            ?: throw HimeRuntimeException("$key does not exist.") else throw HimeRuntimeException("$key does not exist.")
    }

    fun getFunction(env: Env, key: String): HimeFunctionScheduler {
        var data = this
        while (data.father != null && !data.table.containsKey(key))
            data = data.father!!
        if (data.table.containsKey(key)) {
            val token = data.table[key] ?: throw HimeRuntimeException("$key does not exist.")
            himeAssertRuntime(env.isType(token, env.getType("function"))) { "$token is not function." }
            return cast<HimeFunctionScheduler>(token.value)
        }
        return HimeFunctionScheduler(env)
    }

    /**
     * 建立子SymbolTable
     * @return child
     */
    fun createChild(): SymbolTable {
        return SymbolTable(env, HashMap(), this)
    }
}