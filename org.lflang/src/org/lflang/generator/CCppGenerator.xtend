/* Generator for CCpp target. */

/*************
Copyright (c) 2019, The University of California at Berkeley.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
   this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
***************/

package org.lflang.generator

import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.xtext.generator.IFileSystemAccess2
import org.eclipse.xtext.generator.IGeneratorContext
import org.lflang.ErrorReporter
import org.lflang.FileConfig
import org.lflang.Target
import org.lflang.generator.c.CGenerator
import org.lflang.lf.ReactorDecl

import static extension org.lflang.ASTUtils.*

/** 
 * Generator for CCpp target. This class generates C++ code definining each reactor
 * class given in the input .lf file and imported .lf files. The generated code
 * has the following components:
 * 
 * * An alias for template structs for inputs, outputs, and typedefs for actions of each reactor class. These
 *   define the types of the variables that reactions use to access inputs and
 *   action values and to set output values.
 * 
 * * A typedef for a "self" struct for each reactor class. One instance of this
 *   struct will be created for each reactor instance. See below for details.
 * 
 * * A function definition for each reaction in each reactor class. These
 *   functions take an instance of the self struct as an argument.
 * 
 * * A constructor function for each reactor class. This is used to create
 *   a new instance of the reactor.
 * 
 * After these, the main generated function is `__initialize_trigger_objects()`.
 * This function creates the instances of reactors (using their constructors)
 * and makes connections between them.
 * 
 * A few other smaller functions are also generated.
 * 
 * ## Self Struct
 * 
 * The "self" struct has fields for each of the following:
 * 
 * * parameter: the field name and type match the parameter.
 * * state: the field name and type match the state.
 * * action: the field name prepends the action name with "__".
 *   A second field for the action is also created to house the trigger_t object.
 *   That second field prepends the action name with "___".
 * * output: the field name prepends the output name with "__".
 * * input:  the field name prepends the output name with "__".
 *   A second field for the input is also created to house the trigger_t object.
 *   That second field prepends the input name with "___".
 *
 * If, in addition, the reactor contains other reactors and reacts to their outputs,
 * then there will be a struct within the self struct for each such contained reactor.
 * The name of that self struct will be the name of the contained reactor prepended with "__".
 * That inside struct will contain pointers the outputs of the contained reactors
 * that are read together with pointers to booleans indicating whether those outputs are present.
 * 
 * If, in addition, the reactor has a reaction to shutdown, then there will be a pointer to
 * trigger_t object (see reactor.h) for the shutdown event and an action struct named
 * __shutdown on the self struct.
 * 
 * ## Reaction Functions
 * 
 * For each reaction in a reactor class, this generator will produce a C function
 * that expects a pointer to an instance of the "self" struct as an argument.
 * This function will contain verbatim the C code specified in the reaction, but
 * before that C code, the generator inserts a few lines of code that extract from the
 * self struct the variables that that code has declared it will use. For example, if
 * the reaction declares that it is triggered by or uses an input named "x" of type
 * int, the function will contain a line like this:
 * ```
 *     e_x_t* x = self->__x;
 * ```
 * where `r` is the full name of the reactor class and the struct type `r_x_t`
 * will be defined like this:
 * ```
 *     typedef struct {
 *         int value;
 *         bool is_present;
 *         int num_destinations;
 *     } r_x_t;
 * ```
 * The above assumes the type of `x` is `int`.
 * If the programmer fails to declare that it uses x, then the absence of the
 * above code will trigger a compile error when the verbatim code attempts to read `x`.
 *
 * ## Constructor
 * 
 * For each reactor class, this generator will create a constructor function named
 * `new_r`, where `r` is the reactor class name. This function will malloc and return
 * a pointer to an instance of the "self" struct.  This struct initially represents
 * an unconnected reactor. To establish connections between reactors, additional
 * information needs to be inserted (see below). The self struct is made visible
 * to the body of a reaction as a variable named "self".  The self struct contains the
 * following:
 * 
 * * Parameters: For each parameter `p` of the reactor, there will be a field `p`
 *   with the type and value of the parameter. So C code in the body of a reaction
 *   can access parameter values as `self->p`.
 * 
 * * State variables: For each state variable `s` of the reactor, there will be a field `s`
 *   with the type and value of the state variable. So C code in the body of a reaction
 *   can access state variables as as `self->s`.
 * 
 * The self struct also contains various fields that the user is not intended to
 * use. The names of these fields begin with at least two underscores. They are:
 * 
 * * Outputs: For each output named `out`, there will be a field `__out` that is
 *   a struct containing a value field whose type matches that of the output.
 *   The output value is stored here. That struct also has a field `is_present`
 *   that is a boolean indicating whether the output has been set.
 *   This field is reset to false at the start of every time
 *   step. There is also a field `num_destinations` whose value matches the
 *   number of downstream reactions that use this variable. This field must be
 *   set when connections are made or changed. It is used to initialize
 *   reference counts for dynamically allocated message payloads.
 * 
 * * Inputs: For each input named `in` of type T, there is a field named `__in`
 *   that is a pointer struct with a value field of type T. The struct pointed
 *   to also has an `is_present` field of type bool that indicates whether the
 *   input is present.
 * 
 * * Outputs of contained reactors: If a reactor reacts to outputs of a
 *   contained reactor `r`, then the self struct will contain a nested struct
 *   named `__r` that has fields pointing to those outputs. For example,
 *   if `r` has an output `out` of type T, then there will be field in `__r`
 *   named `out` that points to a struct containing a value field
 *   of type T and a field named `is_present` of type bool.
 * 
 * * Inputs of contained reactors: If a reactor sends to inputs of a
 *   contained reactor `r`, then the self struct will contain a nested struct
 *   named `__r` that has fields for storing the values provided to those
 *   inputs. For example, if R has an input `in` of type T, then there will
 *   be field in __R named `in` that is a struct with a value field
 *   of type T and a field named `is_present` of type bool.
 * 
 * * Actions: If the reactor has an action a (logical or physical), then there
 *   will be a field in the self struct named `__a` and another named `___a`.
 *   The type of the first is specific to the action and contains a `value`
 *   field with the type and value of the action (if it has a value). That
 *   struct also has a `has_value` field, an `is_present` field, and a
 *   `token` field (which is NULL if the action carries no value).
 *   The `___a` field is of type trigger_t.
 *   That struct contains various things, including an array of reactions
 *   sensitive to this trigger and a lf_token_t struct containing the value of
 *   the action, if it has a value.  See reactor.h in the C library for
 *   details.
 * 
 * * Reactions: Each reaction will have several fields in the self struct.
 *   Each of these has a name that begins with `___reaction_i`, where i is
 *   the number of the reaction, starting with 0. The fields are:
 *   * ___reaction_i: The struct that is put onto the reaction queue to
 *     execute the reaction (see reactor.h in the C library).
 *   * ___reaction_i_outputs_are_present: An array of pointers to the
 *     __out_is_present fields of each output `out` that may be set by
 *     this reaction. This array also includes pointers to the _is_present
 *     fields of inputs of contained reactors to which this reaction writes.
 *     This array is set up by the constructor.
 *   * ___reaction_i_num_outputs: The size of the previous array.
 *   * ___reaction_i_triggers: This is an array of arrays of pointers
 *     to trigger_t structs. The first level array has one entry for
 *     each effect of the reaction that is a port (actions are ignored).
 *     Each such entry is an array containing pointers to trigger structs for
 *     downstream inputs.
 *   * ___reaction_i_triggered_sizes: An array indicating the size of
 *     each array in ___reaction_i_triggers. The size of this array is
 *     the number of ports that are effects of this reaction.
 * 
 *  * Timers: For each timer t, there is are two fields in the self struct:
 *    * ___t_trigger: The trigger_t struct for this timer (see reactor.h).
 *    * ___t_trigger_reactions: An array of reactions (pointers to the
 *      reaction_t structs on this self struct) sensitive to this timer.
 *
 * * Triggers: For each Timer, Action, Input, and Output of a contained
 *   reactor that triggers reactions, there will be a trigger_t struct
 *   on the self struct with name `___t`, where t is the name of the trigger.
 * 
 * ## Connections Between Reactors
 * 
 * Establishing connections between reactors involves two steps.
 * First, each destination (e.g. an input port) must have pointers to
 * the source (the output port). As explained above, for an input named
 * `in`, the field `__in->value` is a pointer to the output data being read.
 * In addition, `__in->is_present` is a pointer to the corresponding
 * `out->is_present` field of the output reactor's self struct.
 *  
 * In addition, the `reaction_i` struct on the self struct has a `triggers`
 * field that records all the trigger_t structs for ports and reactions
 * that are triggered by the i-th reaction. The triggers field is
 * an array of arrays of pointers to trigger_t structs.
 * The length of the outer array is the number of output ports the
 * reaction effects plus the number of input ports of contained
 * reactors that it effects. Each inner array has a length equal to the
 * number final destinations of that output port or input port.
 * The reaction_i struct has an array triggered_sizes that indicates
 * the sizes of these inner arrays. The num_outputs field of the
 * reaction_i struct gives the length of the triggered_sizes and
 * (outer) triggers arrays.
 * 
 * ## Runtime Tables
 * 
 * This generator creates an populates the following tables used at run time.
 * These tables may have to be resized and adjusted when mutations occur.
 * 
 * * __is_present_fields: An array of pointers to booleans indicating whether an
 *   event is present. The __start_time_step() function in reactor_common.c uses
 *   this to mark every event absent at the start of a time step. The size of this
 *   table is contained in the variable __is_present_fields_size.
 * 
 * * __tokens_with_ref_count: An array of pointers to structs that point to lf_token_t
 *   objects, which carry non-primitive data types between reactors. This is used
 *   by the __start_time_step() function to decrement reference counts, if necessary,
 *   at the conclusion of a time step. Then the reference count reaches zero, the
 *   memory allocated for the lf_token_t object will be freed.  The size of this
 *   array is stored in the __tokens_with_ref_count_size variable.
 * 
 * * __shutdown_triggers: An array of pointers to trigger_t structs for shutdown
 *   reactions. The length of this table is in the __shutdown_triggers_size
 *   variable.
 * 
 * * __timer_triggers: An array of pointers to trigger_t structs for timers that
 *   need to be started when the program runs. The length of this table is in the
 *   __timer_triggers_size variable.
 * 
 * * __action_table: For a federated execution, each federate will have this table
 *   that maps port IDs to the corresponding trigger_t struct.
 * 
 * @author{Edward A. Lee <eal@berkeley.edu>}
 * @author{Marten Lohstroh <marten@berkeley.edu>}
 * @author{Mehrdad Niknami <mniknami@berkeley.edu>}
 * @author{Christian Menard <christian.menard@tu-dresden.de>}
 * @author{Matt Weber <matt.weber@berkeley.edu>}
 * @author{Soroush Bateni <soroush@utdallas.edu>}
 */
