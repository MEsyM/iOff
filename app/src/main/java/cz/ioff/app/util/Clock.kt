package cz.ioff.app.util

fun interface Clock { fun now(): Long }
object SystemClock : Clock { override fun now() = System.currentTimeMillis() }
