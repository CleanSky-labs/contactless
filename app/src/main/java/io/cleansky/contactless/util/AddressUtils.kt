package io.cleansky.contactless.util

object AddressUtils {
    fun isValidEvmAddress(address: String): Boolean {
        return address.startsWith("0x") &&
            address.length == 42 &&
            address.substring(2).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }
}
