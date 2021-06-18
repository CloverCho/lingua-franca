/*
 * Copyright (c) 2021, TU Dresden.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.lflang.generator.rust

import org.lflang.generator.PrependOperator
import org.lflang.generator.rust.RustEmitter.rsLibPath
import org.lflang.joinWithCommas
import org.lflang.withDQuotes

/**
 * Generates Rust code
 */
object RustEmitter {
    const val rsLibPath = "reactorlib"

    fun generateFiles(fileConfig: RustFileConfig, gen: GenerationInfo) {

        fileConfig.emit("Cargo.toml") { makeCargoFile(gen) }
        fileConfig.emit("src/bin/main.rs") { makeMainFile(gen) }
        fileConfig.emit("src/lib.rs") { makeMainFile(gen) }
        for (reactor in gen.reactors) {
            fileConfig.emit("src/${gen.crate.name}/${reactor.modName}.rs") {
                makeReactorModule(reactor)
            }
        }

    }

    private fun Emitter.makeReactorModule(reactor: ReactorInfo) {
        val out = this
        with(reactor) {
            with(ReactorComponentEmitter) {
                with(PrependOperator) {
                    out += """
                |
                |struct $structName {
                |    // TODO state vars
                |}
                |
                |impl $structName {
                |
                |   // todo reaction worker functions
                |
                |}
                |
                |struct $dispatcherName {
                |    _impl: $structName,
${"             |    "..otherComponents.joinToString(",\n") { it.toStructField() }}
                |}
                |
                |
                |reaction_ids!(
                |  ${reactions.joinToString(", ", "enum $reactionIdName {", "}") { it.rustId }}
                | );
                |
                |impl $rsLibPath::ReactorDispatcher for $dispatcherName {
                |    type ReactionId = $reactionIdName;
                |    type Wrapped = $structName;
                |    type Params = (${ctorParamTypes.joinWithCommas()});
                |
                |
                |    fn assemble(_params: Self::Params) -> Self {
                |        Self {
                |            _impl: RandomSource,
${"             |            "..otherComponents.joinToString(",\n") { it.toFieldInitializer() }}
                |        }
                |    }
                |
                |    fn react(&mut self, ctx: &mut $rsLibPath::LogicalCtx, rid: Self::ReactionId) {
                |        match rid {
${"             |            "..reactionWrappers(reactor)}
                |        }
                |    }
                |
                |
                |}
        """.trimMargin()
                }
            }
        }
    }

    private fun reactionWrappers(reactor: ReactorInfo): String {

        fun joinDependencies(n: ReactionInfo): String =
            n.depends.joinToString(", ") { with(ReactorComponentEmitter) { it.toBorrow() } }

        return reactor.reactions.joinToString { n: ReactionInfo ->
            """
                ${reactor.reactionIdName}::${n.rustId} => {
                    self._impl.${n.workerId}(ctx, ${joinDependencies(n)})
                }
            """
        }
    }

    private fun Emitter.makeMainFile(gen: GenerationInfo) {
        this += """
            |fn main() {
            |
            |
            |}
        """.trimMargin()
    }

    private fun Emitter.makeCargoFile(gen: GenerationInfo) {
        val (crate, runtime) = gen
        this += """
            |[package]
            |name = "${crate.name}"
            |version = "${crate.version}"
            |authors = [${crate.authors.joinToString(", ") { it.withDQuotes() }}]
            |edition = "2018"

            |# See more keys and their definitions at https://doc.rust-lang.org/cargo/reference/manifest.html

            |[dependencies.reactor-rust]
            |path = "${runtime.local_crate_path}"
        """.trimMargin()
    }


}


object ReactorComponentEmitter {


    fun ReactorComponent.toBorrow() = when (this) {
        is PortData   ->
            if (isInput) "&self.$name"
            else "&mut self.$name"
        is ActionData -> "&self.$name"
    }

    fun ReactorComponent.toType() = when (this) {
        is ActionData ->
            if (isLogical) "$rsLibPath::LogicalAction"
            else "$rsLibPath::PhysicalAction"
        is PortData   ->
            if (isInput) "$rsLibPath::InputPort<$dataType>"
            else "$rsLibPath::OutputPort<$dataType>"
    }

    fun ReactorComponent.toFieldInitializer() = when (this) {
        is ActionData -> toType() + " (None, ${name.withDQuotes()})"
        else          -> "Default::default()"
    }

    fun ReactorComponent.toStructField() =
        "$name: ${toType()}"

}
