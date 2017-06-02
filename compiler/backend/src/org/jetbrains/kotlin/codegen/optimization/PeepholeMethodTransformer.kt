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

package org.jetbrains.kotlin.codegen.optimization

import org.jetbrains.kotlin.codegen.optimization.common.*
import org.jetbrains.kotlin.codegen.optimization.transformer.MethodTransformer
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.tree.*

class PeepholeMethodTransformer : MethodTransformer() {
    private class PeepholerContext(internalClassName: String, val methodNode: MethodNode) {
        val storesToLoads = matchStoresWithLoads(internalClassName, methodNode)
    }

    private interface Peepholer {
        fun tryRewrite(instructions: InsnList, insn: AbstractInsnNode, context: PeepholerContext): AbstractInsnNode?
    }

    private val peepholers = listOf(IfNotPeepholer, PrintPeepholer)

    override fun transform(internalClassName: String, methodNode: MethodNode) {
        val instructions = methodNode.instructions
        val context = PeepholerContext(internalClassName, methodNode)

        do {
            var changes = false
            var insn: AbstractInsnNode? = instructions.first

            while (insn != null) {
                var rewrittenNext: AbstractInsnNode? = null
                for (peepholer in peepholers) {
                    rewrittenNext = peepholer.tryRewrite(instructions, insn, context) ?: continue
                    changes = true
                    break
                }
                insn = rewrittenNext ?: insn.next
            }
        } while (changes)
    }


    private object IfNotPeepholer : Peepholer {
        override fun tryRewrite(instructions: InsnList, insn: AbstractInsnNode, context: PeepholerContext): AbstractInsnNode? {
            insn.matchInsns {
                val iConst1Insn = opcode(Opcodes.ICONST_1)
                val iXorInsn = opcode(Opcodes.IXOR)
                val ifInsn = expect<JumpInsnNode> { opcode == Opcodes.IFEQ || opcode == Opcodes.IFNE }

                instructions.run {
                    remove(iConst1Insn)
                    remove(iXorInsn)
                    val invertedOpcode = Opcodes.IFNE + Opcodes.IFEQ - ifInsn.opcode
                    val newIfInsn = JumpInsnNode(invertedOpcode, ifInsn.label)
                    set(ifInsn, newIfInsn)
                    return newIfInsn
                }
            }

            return null
        }
    }

    private object PrintPeepholer : Peepholer {
        override fun tryRewrite(instructions: InsnList, insn: AbstractInsnNode, context: PeepholerContext): AbstractInsnNode? {
            insn.matchInsns {
                val storeInsn = expect<VarInsnNode> { isStoreOperation() }
                tryExpectInsn { opcode == Opcodes.NOP }
                tryExpect<LabelNode>()
                val getSystemOutInsn = expect<FieldInsnNode>(Opcodes.GETSTATIC) {
                    owner == "java/lang/System" && (name == "out" || name == "err")
                }
                val loadInsn = expect<VarInsnNode> { isLoadOperation() && `var` == storeInsn.`var` }

                if (context.storesToLoads[storeInsn]?.size != 1) return null

                val beforeStore = storeInsn.previous ?: return null
                instructions.run {
                    remove(storeInsn)
                    remove(loadInsn)
                    if (isSafeToSwapInPlace(beforeStore)) {
                        remove(getSystemOutInsn)
                        insertBefore(beforeStore, getSystemOutInsn)
                    }
                    else {
                        insert(getSystemOutInsn, InsnNode(Opcodes.SWAP))
                    }
                }

                return getSystemOutInsn
            }

            return null
        }

        private fun isSafeToSwapInPlace(beforeStore: AbstractInsnNode): Boolean =
                beforeStore.opcode in Opcodes.ACONST_NULL .. Opcodes.ALOAD

    }
}