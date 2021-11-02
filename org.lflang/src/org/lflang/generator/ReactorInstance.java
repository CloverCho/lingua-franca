/** A data structure for a reactor instance. */

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

package org.lflang.generator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lflang.ASTUtils;
import org.lflang.ErrorReporter;
import org.lflang.generator.TriggerInstance.BuiltinTriggerVariable;
import org.lflang.lf.Action;
import org.lflang.lf.Connection;
import org.lflang.lf.Input;
import org.lflang.lf.Instantiation;
import org.lflang.lf.Output;
import org.lflang.lf.Parameter;
import org.lflang.lf.Port;
import org.lflang.lf.Reaction;
import org.lflang.lf.Reactor;
import org.lflang.lf.Timer;
import org.lflang.lf.TriggerRef;
import org.lflang.lf.Value;
import org.lflang.lf.VarRef;
import org.lflang.lf.Variable;
import org.lflang.lf.WidthSpec;


/**
 * Representation of a runtime instance of a reactor.
 * For the main reactor, which has no parent, once constructed,
 * this object represents the entire Lingua Franca program.
 * The constructor analyzes the graph of dependencies between
 * reactions and throws exception if this graph is cyclic.
 *
 * @author{Marten Lohstroh <marten@berkeley.edu>}
 * @author{Edward A. Lee <eal@berkeley.edu>}
 */
public class ReactorInstance extends NamedInstance<Instantiation> {

    /**
     * Create a new instantiation hierarchy that starts with the given top-level reactor.
     * @param reactor The top-level reactor.
     * @param reporter The error reporter.
     */
    public ReactorInstance(Reactor reactor, ErrorReporter reporter) {
        this(ASTUtils.createInstantiation(reactor), null, reporter, -1, null);
    }

    /**
     * Create a new instantiation hierarchy that starts with the given top-level reactor
     * but only creates contained reactors up to the specified depth.
     * @param reactor The top-level reactor.
     * @param reporter The error reporter.
     * @param desiredDepth The depth to which to go, or -1 to construct the full hierarchy.
     */
    public ReactorInstance(Reactor reactor, ErrorReporter reporter, int desiredDepth) {
        this(ASTUtils.createInstantiation(reactor), null, reporter, desiredDepth, null);
    }

    /**
     * Create a new instantiation hierarchy that starts with the given reactor.
     * @param reactor The top-level reactor.
     * @param reporter The error reporter.
     * @param unorderedReactions A list of reactions that should be treated as unordered.
     */
    public ReactorInstance(Reactor reactor, ErrorReporter reporter, Set<Reaction> unorderedReactions) {
        this(ASTUtils.createInstantiation(reactor), null, reporter, -1, unorderedReactions);
    }

    //////////////////////////////////////////////////////
    //// Public fields.

    /** The action instances belonging to this reactor instance. */
    public List<ActionInstance> actions = new ArrayList<ActionInstance>();

    /**
     * The contained reactor instances, in order of declaration.
     * For banks of reactors, this includes both the bank definition
     * Reactor (which has bankIndex == -2) followed by each of the
     * bank members (which have bankIndex >= 0).
     */
    public List<ReactorInstance> children = new ArrayList<ReactorInstance>();

    /** The input port instances belonging to this reactor instance. */
    public List<PortInstance> inputs = new ArrayList<PortInstance>();

    /** The output port instances belonging to this reactor instance. */
    public List<PortInstance> outputs = new ArrayList<PortInstance>();

    /** The parameters of this instance. */
    public List<ParameterInstance> parameters = new ArrayList<ParameterInstance>();

    /** List of reaction instances for this reactor instance. */
    public List<ReactionInstance> reactions = new ArrayList<ReactionInstance>();

    /** The timer instances belonging to this reactor instance. */
    public List<TimerInstance> timers = new ArrayList<TimerInstance>();

    /** The reactor definition in the AST. */
    public final Reactor reactorDefinition;

    /** Indicator that this reactor has itself as a parent, an error condition. */
    public final boolean recursive;

    //////////////////////////////////////////////////////
    //// Public methods.

    /**
     * If this reactor is in a bank of reactors, then return
     * the reactor instance defining the bank. Otherwise, return null.
     */
    public ReactorInstance getBank() {
        return bank;
    }

    /**
     * If this reactor is in a bank of reactors, return its index, otherwise, return -1
     * for an ordinary reactor and -2 for a placeholder for a bank of reactors.
     */
    public int getBankIndex() {
        return bankIndex;
    }