class CCppGenerator extends CGenerator {

    new(FileConfig fileConfig, ErrorReporter errorReporter) {
        super(fileConfig, errorReporter)
        // set defaults
        targetConfig.compiler = "g++"
        targetConfig.compilerFlags.add("-O2") // -Wall -Wconversion"
    }
	
    /** 
    * Template struct for ports with primitive types and
    * statically allocated arrays in Lingua Franca.
    * This template is defined as
    *     template <class T>
    *     struct template_input_output_port_struct {
    *         T value;
    *         bool is_present;
    *         int num_destinations;
    *     };
    *
    * @see xtext/org.lflang.linguafranca/src/lib/CCpp/ccpptarget.h
    */
	val template_port_type =  "template_port_instance_struct"

    /** 
    * Special template struct for ports with dynamically allocated
    * array types (a.k.a. token types) in Lingua Franca.
    * This template is defined as
    *     template <class T>
    *     struct template_input_output_port_struct {
    *         T value;
    *         bool is_present;
    *         int num_destinations;
    *         lf_token_t* token;
    *         int length;
    *     };
    *
    * @see xtext/org.lflang.linguafranca/src/lib/CCpp/ccpptarget.h
    */
	val template_port_type_with_token = "template_port_instance_with_token_struct"
    
   ////////////////////////////////////////////
    //// Public methods
    
    
    ////////////////////////////////////////////
    //// Protected methods
    
