package example.com.utils

import kotlin.random.Random

object RandomString {
    fun generateRandomString(length: Int): String {
        val charRange = ('a'..'z') + ('0'..'9')
        val random = Random.Default
        return (1..length)
            .map { charRange.random(random) }
            .joinToString("")
    }
}