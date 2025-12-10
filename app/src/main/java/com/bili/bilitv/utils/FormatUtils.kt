package com.bili.bilitv.utils

object FormatUtils {
    fun formatCount(countStr: String): String {
        val count = countStr.toLongOrNull() ?: return countStr
        return when {
            count >= 100000000 -> String.format("%.1f亿", count / 100000000f)
            count >= 10000 -> String.format("%.1f万", count / 10000f)
            else -> countStr
        }
    }

    fun formatOnline(online: Int): String {
        return if (online >= 10000) {
            String.format("%.1f万", online / 10000f)
        } else {
            online.toString()
        }
    }
}