     /**
     * Generate the aliases for inputs, outputs, and struct type definitions for 
     * actions of the specified reactor in the specified federate.
     * @param reactor The parsed reactor data structure.
     * @param federate A federate name, or null to unconditionally generate.
     */
    override generateAuxiliaryStructs(
        ReactorDecl decl, FederateInstance federate
    ) {
        val reactor = decl.toDefinition
        // First, handle inputs.
        for (input : reactor.allInputs) {
            if (input.inferredType.isTokenType) {
                pr(input, code, '''
                    using «variableStructType(input, reactor)» = «template_port_type_with_token»<«input.inferredType.targetType»>;
                ''')
            }
            else
            {
                pr(input, code, '''
                    using «variableStructType(input, reactor)» = «template_port_type»<«input.inferredType.targetType»>;
                ''')            	
            }
            
        }
        // Next, handle outputs.
        for (output : reactor.allOutputs) {
            if (output.inferredType.isTokenType) {
                 pr(output, code, '''
                    using «variableStructType(output, reactor)» = «template_port_type_with_token»<«output.inferredType.targetType»>;
                 ''')
            }
            else
            {
                pr(output, code, '''
                    using «variableStructType(output, reactor)» = «template_port_type»<«output.inferredType.targetType»>;
                ''')
            }
        }
        // Finally, handle actions.
        // The very first item on this struct needs to be
        // a trigger_t* because the struct will be cast to (trigger_t*)
        // by the schedule() functions to get to the trigger.
        for (action : reactor.allActions) {
            pr(action, code, '''
                typedef struct {
                    trigger_t* trigger;
                    «action.valueDeclaration»
                    bool is_present;
                    bool has_value;
                    lf_token_t* token;
                } «variableStructType(action, reactor)»;
            ''')
        }
    }
    