    /**
     * Return the members of this bank, or null if there are none.
     * @return actual bank size or -1 if this is not a bank master.
     */
    public List<ReactorInstance> getBankMembers() {
        return bankMembers;
    }
    /**
     * Return the Connection that created the link between the specified source
     * and destination, or null if there is no such link.
     */
    public Connection getConnection(PortInstance source, PortInstance destination) {
        var table = connectionTable.get(source);
        if (table != null) {
            return table.get(destination);
        }
        return null;
    }

    /**
     * Get a map of connections as they appear in a visualization of the program.
     * For each connection, there is map from source ports (single ports and multiports)
     * on the left side of the connection to a set of destination ports (single ports
     * and multiports) on the right side of the connection. For banks of reactors,
     * this includes only the connections to and from the first element of the bank.
     */
    public Map<Connection, Map<PortInstance, Set<PortInstance>>> getConnections() {
        return connections;
    }
    
    /**
     * Return the specified input by name or null if there is no such input.
     * @param name The input name.
     */
    public PortInstance getInput(String name) {
        for (PortInstance port: inputs) {
            if (port.getName().equals(name)) {
                return port;
            }
        }
        return null;
    }

    /** 
     * Override the base class to append [index] if this reactor
     * is in a bank of reactors.
     * @return The full name of this instance.
     */
    @Override
    public String getName() {
        var result = this.definition.getName();
        if (this.bankIndex >= 0) {
            result += "[" + this.bankIndex + "]";
        }
        if (result == null) return "";
        return result;
    }

    /**
     * Return the specified output by name or null if there is no such output.
     * @param name The output name.
     */
    public PortInstance getOutput(String name) {
        for (PortInstance port: outputs) {
            if (port.getName().equals(name)) {
                return port;
            }
        }
        return null;
    }

    /**
     * Return a parameter matching the specified name if the reactor has one
     * and otherwise return null.
     * @param name The parameter name.
     */
    public ParameterInstance getParameter(String name) {
        for (ParameterInstance parameter: parameters) {
            if (parameter.getName().equals(name)) {
                return parameter;
            }
        }
        return null;
    }
    
    /**
     * Return the startup trigger or null if not used in any reaction.
     */
    public TriggerInstance<BuiltinTriggerVariable> getStartupTrigger() {
        return startupTrigger;
    }

    /**
     * Return the shutdown trigger or null if not used in any reaction.
     */
    public TriggerInstance<BuiltinTriggerVariable> getShutdownTrigger() {
        return shutdownTrigger;
    }
    
    /**
     * Given a parameter definition for this reactor, return the initial integer
     * value of the parameter. If the parameter is overridden when instantiating
     * this reactor or any of its containing reactors, use that value.
     * Otherwise, use the default value in the reactor definition.
     * If the parameter cannot be found or its value is not an integer, return null.
     * 
     * @param parameter The parameter definition (a syntactic object in the AST).
     * 
     * @return An integer value or null.
     */
    public Integer initialIntParameterValue(Parameter parameter) {
        return ASTUtils.initialValueInt(parameter, instantiations());
    }  

    /**
     * Given a parameter definition for this reactor, return the initial value
     * of the parameter. If the parameter is overridden when instantiating
     * this reactor or any of its containing reactors, use that value.
     * Otherwise, use the default value in the reactor definition.
     * 
     * The returned list of Value objects is such that each element is an
     * instance of Time, String, or Code, never Parameter.
     * For most uses, this list has only one element, but parameter
     * values can be lists of elements, so the returned value is a list.
     * 
     * @param parameter The parameter definition (a syntactic object in the AST).
     * 
     * @return A list of Value objects, or null if the parameter is not found.
     *  Return an empty list if no initial value is given.
     *  Each value is an instance of Literal if a literal value is given,
     *  a Time if a time value was given, or a Code, if a code value was
     *  given (text in the target language delimited by {= ... =}
     */
    public List<Value> initialParameterValue(Parameter parameter) {
        return ASTUtils.initialValue(parameter, instantiations());
    }

    /**
     * Return a list of Instantiation objects for evaluating parameter
     * values.  The first object in the list is the AST Instantiation
     * that created this reactor instance, the second is the AST instantiation
     * that created the containing reactor instance, and so on until there
     * are no more containing reactor instances. This will return an empty
     * list if this reactor instance is at the top level (is main).
     */
    public List<Instantiation> instantiations() {
        if (_instantiations == null) {
            _instantiations = new ArrayList<Instantiation>();
            if (definition != null) {
                _instantiations.add(definition);
                if (parent != null) {
                    _instantiations.addAll(parent.instantiations());
                }
            }
        }
        return _instantiations;
    }

    /**
     * Returns true if this is a bank of reactors.
     * @return true if a reactor is a bank, false otherwise
     */
    public boolean isBank() {
        // FIXME magic number
        return bankIndex == -2;
    }

