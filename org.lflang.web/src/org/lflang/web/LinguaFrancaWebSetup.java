package org.lflang.web;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.eclipse.xtext.util.Modules2;
import org.lflang.LFRuntimeModule;
import org.lflang.LFStandaloneSetup;
import org.lflang.ide.LFIdeModule;

/**
 * Initialization support for running Xtext languages in web applications.
 */
class LinguaFrancaWebSetup extends LFStandaloneSetup {
	
	@Override
	public Injector createInjector() {
		return Guice.createInjector(Modules2.mixin(new LFRuntimeModule(), new LFIdeModule(), new LinguaFrancaWebModule()));
	}
	
}
