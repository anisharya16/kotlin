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

import org.jetbrains.kotlin.codegen.optimization.transformer.MethodTransformer
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode
import org.jetbrains.org.objectweb.asm.tree.IincInsnNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode
import org.jetbrains.org.objectweb.asm.tree.VarInsnNode
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame

fun matchStoresWithLoads(internalClassName: String, methodNode: MethodNode): Map<AbstractInsnNode, List<AbstractInsnNode>> {
    val result = HashMap<AbstractInsnNode, MutableList<AbstractInsnNode>>()

    val frames = analyzeStoreLoadChains(internalClassName, methodNode)
    val insns = methodNode.instructions.toArray()

    insnLoop@ for (i in frames.indices) {
        val frame = frames[i] ?: continue

        val insn = insns[i]
        val varIndex = when (insn) {
            is VarInsnNode -> insn.`var`
            is IincInsnNode -> insn.`var`
            else -> continue@insnLoop
        }

        val loadedValue = frame.getLocal(varIndex) as? StoredValue ?: continue
        for (storeInsn in loadedValue.stores) {
            result.getOrPut(storeInsn) { SmartList() }.add(insn)
        }
    }

    return result
}

fun analyzeStoreLoadChains(internalClassName: String, methodNode: MethodNode): Array<out Frame<BasicValue>?> =
        MethodTransformer.analyze(internalClassName, methodNode, StoreLoadChainInterpreter())

class StoreLoadChainInterpreter : OptimizationBasicInterpreter() {
    override fun merge(v: BasicValue, w: BasicValue): BasicValue {
        val default = super.merge(v, w)
        val type = default.type
        return when {
            v is StoredValue && w is StoredValue -> if (v == w) v else StoredValue(type, v.stores + w.stores)
            v is StoredValue -> v.withType(type)
            w is StoredValue -> w.withType(type)
            else -> default
        }
    }

    override fun copyOperation(insn: AbstractInsnNode, value: BasicValue): BasicValue =
            if (insn.isStoreOperation())
                StoredValue(value.type, setOf(insn))
            else
                super.copyOperation(insn, value)

    override fun unaryOperation(insn: AbstractInsnNode, value: BasicValue): BasicValue? =
            if (insn.opcode == Opcodes.IINC)
                StoredValue(value.type, setOf(insn))
            else
                super.unaryOperation(insn, value)
}

class StoredValue(type: Type, val stores: Set<AbstractInsnNode>): StrictBasicValue(type) {
    fun withType(newType: Type?) =
            if (newType == null || newType == type) this else StoredValue(newType, stores)

    override fun equals(other: Any?): Boolean =
            other === this ||
            other is StoredValue && other.type == type && other.stores == stores

    override fun hashCode(): Int =
            type.hashCode() * 31 + stores.hashCode()
}