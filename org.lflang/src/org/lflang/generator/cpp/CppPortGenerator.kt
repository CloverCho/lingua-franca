/*************
 * Copyright (c) 2019-2021, TU Dresden.

 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:

 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.

 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ***************/

package org.lflang.generator.cpp

import org.lflang.ErrorReporter
import org.lflang.getWidth
import org.lflang.isMultiport
import org.lflang.lf.Input
import org.lflang.lf.Output
import org.lflang.lf.Port
import org.lflang.lf.Reactor

class CppPortGenerator(private val reactor: Reactor, private val errorReporter: ErrorReporter) {

    private fun generateDeclaration(port: Port): String {
        val portType = when (port) {
            is Input  -> "reactor::Input"
            is Output -> "reactor::Output"
            else      -> throw AssertionError()
        }

        return if (port.isMultiport) {
            val width = port.getValidWidth()
            val initializerLists = (0 until width).joinToString(", ") { """{"${port.name}_$it", this}""" }
            """std::array<$portType<${port.targetType}>, ${port.width}> ${port.name}{{$initializerLists}};"""
        } else {
            """$portType<${port.targetType}> ${port.name}{"${port.name}", this};"""
        }
    }

    /**
     * Calculate the width of a multiport.
     *
     * This reports an error on the receiving port if the width is not given as a literal integer.
     */
    fun Port.getValidWidth(): Int {
        if (width < 0) {
            errorReporter.reportError(
                this,
                "The C++ target only supports multiport widths specified as literal integer values for now"
            )
            // TODO Support parameterized widths
        }
        return width
    }

    fun generateDeclarations() =
        reactor.inputs.joinToString("\n", "// input ports\n", postfix = "\n") { generateDeclaration(it) } +
                reactor.outputs.joinToString("\n", "// output ports\n", postfix = "\n") { generateDeclaration(it) }
}