    /**
     * Returns whether this is a main or federated reactor.
     * @return true if reactor definition is marked as main or federated, false otherwise.
     */
    public boolean isMainOrFederated() {
        return reactorDefinition != null 
                && (reactorDefinition.isMain() || reactorDefinition.isFederated());
    }
    
    ///////////////////////////////////////////////////
    //// Methods for finding instances in this reactor given an AST node.
    
    /** 
     * Return the action instance within this reactor 
     * instance corresponding to the specified action reference.
     * @param action The action as an AST node.
     * @return The corresponding action instance or null if the
     *  action does not belong to this reactor.
     */
    public ActionInstance lookupActionInstance(Action action) {
        for (ActionInstance actionInstance : actions) {
            if (actionInstance.definition == action) {
                return actionInstance;
            }
        }
        return null;
    }

    /** 
     * Given a parameter definition, return the parameter instance
     * corresponding to that definition, or null if there is
     * no such instance.
     * @param parameter The parameter definition (a syntactic object in the AST).
     * @return A parameter instance, or null if there is none.
     */
    public ParameterInstance lookupParameterInstance(Parameter parameter) {
        for (ParameterInstance param : parameters) {
            if (param.definition == parameter) {
                return param;
            }
        }
        return null;
    }
    
    /** 
     * Given a port definition, return the port instance
     * corresponding to that definition, or null if there is
     * no such instance.
     * @param port The port definition (a syntactic object in the AST).
     * @return A port instance, or null if there is none.
     */
    public PortInstance lookupPortInstance(Port port) {
        // Search one of the inputs and outputs sets.
        List<PortInstance> ports = null;
        if (port instanceof Input) {
            ports = this.inputs;
        } else if (port instanceof Output) {
            ports = this.outputs;
        }
        for (PortInstance portInstance : ports) {
            if (portInstance.definition == port) {
                return portInstance;
            }
        }
        return null;
    }

    /** 
     * Given a reference to a port belonging to this reactor
     * instance, return the port instance.
     * Return null if there is no such instance.
     * @param reference The port reference.
     * @return A port instance, or null if there is none.
     */
    public PortInstance lookupPortInstance(VarRef reference) {
        if (!(reference.getVariable() instanceof Port)) {
            // Trying to resolve something that is not a port
            return null;
        }
        if (reference.getContainer() == null) {
            // Handle local reference
            return lookupPortInstance((Port) reference.getVariable());
        } else {
            // Handle hierarchical reference
            var containerInstance = getChildReactorInstance(reference.getContainer());
            if (containerInstance == null) return null;
            return containerInstance.lookupPortInstance((Port) reference.getVariable());
        }
    }

    /** 
     * Return the reaction instance within this reactor 
     * instance corresponding to the specified reaction.
     * @param reaction The reaction as an AST node.
     * @return The corresponding reaction instance or null if the
     *  reaction does not belong to this reactor.
     */
    public ReactionInstance lookupReactionInstance(Reaction reaction) {
        for (ReactionInstance reactionInstance : reactions) {
            if (reactionInstance.definition == reaction) {
                return reactionInstance;
            }
        }
        return null;
    }
    
    /**
     * Return the reactor instance within this reactor
     * that has the specified instantiation. Note that this
     * may be a bank of reactors. Return null if there
     * is no such reactor instance.
     */
    public ReactorInstance lookupReactorInstance(Instantiation instantiation) {
        for (ReactorInstance reactorInstance : children) {
            if (reactorInstance.definition == instantiation) {
                return reactorInstance;
            }
        }
        return null;
    }
    
