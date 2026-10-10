package security

/** Strict numeric IP/CIDR matching. No DNS, wildcard, port, or zone-id interpretation. */
class IpNetwork private constructor(private val bytes: List<Int>, private val prefix: Int) {
    fun contains(address: String): Boolean {
        val candidate = parseAddress(address) ?: return false
        if (candidate.size != bytes.size) return false
        return bytes.indices.all { i ->
            val bits = (prefix - i * 8).coerceIn(0, 8)
            val mask = (0xff shl (8 - bits)) and 0xff
            (candidate[i] and mask) == (bytes[i] and mask)
        }
    }

    companion object {
        fun parse(raw: String): IpNetwork {
            val parts = raw.trim().split('/')
            require(parts.size in 1..2) { "Invalid IP/CIDR" }
            val address = parseAddress(parts[0]) ?: error("Invalid numeric IP address")
            val prefix = if (parts.size == 1) address.size * 8 else {
                require(parts[1].isNotEmpty() && parts[1].all { it in '0'..'9' }) { "Invalid CIDR prefix" }
                parts[1].toIntOrNull() ?: error("Invalid CIDR prefix")
            }
            require(prefix in 0..address.size * 8) { "CIDR prefix out of range" }
            return IpNetwork(address, prefix)
        }

        private fun ipv4(raw: String): List<Int>? {
            val parts = raw.split('.')
            if (parts.size != 4) return null
            return parts.map {
                if (it.isEmpty() || it.length > 3 || (it.length > 1 && it[0] == '0') ||
                    !it.all { c -> c in '0'..'9' }) return null
                it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return null
            }
        }

        private fun parseAddress(raw: String): List<Int>? {
            if (':' !in raw) return ipv4(raw)
            if (raw.any { it !in "0123456789abcdefABCDEF:." }) return null
            var value = raw
            if ('.' in value) {
                val tail = ipv4(value.substringAfterLast(':')) ?: return null
                value = value.substringBeforeLast(':') + ":" +
                    ((tail[0] shl 8) or tail[1]).toString(16) + ":" +
                    ((tail[2] shl 8) or tail[3]).toString(16)
            }
            val halves = value.split("::")
            if (halves.size > 2) return null
            fun words(s: String): List<Int>? {
                if (s.isEmpty()) return emptyList()
                return s.split(':').map {
                    if (it.length !in 1..4) return null
                    it.toIntOrNull(16) ?: return null
                }
            }
            val left = words(halves[0]) ?: return null
            val right = if (halves.size == 2) words(halves[1]) ?: return null else emptyList()
            val missing = 8 - left.size - right.size
            if (halves.size == 1 && missing != 0 || halves.size == 2 && missing < 1) return null
            return (left + List(missing) { 0 } + right).flatMap { listOf(it shr 8, it and 255) }
        }
    }
}
