package org.icyphy.linguafranca.diagram.synthesis.util

import com.google.common.collect.HashMultimap
import com.google.inject.Inject
import de.cau.cs.kieler.klighd.kgraph.KGraphElement
import de.cau.cs.kieler.klighd.kgraph.KNode
import de.cau.cs.kieler.klighd.krendering.ViewSynthesisShared
import java.util.Map
import java.util.function.Consumer
import org.eclipse.elk.graph.properties.Property
import org.icyphy.graph.BreadCrumbTrail
import org.icyphy.graph.ReactionGraph
import org.icyphy.linguaFranca.Connection
import org.icyphy.linguaFranca.Instantiation
import org.icyphy.linguaFranca.Reactor
import org.icyphy.linguafranca.diagram.synthesis.AbstractSynthesisExtensions
import org.icyphy.linguafranca.diagram.synthesis.LinguaFrancaSynthesis

/**
 * Dependency cycle detection for Lingua Franca diagrams.
 * 
 * @author{Alexander Schulz-Rosengarten <als@informatik.uni-kiel.de>}
 */
@ViewSynthesisShared
class CycleVisualization extends AbstractSynthesisExtensions {
	
	// Properties for marking diagram elements
	public static val DEPENDENCY_CYCLE = new Property<Boolean>("org.icyphy.linguafranca.diagram.synthesis.dependency.cycle", false)
	
	@Inject
	extension UtilityExtensions
	
	/**
	 * Performs cycle detection based on the diagram's graph structure and applies given highlighting to the included elements
	 */
	def boolean detectAndHighlightCycles(Reactor reactor, Map<BreadCrumbTrail<Instantiation>, KNode> allReactorNodes, Consumer<KGraphElement> highlighter) {
		val graph = new ReactionGraph(reactor)
		
        if (!graph.cycles.empty && highlighter !== null) {
			// Highlight cycles
            for (cycle : graph.cycles) {
            	val allAffectedElements = HashMultimap.create
            	for (elem : cycle) {
            		allAffectedElements.put(elem.path, elem.node)
            	}
            	
            	for (reactorCrumb : allAffectedElements.keySet) {
            		val affectedElements = allAffectedElements.get(reactorCrumb)
            		val reactorNode = allReactorNodes.get(reactorCrumb)
            		reactorNode.setProperty(DEPENDENCY_CYCLE, true)
            		highlighter.accept(reactorNode)
            		
            		// Reactor edges
            		for (cycleEgde : reactorNode.outgoingEdges.filter[
            			affectedElements.contains(sourcePort.sourceElement()) &&
            			(
            				(
            					!target.sourceIsReactor() &&
            					allAffectedElements.values.contains(target.sourceElement())
            				) || (
            					target.sourceIsReactor() &&
            					allAffectedElements.keySet.contains(target.getProperty(LinguaFrancaSynthesis.REACTOR_INSTANCE)) &&
            					allAffectedElements.get(target.getProperty(LinguaFrancaSynthesis.REACTOR_INSTANCE)).contains(targetPort.sourceElement())
            				)
            			)
            		]) {
            			// FIXME: Still hard-coded semantics
            			if (!(cycleEgde.sourceElement() instanceof Connection) || (cycleEgde.sourceElement() as Connection).delay === null) {
	            			cycleEgde.setProperty(DEPENDENCY_CYCLE, true)
            				highlighter.accept(cycleEgde)
            			}
            		}
            		
            		// Reactor ports
            		for (cyclePort : reactorNode.ports.filter[affectedElements.contains(it.sourceElement())]) {
            			cyclePort.setProperty(DEPENDENCY_CYCLE, true)
            			highlighter.accept(cyclePort)
            		}
            		
            		// Child Nodes
					for (childNode : reactorNode.children.filter[affectedElements.contains(it.sourceElement()) && !sourceIsReactor]) {
            			childNode.setProperty(DEPENDENCY_CYCLE, true)
            			highlighter.accept(childNode)
            			
						for (cycleEgde : childNode.outgoingEdges.filter[
            				(
            					!target.sourceIsReactor() &&
            					affectedElements.contains(target.sourceElement())
            				) || (
            					target.sourceIsReactor() &&
            					allAffectedElements.keySet.contains(target.getProperty(LinguaFrancaSynthesis.REACTOR_INSTANCE)) &&
            					allAffectedElements.get(target.getProperty(LinguaFrancaSynthesis.REACTOR_INSTANCE)).contains(targetPort.sourceElement())
            				)
	            		]) {
	            			// FIXME: Still hard-coded semantics
	            			if (!(cycleEgde.sourceElement() instanceof Connection) || (cycleEgde.sourceElement() as Connection).delay === null) {
		            			cycleEgde.setProperty(DEPENDENCY_CYCLE, true)
		            			highlighter.accept(cycleEgde)
	            			}
	            		}
            		}
            	}
            }
            return true
         }
         
         return false
	}
}