    /** 
     * Return the timer instance within this reactor 
     * instance corresponding to the specified timer reference.
     * @param timer The timer as an AST node.
     * @return The corresponding timer instance or null if the
     *  timer does not belong to this reactor.
     */
    public TimerInstance lookupTimerInstance(Timer timer) {
        for (TimerInstance timerInstance : timers) {
            if (timerInstance.definition == timer) {
                return timerInstance;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReactorInstance root() {
        if (parent != null) {
            return parent.root();
        } else {
            return this;
        }
    }
    
    /** 
     * Return a descriptive string.
     */
    @Override
    public String toString() {
        return "ReactorInstance " + getFullName();
    }

    /** 
     * Return the set of all ports that receive data from the 
     * specified source. This includes inputs and outputs at the same level 
     * of hierarchy and input ports deeper in the hierarchy.
     * It also includes inputs or outputs up the hierarchy (i.e., ones
     * that are reached via any output port).
     * If the argument is an input port, then it is included in the result.
     * No port will appear more than once in the result.
     * 
     * @param source An output or input port.
     */
    public Set<PortInstance> transitiveClosure(PortInstance source) {
        var result = new LinkedHashSet<PortInstance>();
        transitiveClosure(source, result);
        return result;
    }

    /** 
     * Override the base class to return the uniqueID of the bank rather
     * than this member of the bank, if this is a member of a bank of reactors.
     * 
     * @return An identifier for this instance that is guaranteed to be
     *  unique within the top-level parent.
     */
    @Override
    public String uniqueID() {
        if (this.bank != null) {
            return this.bank.uniqueID();
        }
        return super.uniqueID();
    }

    /**
     * For the specified width specification, return the width.
     * This may be for a bank of reactors within this reactor instance or
     * for a port of this reactor instance. If the argument is null, there
     * is no width specification, so return 1. Otherwise, evaluate the
     * width value by determining the value of any referenced parameters.
     * 
     * @param widthSpec The width specification.
     * 
     * @return The width, or -1 if it cannot be determined.
     */
    public int width(WidthSpec widthSpec) {
        if (widthSpec.eContainer() instanceof Instantiation && parent != null) {
            // We need the instantiations list of the containing reactor,
            // not this one.
            return ASTUtils.width(widthSpec, parent.instantiations());
        }
        return ASTUtils.width(widthSpec, instantiations());
    }
    
    //////////////////////////////////////////////////////
    //// Protected fields.

    /**
     * The LF syntax does not currently support declaring reactions unordered,
     * but unordered reactions are created in the AST transformations handling
     * federated communication and after delays. Unordered reactions can execute
     * in any order and concurrently even though they are in the same reactor.
     * FIXME: Remove this when the language provides syntax.
     */
    protected Set<Reaction> unorderedReactions = new LinkedHashSet<Reaction>();

    /**
     * If this reactor is in a bank of reactors, then this member
     * refers to the reactor instance defining the bank.
     */
    protected ReactorInstance bank = null;

    /**
     * If this reactor instance is a placeholder for a bank of reactors,
     * as created by the new[width] ReactorClass() syntax, then this
     * list will be non-null and will contain the reactor instances in
     * the bank.
     */
    protected List<ReactorInstance> bankMembers = null;

    /**
     * If this reactor is in a bank of reactors, its index, otherwise, -1
     * for an ordinary reactor and -2 for a placeholder for a bank of reactors.
     */
    protected int bankIndex = -1;

    /** The generator that created this reactor instance. */
    protected ErrorReporter reporter; // FIXME: This accumulates a lot of redundant references

    /** The startup trigger. Null if not used in any reaction. */
    protected TriggerInstance<BuiltinTriggerVariable> startupTrigger = null;

    /** The shutdown trigger. Null if not used in any reaction. */
    protected TriggerInstance<BuiltinTriggerVariable> shutdownTrigger = null;

    /** Table recording which connection created a link between a source and destination. */
    protected Map<PortInstance, Map<PortInstance, Connection>> connectionTable 
            = new LinkedHashMap<PortInstance, Map<PortInstance, Connection>>();

    /** The nested list of instantiations that created this reactor instance. */
    protected List<Instantiation> _instantiations;

    /**
     * The depth in the hierarchy of this reactor instance.
     * This is 0 for main or federated, 1 for the reactors immediately contained, etc.
     */
    protected int depth = 0;

    //////////////////////////////////////////////////////
    //// Protected methods.
    
    /** 
     * Create all the reaction instances of this reactor instance
     * and record the dependencies and antidependencies
     * between ports, actions, and timers and reactions.
     * This also records the dependencies between reactions
     * that follows from the order in which they are defined.
     */
    protected void createReactionInstances() {
        List<Reaction> reactions = ASTUtils.allReactions(reactorDefinition);
        if (reactions != null) {
            int count = 0;

            // Check for startup and shutdown triggers.
            for (Reaction reaction : reactions) {
                // Create the reaction instance.
                var reactionInstance = new ReactionInstance(reaction, this,
                    unorderedReactions.contains(reaction), count++);
                
                // Add the reaction instance to the map of reactions for this
                // reactor.
                this.reactions.add(reactionInstance);
            }
        }
    }

    /** 
     * Return the instance of a child rector created by the specified
     * definition or null if there is none.
     * @param definition The definition of the child reactor ("new" statement).
     */
    protected ReactorInstance getChildReactorInstance(Instantiation definition) {
        for (ReactorInstance child : this.children) {
            if (child.definition == definition) {
                return child;
            }
        }
        return null;
    }

    /**
     * Returns the startup trigger or create a new one if none exists.
     */
    protected TriggerInstance<? extends Variable> getOrCreateStartup(TriggerRef trigger) {
        if (startupTrigger == null) {
            startupTrigger = new TriggerInstance<BuiltinTriggerVariable>(
                    TriggerInstance.BuiltinTrigger.STARTUP, trigger, this);
        }
        return startupTrigger;
    }
    
    /**
     * Returns the shutdown trigger or create a new one if none exists.
     */
    protected TriggerInstance<? extends Variable> getOrCreateShutdown(TriggerRef trigger) {
        if (shutdownTrigger == null) {
            shutdownTrigger = new TriggerInstance<BuiltinTriggerVariable>(
                    TriggerInstance.BuiltinTrigger.SHUTDOWN, trigger, this);
        }
        return shutdownTrigger;
    }
    
    /** 
     * Return the trigger instances (input ports, timers, and actions
     * that trigger reactions) belonging to this reactor instance.
     */
    protected Set<TriggerInstance<? extends Variable>> getTriggers() {
        // FIXME: Cache this.
        var triggers = new LinkedHashSet<TriggerInstance<? extends Variable>>();
        for (ReactionInstance reaction : this.reactions) {
            triggers.addAll(reaction.triggers);
        }
        return triggers;
    }

    /** 
     * Return the trigger instances (input ports, timers, and actions
     * that trigger reactions) together the ports that the reaction reads
     * but that don't trigger it.
     * 
     * @return The trigger instances belonging to this reactor instance.
     */
    protected Set<TriggerInstance<? extends Variable>> getTriggersAndReads() {
        // FIXME: Cache this.
        var triggers = new LinkedHashSet<TriggerInstance<? extends Variable>>();
        for (ReactionInstance reaction : this.reactions) {
            triggers.addAll(reaction.triggers);
            triggers.addAll(reaction.reads);
        }
        return triggers;
    }
    
    /** 
     * Collect all reactions that have not been assigned a level and
     * return the list.
     * @param reactor The reactor for which to check reactions.
     * @param result The list to add reactions to.
     * @return The list of reactions without levels.
     */
    protected List<ReactionInstance> reactionsWithoutLevels(
        ReactorInstance reactor,
        List<ReactionInstance> result
    ) {
        for (ReactionInstance reaction : reactor.reactions) {
            if (reaction.level < 0) {
                result.add(reaction);
            }
        }
        for (ReactorInstance child : reactor.children) {
            reactionsWithoutLevels(child, result);
        }
        return result;
    }

    /** 
     * Add to the specified destinations set all ports that receive data from the 
     * specified source. This includes inputs and outputs at the same level 
     * of hierarchy and input ports deeper in the hierarchy.
     * It also includes inputs or outputs up the hierarchy (i.e., ones
     * that are reached via any output port).
     * @param source A port belonging to this reaction instance or one
     *  of its children.
     * @param destinations The set of destinations to populate.
     */
    protected void transitiveClosure(
            PortInstance source,
            LinkedHashSet<PortInstance> destinations
    ) {
        // Check that the specified port belongs to this reactor or one of its children.
        // The following assumes that the main reactor has no ports, or else
        // a NPE will occur.
        if (source.parent != this && source.parent.parent != this) {
            throw new InvalidSourceException(
                "Internal error: port " + source + " does not belong to " +
                    this + " nor any of its children."
            );
        }
        // If the port is a multiport, then iterate over its contained ordinary ports instead.
        // If the port is an input port, then include it in the result.
        if (source.isInput()) {
            destinations.add(source);
        }
        Map<PortInstance, Connection> map = connectionTable.get(source);
        if (map != null) {
            Set<PortInstance> localDestinations = map.keySet();

            if (localDestinations != null) {
                for (PortInstance destination : localDestinations) {
                    destinations.add(destination);
                    if (destination.isInput()) {
                        // Destination may have further destinations lower in the hierarchy.
                        destination.parent.transitiveClosure(destination, destinations);
                    } else if (destination.parent.parent != null) {
                        // Destination may have further destinations higher in the hierarchy.
                        destination.parent.parent.transitiveClosure(destination, destinations);
                    }
                }
            }
        }
    }
    
    ////////////////////////////////////////
    //// Private constructors
    
    /**
     * Create reactor instance resulting from the specified top-level instantiation.
     * @param instance The Instance statement in the AST.
     * @param parent The parent, or null for the main rector.
     * @param reporter The error reporter.
     * @param desiredDepth The depth to which to expand the hierarchy.
     * @param unorderedReactions A list of reactions that should be treated as unordered.
     */
    private ReactorInstance(
        Instantiation definition, 
        ReactorInstance parent, 
        ErrorReporter reporter, 
        int desiredDepth,
        Set<Reaction> unorderedReactions
    ) {
        // If the reactor is being instantiated with new[width], then pass -2
        // to the constructor, otherwise pass -1.
        this(
            definition, 
            parent, 
            reporter, 
            (definition.getWidthSpec() != null)? -2 : -1, 
            0, 
            desiredDepth,
            unorderedReactions
        );
    }

    /**
     * Create a runtime instance from the specified definition
     * and with the specified parent that instantiated it.
     * @param instance The Instance statement in the AST.
     * @param parent The parent, or null for the main rector.
     * @param generator The generator (for error reporting).
     * @param reactorIndex -1 for an ordinary reactor, -2 for a
     *  placeholder for a bank of reactors, or the index of the
     *  reactor in a bank of reactors otherwise.
     * @param depth The depth of this reactor in the hierarchy.
     * @param desiredDepth The depth to which to expand the hierarchy.
     * @param unorderedReactions A list of reactions that should be treated as unordered.
     *  It can be passed as null.
     */
    private ReactorInstance(
            Instantiation definition, 
            ReactorInstance parent,
            ErrorReporter reporter,
            int reactorIndex,
            int depth,
            int desiredDepth,
            Set<Reaction> unorderedReactions) {
        super(definition, parent);
        this.reporter = reporter;
        this.bankIndex = reactorIndex;
        this.reactorDefinition = ASTUtils.toDefinition(definition.getReactorClass());
        this.depth = depth;
        if (unorderedReactions != null) {
            this.unorderedReactions = unorderedReactions;
        }
        
        // check for recursive instantiation
        var currentParent = parent;
        var foundSelfAsParent = false;
        do {
            if (currentParent != null) {
                if (currentParent.reactorDefinition == this.reactorDefinition) {
                    foundSelfAsParent = true;
                    currentParent = null; // break
                } else {
                    currentParent = currentParent.parent;
                }
            }
        } while(currentParent != null);
        
        this.recursive = foundSelfAsParent;
        if (recursive) {
            reporter.reportError(definition, "Recursive reactor instantiation.");
        }
        
        // If this reactor is actually a bank of reactors, then instantiate
        // each individual reactor in the bank and skip the rest of the
        // initialization for this reactor instance.
        if (reactorIndex == -2) {
            // If the bank width is variable, then we have to wait until the first connection
            // before instantiating the children.
            var width = width(definition.getWidthSpec());
            if (width > 0) {
                this.bankMembers = new ArrayList<ReactorInstance>(width);
                for (var index = 0; index < width; index++) {
                    var childInstance = new ReactorInstance(
                        definition, parent, reporter, index, depth, desiredDepth, this.unorderedReactions
                    );
                    this.bankMembers.add(childInstance);
                    childInstance.bank = this;
                    childInstance.bankIndex = index;
                }
            } else {
                reporter.reportWarning(definition, "Cannot infer width.");
            }
            return;
        }
        
        // If the reactor definition is null, give up here. Otherwise, diagram generation
        // will fail an NPE.
        if (reactorDefinition == null) {
            reporter.reportError(definition, "Reactor instantiation has no matching reactor definition.");
            return;
        }
        
        // Apply overrides and instantiate parameters for this reactor instance.
        for (Parameter parameter : ASTUtils.allParameters(reactorDefinition)) {
            this.parameters.add(new ParameterInstance(parameter, this));
        }

        // Instantiate inputs for this reactor instance
        for (Input inputDecl : ASTUtils.allInputs(reactorDefinition)) {
            this.inputs.add(new PortInstance(inputDecl, this, reporter));
        }

        // Instantiate outputs for this reactor instance
        for (Output outputDecl : ASTUtils.allOutputs(reactorDefinition)) {
            this.outputs.add(new PortInstance(outputDecl, this, reporter));
        }

        // Do not process content (except interface above) if recursive
        if (!recursive && (desiredDepth < 0 || this.depth < desiredDepth)) {
            // Instantiate children for this reactor instance
            for (Instantiation child : ASTUtils.allInstantiations(reactorDefinition)) {
                var childInstance = new ReactorInstance(
                    child, 
                    this, 
                    reporter, 
                    (child.getWidthSpec() != null)? -2 : -1, 
                    depth + 1, 
                    desiredDepth,
                    this.unorderedReactions
                );
                this.children.add(childInstance);
                // If the child is a bank of instances, add all the bank instances.
                // These must be added after the bank itself.
                if (childInstance.bankMembers != null) {
                    this.children.addAll(childInstance.bankMembers);
                }
            }

            // Instantiate timers for this reactor instance
            for (Timer timerDecl : ASTUtils.allTimers(reactorDefinition)) {
                this.timers.add(new TimerInstance(timerDecl, this));
            }

            // Instantiate actions for this reactor instance
            for (Action actionDecl : ASTUtils.allActions(reactorDefinition)) {
                this.actions.add(new ActionInstance(actionDecl, this));
            }

            establishPortConnections();

            // Create the reaction instances in this reactor instance.
            // This also establishes all the implied dependencies.
            // Note that this can only happen _after_ the children, 
            // port, action, and timer instances have been created.
            createReactionInstances();
        }
    }
    
    //////////////////////////////////////////////////////
    //// Private methods.

    /**
     * Record the connection from the source port to the destination port in the
     * connectionTable map.
     * @param source The source port.
     * @param destination The destination port.
     * @param connection The connection AST node creating the connection.
     */
    private void addDestination(PortInstance source, PortInstance destination, Connection connection) {
        Map<PortInstance, Connection> srcConnections = connectionTable.get(source);
        if (srcConnections == null) {
            srcConnections = new LinkedHashMap<PortInstance, Connection>();
            connectionTable.put(source, srcConnections);
        }
        srcConnections.put(destination, connection);
    }

    /**
     * Connect the given left port instance to the given right port instance.
     * These may be multiports.
     * @param connection The connection statement creating this connection.
     * @param srcInstance The source instance (the left port).
     * @param dstInstance The destination instance (the right port).
     */
    private void connectPortInstances(
            Connection connection, 
            PortInstance srcInstance, 
            PortInstance dstInstance
    ) {
        srcInstance.addDependentPort(dstInstance);
        dstInstance.dependsOnPorts.add(srcInstance);
        // Record the connection in the connection table.
        // Original cryptic xtend code:
        // this.destinations.compute(srcInstance, [key, set| CollectionUtil.plus(set, dstInstance)])
        // this.connectionTable.compute(srcInstance, [key, map| CollectionUtil.plus(map, dstInstance, connection)])
        addDestination(srcInstance, dstInstance, connection);
                
        // The following is support for the diagram visualization.
        
        // The source may be at a bank index greater than 0.
        // For visualization, this needs to be converted to the source
        // at bank 0, because only that one is rendered.
        // We want the rendering to represent all connections.
        var src = srcInstance;
        var dst = dstInstance;
        if (src.isOutput() && src.parent.bankIndex > 0) {
            // Replace the source with the corresponding port instance
            // at bank index 0.
            ReactorInstance newParent = src.parent.bank.bankMembers.get(0);
            src = newParent.getOutput(src.getName());
        }
        // The destination may be at a bank index greater than 0.
        // For visualization, this needs to be converted to the destination
        // at bank 0, because only that one is rendered.
        // We want the rendering to represent all connections.
        if (dst.isInput() && dst.parent.bankIndex > 0) {
            // Replace the destination with the corresponding port instance
            // at bank index 0.
            ReactorInstance newParent = dst.parent.bank.bankMembers.get(0);
            dst = newParent.getInput(dst.getName());
        }
        
        // Record this representative connection for visualization in the
        // connections map.
        Map<PortInstance, Set<PortInstance>> map = connections.get(connection);
        if (map == null) {
            map = new LinkedHashMap<PortInstance, Set<PortInstance>>();
            connections.put(connection, map);
        }
        Set<PortInstance> destinations = map.get(src);
        if (destinations == null) {
            destinations = new LinkedHashSet<PortInstance>();
            map.put(src, destinations);
        }
        destinations.add(dst);

        // Original cryptic xtend code below.
        // val src2 = src
        // val dst2 = dst
        // this.connections.compute(connection, [_, links| {
        //     CollectionUtil.compute(links, src2, [_2, destinations| CollectionUtil.plus(destinations, dst2)])
        // }])
    }

    /**
     * Populate destinations map and the connectivity information in the port instances.
     * Note that this can only happen _after_ the children and port instances have been created.
     * Unfortunately, we have to do some complicated things here
     * to support multiport-to-multiport, multiport-to-bank,
     * and bank-to-multiport communication.  The principle being followed is:
     * in each connection statement, for each port instance on the left,
     * as obtained by the nextPort() function, connect to the next available
     * port on the right, as obtained by the nextPort() function.
     */
    private void establishPortConnections() {
        for (Connection connection : ASTUtils.allConnections(reactorDefinition)) {
            List<PortInstance> leftPortInstances = listPortInstances(connection.getLeftPorts());
            List<PortInstance> rightPortInstances = listPortInstances(connection.getRightPorts());

            // Check widths.
            int leftWidth = 0;
            for (PortInstance port: leftPortInstances) {
                leftWidth += port.width;
            }
            int rightWidth = 0;
            for (PortInstance port: rightPortInstances) {
                rightWidth += port.width;
            }
            if (leftWidth > rightWidth) {
                reporter.reportWarning(connection, 
                        "Source is wider than the destination. Outputs will be lost.");
            } else if (leftWidth < rightWidth && !connection.isIterated()) {
                reporter.reportWarning(connection, 
                        "Destination is wider than the source. Inputs will be missing.");
            }

            // If any of these ports is a multiport, then things can complicated depending
            // on how they overlap. Keep track of how much of the current left and right
            // multiports have already been used.
            int leftPortIndex = 0;  // Which port on the left we are looking at.
            int leftChannel = 0;
            int rightChannel = 0;
            for (PortInstance rightPortInstance : rightPortInstances) {
                while (rightChannel < rightPortInstance.width) {
                    if (leftPortIndex < leftPortInstances.size()) {
                        PortInstance leftPortInstance = leftPortInstances.get(leftPortIndex);
                        connectPortInstances(connection, leftPortInstance, rightPortInstance);
                    
                        // Figure out how much of each port we have used (in case it is a multiport).
                        // First, find the minimum of the two remaining widths.
                        int connectionWidth = leftPortInstance.width - leftChannel;
                        if (rightPortInstance.width - rightChannel < connectionWidth) {
                            connectionWidth = rightPortInstance.width - rightChannel;
                        }
                        leftChannel += connectionWidth;
                        rightChannel += connectionWidth;
                        if (leftChannel >= leftPortInstance.width) {
                            leftPortIndex++;
                            if (connection.isIterated() && leftPortIndex >= leftPortInstances.size()) {
                                leftPortIndex = 0;
                            }
                            leftChannel = 0;
                        }
                    } else {
                        // Something is wrong. We have run out of left ports.
                        // The validator should have caught this, so we just
                        // leave the remaining right ports unconnected.
                        rightChannel = rightPortInstance.width;
                    }
                }
                rightChannel = 0;
            }
        }
    }
    
    /**
     * Given a list of port references, as found on either side of a connection,
     * return a list of the port instances referenced. These may be multiports.
     * If the port reference has the form `c.x`, where `c` is a bank of reactors,
     * then the list will contain the port instances belonging to each bank member.
     * 
     * If a given port reference `b.m`, where `b` is a bank and `m` is a multiport,
     * is unqualified, this function iterates over bank members first, then ports.
     * E.g., if `b` and `m` have width 2, it returns `[b0.m0, b0.m1, b1.m0, b1.m1]`.
     * 
     * If a given port reference has the form `interleaved(b.m)`, where `b` is a
     * bank and `m` is a multiport, this function iterates over ports first,
     * then bank members. E.g., if `b` and `m` have width 2, it returns
     * `[b0.m0, b1.m0, b0.m1, b1.m1]`.
     */
    private List<PortInstance> listPortInstances(List<VarRef> references) {
        List<PortInstance> result = new ArrayList<PortInstance>();
        for (VarRef portRef : references) {
            // Simple error checking first.
            if (!(portRef.getVariable() instanceof Port)) {
                reporter.reportError(portRef, "Not a port.");
                return result;
            }
            // First, figure out which reactor we are dealing with.
            // The reactor we want is the container of the port.
            // If the port reference has no container, then the reactor is this one,
            // or if this one is a bank, the next available bank member.
            var reactor = this;
            if (portRef.getContainer() != null) {
                reactor = getChildReactorInstance(portRef.getContainer());
            }
            // The reactor be null only if there is an error in the code.
            // Skip this portRef so that diagram synthesis can complete.
            if (reactor != null) {
                if (reactor.bankMembers != null) {
                    // Reactor is a bank.
                    for (ReactorInstance memberReactor: reactor.bankMembers) {
                        result.add(memberReactor.lookupPortInstance((Port) portRef.getVariable()));
                    }
                } else {
                    // Reactor is not a bank.
                    PortInstance portInstance = reactor.lookupPortInstance((Port) portRef.getVariable());
                    result.add(portInstance);
                }
            }
        }
        return result;
    }

    //////////////////////////////////////////////////////
    //// Private fields.

    /**
     * A map of connections as they appear in a visualization of the program.
     * For each connection, there is map from source ports (single ports and multiports)
     * on the left side of the connection to a set of destination ports (single ports
     * and multiports) on the right side of the connection. For banks of reactors,
     * this includes only the connections to and from the first element of the bank
     */
    private Map<Connection, Map<PortInstance, Set<PortInstance>>> connections 
            = new LinkedHashMap<Connection, Map<PortInstance, Set<PortInstance>>>();
}
