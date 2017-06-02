/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.codegen.optimization.common

import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode

class InsnMatcher(var current: AbstractInsnNode?)

class MatchFailedException : RuntimeException() {
    override fun fillInStackTrace(): Throwable = this
}

inline fun AbstractInsnNode.matchInsns(body: InsnMatcher.() -> Unit): Boolean {
    try {
        InsnMatcher(this).body()
    }
    catch (e: MatchFailedException) {
        return false
    }
    return true
}

fun InsnMatcher.fail(): Nothing {
    throw MatchFailedException()
}

fun InsnMatcher.next(): AbstractInsnNode? {
    current = (current ?: fail()).next
    return current
}

fun InsnMatcher.previous(): AbstractInsnNode? {
    current = (current ?: fail()).previous
    return current
}

inline fun InsnMatcher.expectInsn(predicate: AbstractInsnNode.() -> Boolean): AbstractInsnNode {
    val insn = current ?: fail()
    if (!insn.predicate()) fail()
    current = insn.next
    return insn
}

inline fun InsnMatcher.tryExpectInsn(predicate: AbstractInsnNode.() -> Boolean): AbstractInsnNode? {
    val insn = current ?: fail()
    if (!insn.predicate()) return null
    current = insn.next
    return insn
}

inline fun <reified T : AbstractInsnNode> InsnMatcher.expect(): T {
    val insn = current ?: fail()
    if (insn !is T) fail()
    current = insn.next
    return insn
}

inline fun <reified T : AbstractInsnNode> InsnMatcher.expect(predicate: T.() -> Boolean): T {
    val insn = current ?: fail()
    if (insn !is T || !insn.predicate()) fail()
    current = insn.next
    return insn
}

inline fun <reified T : AbstractInsnNode> InsnMatcher.expect(opcode: Int, predicate: T.() -> Boolean): T {
    val insn = current ?: fail()
    if (insn.opcode != opcode || insn !is T || !insn.predicate()) fail()
    current = insn.next
    return insn
}

inline fun <reified T : AbstractInsnNode> InsnMatcher.tryExpect(): T? {
    val insn = current ?: fail()
    if (insn !is T) return null
    current = insn.next
    return insn
}

inline fun <reified T : AbstractInsnNode> InsnMatcher.tryExpect(opcode: Int, predicate: T.() -> Boolean): T? {
    val insn = current ?: fail()
    if (insn.opcode != opcode || insn !is T || !insn.predicate()) return null
    current = insn.next
    return insn
}

inline fun <reified T : AbstractInsnNode> InsnMatcher.tryExpect(predicate: T.() -> Boolean): T? {
    val insn = current ?: fail()
    if (insn !is T || !insn.predicate()) return null
    current = insn.next
    return insn
}

fun InsnMatcher.opcode(opcode: Int): AbstractInsnNode = expectInsn { this.opcode == opcode }
