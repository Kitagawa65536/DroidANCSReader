package com.bridgeip.ancsreader.util

fun ByteArray.readUInt8(offset: Int): Int = getOrNull(offset)?.toInt()?.and(0xFF) ?: 0

fun ByteArray.readUInt16Le(offset: Int): Int {
    val first = readUInt8(offset)
    val second = readUInt8(offset + 1)
    return first or (second shl 8)
}

fun ByteArray.readUInt32Le(offset: Int): Long {
    val b0 = readUInt8(offset).toLong()
    val b1 = readUInt8(offset + 1).toLong()
    val b2 = readUInt8(offset + 2).toLong()
    val b3 = readUInt8(offset + 3).toLong()
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}

fun Int.toLeByteArray(): ByteArray = byteArrayOf(
    (this and 0xFF).toByte(),
    ((this shr 8) and 0xFF).toByte(),
    ((this shr 16) and 0xFF).toByte(),
    ((this shr 24) and 0xFF).toByte(),
)

fun Int.toLeShortByteArray(): ByteArray = byteArrayOf(
    (this and 0xFF).toByte(),
    ((this shr 8) and 0xFF).toByte(),
)

