// FILE: simple.kt
fun test1() {
    println("Hello, world!")
}

// @SimpleKt.class:
// 0 ASTORE
// 0 ALOAD
// 0 SWAP

// FILE: withSwap.kt
fun foo() = "Hello, world!"

fun test2() {
    println(foo())
}

// @WithSwapKt.class:
// 0 ASTORE
// 0 ALOAD
// 1 SWAP