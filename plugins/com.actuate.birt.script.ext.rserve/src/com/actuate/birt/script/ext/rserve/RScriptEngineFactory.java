/*******************************************************************************
 * Copyright (c) 2017 Actuate. All Rights Reserved.
 * Trademarks owned by Actuate.
 * "OpenText" is a trademark of Open Text.
 *******************************************************************************/

package com.actuate.birt.script.ext.rserve;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;

/**
 * Implements ScriptEngineFactory interface for Rserve-based R script
 */
public class RScriptEngineFactory implements ScriptEngineFactory {

	public static List<String> names = Collections.unmodifiableList(
			Arrays.asList("BirtR"));
	
	@Override
	public String getEngineName() {
		return "BIRT-R Script Engine";
	}

	@Override
	public String getEngineVersion() {
		return "1.0.0";
	}

	@Override
	public List<String> getExtensions() {
		return Collections.emptyList();
	}

	@Override
	public List<String> getMimeTypes() {
		return Collections.emptyList();
	}

	@Override
	public List<String> getNames() {
		return names;
	}

	@Override
	public String getLanguageName() {
		return "BIRT-R";
	}

	@Override
	public String getLanguageVersion() {
		return "1.0.0";
	}

	@Override
	public Object getParameter(String key) {
		return null;
	}

	@Override
	public String getMethodCallSyntax(String obj, String m, String... args) {
		return "";
	}

	@Override
	public String getOutputStatement(String toDisplay) {
		return "";
	}

	@Override
	public String getProgram(String... statements) {
		return "";
	}

	@Override
	public ScriptEngine getScriptEngine() {
		return new RScriptEngine( this );
	}

}
