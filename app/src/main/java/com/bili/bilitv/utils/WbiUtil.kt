package com.bili.bilitv.utils

import java.security.MessageDigest
import java.net.URLEncoder
import kotlin.collections.LinkedHashMap

object WbiUtil {
    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52
    )

    fun getMixinKey(imgKey: String, subKey: String): String {
        val s = imgKey + subKey
        val sb = StringBuilder()
        for (i in 0 until 32) {
            if (i < MIXIN_KEY_ENC_TAB.size && MIXIN_KEY_ENC_TAB[i] < s.length) {
                sb.append(s[MIXIN_KEY_ENC_TAB[i]])
            }
        }
        return sb.toString()
    }

    fun sign(params: Map<String, String>, imgKey: String, subKey: String): Map<String, String> {
        val mixinKey = getMixinKey(imgKey, subKey)
        val currTime = System.currentTimeMillis() / 1000
        
        // Copy params and add wts
        val newParams = params.toMutableMap()
        newParams["wts"] = currTime.toString()
        
        // Sort by key
        val sortedKeys = newParams.keys.sorted()
        val queryBuilder = StringBuilder()
        
        for (key in sortedKeys) {
            if (queryBuilder.isNotEmpty()) {
                queryBuilder.append("&")
            }
            val value = newParams[key] ?: ""
            // Encode key and value. Note: URLEncoder uses application/x-www-form-urlencoded which encodes space as +
            // Bilibili requires RFC 3986 (space as %20). However, commonly standard params don't have spaces.
            // If they do, we need to replace + with %20.
            // Also reference implementation handles specific character exclusions.
            queryBuilder.append(encodeURIComponent(key))
            queryBuilder.append("=")
            queryBuilder.append(encodeURIComponent(value))
        }
        
        val queryStr = queryBuilder.toString()
        val strToHash = queryStr + mixinKey
        val wRid = md5(strToHash)
        
        newParams["w_rid"] = wRid
        return newParams
    }
    
    fun encodeURIComponent(s: String): String {
        return URLEncoder.encode(s, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%2A", "*")
    }

    private fun md5(s: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(s.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