    /** Add necessary include files specific to the target language.
     *  Note. The core files always need to be (and will be) copied 
     *  uniformly across all target languages.
     */
    override includeTargetLanguageHeaders()
    {    	
        pr('#include "ccpptarget.h"')
    }

    /** Add necessary source files specific to the target language.  */
    override includeTargetLanguageSourceFiles()
    {
        if (targetConfig.threads > 0) {
            // Set this as the default in the generated code,
            // but only if it has not been overridden on the command line.
            pr(startTimers, '''
                if (number_of_threads == 0) {
                   number_of_threads = «targetConfig.threads»;
                }
            ''')
        }
        if (isFederated) {
            pr("#include \"core/federate.c\"")
        }
    }

    /** Generate C code from the Lingua Franca model contained by the
     *  specified resource. This is the main entry point for code
     *  generation.
     *  @param resource The resource containing the source code.
     *  @param fsa The file system access (used to write the result).
     *  @param context FIXME: Undocumented argument. No idea what this is.
     */
    override void doGenerate(Resource resource, IFileSystemAccess2 fsa,
            IGeneratorContext context) {
                // Always use the threaded version
                targetConfig.threads = 1;
                targetConfig.compiler = "g++";
            	super.doGenerate(resource, fsa, context);
            }
            
    
    /**
     * Copy target-specific header file to the src-gen directory.
     */
    override copyTargetHeaderFile() {
        copyFileFromClassPath("/lib/CCpp/ccpptarget.h", fileConfig.getSrcGenPath.resolve("ccpptarget.h").toString)
    }
    
    override getTarget() {
        return Target.CCPP
    }
}
