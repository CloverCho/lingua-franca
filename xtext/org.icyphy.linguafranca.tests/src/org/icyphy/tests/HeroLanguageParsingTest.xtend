/*
 * generated by Xtext 2.11.0.RC2
 */

// This is a JUnit4 test that is here to prove that the problem
// is not JUnit4 vs JUnit5.  This test can be removed once things work.

package org.icyphy.tests
//package my.mavenized.teststo

import com.google.inject.Inject
// import my.mavenized.herolanguage.Heros
import org.eclipse.xtext.testing.InjectWith
import org.eclipse.xtext.testing.XtextRunner
import org.eclipse.xtext.testing.util.ParseHelper

import org.icyphy.linguaFranca.Model

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.icyphy.linguaFranca.Model

@RunWith(XtextRunner)
@InjectWith(LinguaFrancaInjectorProvider)
class HeroLanguageParsingTest {
	@Inject
	ParseHelper<Model> parseHelper
	
	@Test
	def void loadModel() {
                val result = parseHelper.parse('''
			hero superman can FLY
			hero iceman can ICE
		''')
          
		Assert.assertNotNull(result)
		//Assert.assertTrue(result.eResource.errors.isEmpty)
	}
}

