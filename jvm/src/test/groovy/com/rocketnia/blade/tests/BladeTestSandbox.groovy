// BladeTestSandbox.groovy
//
// Copyright 2010 Ross Angle
//
// This file is part of JVM-Blade.
//
// JVM-Blade is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published
// by the Free Software Foundation, either version 3 of the License,
// or (at your option) any later version.
//
// JVM-Blade is distributed in the hope that it will be useful, but
// WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with JVM-Blade.  If not, see <http://www.gnu.org/licenses/>.


package com.rocketnia.blade.tests

import com.rocketnia.blade.*
import com.rocketnia.blade.declare.*
import com.rocketnia.blade.parse.*


println "Beginning BladeTestSandbox"

def lines = []
new InputStreamReader(
	getClass().getResourceAsStream( "/resource.txt" ),
	"UTF-8"
).eachLine { lines.add it }

println lines.inspect()

for ( line in ListDocument.of( "\n hey \n\t\tyou\n\n\n\n\n" ) )
	println LineLocation.of( line ).prefixes().reverse()

println BladeParser.parseBrackets( "\n[]a\nbc\t[d[\ne]f\n]ghi[]\n" )

println BladeParser.parseParagraphedBrackets( '''

 D'oh! This isn't in a paragraph, since it's indented. Instead, it's
an error.

[This paragraph] [has [tree-like structure]] using [brackets].
This part of the paragraph is ignored, and so is "using".

This begins a new, non-[] paragraph, which is ignored.

  [This is a continuation of the same non-[] paragraph.]

[ This paragraph yields an error.

] This paragraph is an independent non-[] paragraph, not a
continuation of the above paragraph.

[]] This paragraph yields an error as well.

''' )

println BladeParser.parseProject(
	BladeTests.getResourceFile( "/bladeproject" ) )

println BladeParser.parseProject(
	BladeTests.getResourceFile( "/bladeproject/something.blade" ) )

def bladeCore = { File projectFile ->
	
	Set parsedProject = BladeParser.parseProject( projectFile )
	
	Blade bladeIso = BuiltIn.of { List< Blade > args ->
		
		// TODO: Support more type-specific behavior.
		// TODO: Support extending this from within Blade.
		
		if ( args.size() != 2 )
			return new CalcErr( error: BladeString.of(
					"Expected 2 arguments to iso and got"
				 + " ${args.size()}." ) )
		
		def ( arg0, arg1 ) = args
		
		def result
		if ( arg0 in BuiltIn && arg1 in BuiltIn )
			result = ((BuiltIn)arg0).getValue().equals(
				((BuiltIn)arg1).getValue() )
		else
			result = args[ 0 ] == args[ 1 ]
		
		return new CalcResult( value: BuiltIn.of( result ) )
	}
	
	Closure bladeDefinitionIsoMaker = { Closure getRef -> bladeIso }
	Closure bladeReducerIsoMaker = { Closure getRef -> bladeIso }
	
	Closure bladeTruthyInteractive = { Blade blade, Closure getRef ->
		
		// TODO: Support more type-specific behavior.
		// TODO: Support extending this from within Blade.
		
		if ( blade in BuiltIn
			&& ((BuiltIn)blade).getValue() == false )
			return false
		
		return true
	}
	
	Blade namespaceReducer =
		[ toString: { "namespaceReducer" } ] as Blade
	Blade sigBase = [ toString: { "sigBase" } ] as Blade
	
	
	Closure calcCall = { Blade fnRef, List< Blade > args ->
		
		// TODO: Support more type-specific behavior.
		// TODO: support extending this from within Blade, all the
		// while maintaining the semantics that the fn here should act
		// as a pure function that returns only Calcs.
		
		return BuiltIn.hardAsk( fnRef ) { fn ->
			
			if ( fn in BuiltIn )
			{
				def value = ((BuiltIn)fn).getValue()
				if ( value in Closure )
				{
					def result = ((Closure)value).call( args )
					assert result in Calc
					return result
				}
			}
			
			return new CalcErr( error: BladeString.of(
					"Tried to invoke something other than a built-in"
				 + " function." ) )
		}
	}
	
	// Note that this reducer produces BladeMultisets but never
	// produces an empty BladeMultiset, since contribution sets are
	// never empty. (We don't specify a reducer until we contribute.)
	// For general-purpose multiset construction, a reducer that
	// appends its contributions would be a better choice.
	Blade contribReducer = BuiltIn.of { List< Blade > args ->
		
		if ( args.size() != 1 )
			return new CalcErr( error: BladeString.of(
					"Expected 1 argument to contribReducer and got"
				 + " ${args.size()}." ) )
		
		// If not for the type sanity check here, the call to hardAsk
		// could be avoided and the CalcResult could be returned
		// right here.
		return BuiltIn.hardAsk( args.head() ) { contribs ->
			
			if ( !(contribs in BladeMultiset) )
				return new CalcErr( error: BladeString.of(
						"The argument to contribReducer wasn't a"
					 + " multiset." ) )
			
			return new CalcResult( value: contribs )
		}
	}
	
	Closure sigFromList = { List< String > derivs -> derivs.
		inject sigBase, { p, d -> new Sig(
			parent: p, derivative: BladeString.of( d ) ) } }
	
	Closure sig =
		{ String... derivs -> sigFromList( derivs as List ) }
	
	Blade interpretDeclaration = BuiltIn.of { List< Blade > args ->
		
		if ( args.size() != 1 )
			return new CalcErr( error: BladeString.of(
					"Expected 1 argument to interpretDeclaration and"
				 + " got ${args.size()}." ) )
		
		return BuiltIn.hardAsk( args.head() ) { declaration ->
			
			if ( !(declaration in BracketView) )
				return new CalcErr( error: BladeString.of(
						"The argument to interpretDeclaration wasn't"
					+ " a BracketView." ) )
			
			def view = (BracketView)declaration
			
			def brackets = view.brackets
			
			DocumentSelection firstSelection = brackets.head()
			
			List firstLines =
				Documents.contents( view.doc, firstSelection )
			
			if ( firstLines.isEmpty() )
				return new CalcResult( value: new LeadEnd() )
			
			String firstPart = firstLines.join( '\n' )
			
			def headWordMatch = firstPart =~ /^\s*(\S+)\s/
			
			if ( !headWordMatch )
				return new CalcResult( value: new LeadEnd() )
			
			def ( String headSpace, String headWord ) =
				headWordMatch[ 0 ]
			
			def newlineMatch = headSpace =~ /^.+\n([^\n]+)$/
			
			def line = firstSelection.start.lineNumber
			def linePos
			if ( newlineMatch )
			{
				line += (headSpace =~ /\n/).size()
				linePos = LineLocation.of( newlineMatch[ 0 ][ 1 ] )
			}
			else
			{
				linePos = LineLocation.of( headSpace )
			}
			
			def newFirstSelection = DocumentSelection.
				from( line, linePos ).to( firstSelection.stop )
			
			def newBrackets = [ newFirstSelection ] + brackets.tail()
			
			def newView = new BracketView(
				path: view.path, doc: view.doc, brackets: newBrackets
			)
			
			return BuiltIn.softAsk(
				sig( "ext", "blade", headWord ) ) { (
				
				new CalcCalc( calc: calcCall( it, [ newView ] ) )
			) }
		}
	}
	
	Set< Lead > initialLeads = []
	
	for ( declaration in parsedProject )
	{
		if ( declaration in ErrorSelection )
			initialLeads.add new LeadErr(
				error: BuiltIn.of( declaration ) )
		else
			initialLeads.add new LeadCalc( calc:
				calcCall( interpretDeclaration, [ declaration ] ) )
	}
	
	// If the Blade program makes no explicit contributions and we
	// don't make any automatic ones, then sigBase won't have any
	// contributions, and it won't be able to reduce.
	//
	// Since we are in fact making at least these two automatic
	// contributions, we don't have to worry about that.
	
	initialLeads.add new LeadDefine(
		sig: sig( "ext", "blade", "blade" ),
		calc: new CalcResult( value:
			BuiltIn.of { List< Blade > args ->
				
				if ( args.size() != 1 )
					return new CalcErr( error: BladeString.of(
							'Expected 1 argument to the "blade"'
						+ " top-level op and got ${args.size()}." ) )
				
				return BuiltIn.hardAsk( args.head() ) { declaration ->
					
					if ( !(declaration in BracketView) )
						return new CalcErr( error: BladeString.of(
								'The argument to the "blade"'
							+ " top-level op wasn't a BracketView."
						) )
					
					def view = (BracketView)declaration
					def doc = view.doc
					
					def header = []
					def body = []
					
					def inBody = false
					
					for ( elem in view.brackets )
					{
						if ( inBody )
							body.add elem
						else if ( elem in List )
							header.add elem
						else
						{
							def selection = (DocumentSelection)elem
							
							def start = selection.start
							def stop = selection.stop
							def startLine = start.lineNumber
							def stopLine = stop.lineNumber
							
							if ( startLine == stopLine )
								header.add elem
							else
							{
								inBody = true
								header.add DocumentSelection.
									from( start ).
									to( doc[ startLine ] )
								body.add DocumentSelection.
									from( startLine + 1 ).to( stop )
							}
						}
					}
					
					if ( !inBody )
						return new CalcErr( error: BladeString.of(
								'A "blade" top-level operation must'
							 + " have a sig line followed by a body"
							 + " to pass to the value of that sig."
						) )
					
					def errors = []
					
					def siggedHeader = []
					for ( headerPart in header )
					{
						if ( headerPart in DocumentSelection )
						{
							def contents =
								Documents.contents( doc, headerPart )
							
							siggedHeader.addAll contents[ 0 ].
								split( /[ \t]+/ ).
								findAll { !it.isEmpty() }
						}
						else if ( headerPart.size() != 1 )
						{
							errors.add DocumentSelection.
								from( headerPart.first().start ).
								to( headerPart.last().end )
						}
						else
						{
							def selection = headerPart[ 0 ]
							def contents =
								Documents.contents( doc, selection )
							
							if ( contents.size() != 1 )
								errors.add selection
							else
								siggedHeader.add contents[ 0 ]
						}
					}
					
					// TODO: Make this yield ErrorSelections somehow.
					if ( !errors.isEmpty() )
						return new CalcErr( error: BladeString.of(
								'Parts of the sig in a "blade"'
							 + " top-level operation had multiple"
							 + " levels of bracket nesting: $errors"
						) )
					
					def bodyView = new BracketView(
						path: view.path, doc: doc, brackets: body )
					
					return BuiltIn.softAsk(
						sigFromList( siggedHeader ) ) {
						
						return new CalcResult(
							value: calcCall( it, [ bodyView ] ) )
					}
				}
			}
		),
		next: BuiltIn.of { new CalcResult( value: new LeadEnd() ) }
	)
	
	initialLeads.add new LeadDefine(
		sig: sig( "impl", "jvm-blade", "groovy-eval" ),
		calc: new CalcResult( value:
			BuiltIn.of { List< Blade > args ->
				
				if ( args.size() != 1 )
					return new CalcErr( error: BladeString.of(
							"Expected 1 argument to groovy-eval and"
						 + " got ${args.size()}." ) )
				
				return BuiltIn.hardAsk( args.head() ) { declaration ->
					
					if ( !(declaration in BracketView) )
						return new CalcErr( error: BladeString.of(
								"The argument to groovy-eval wasn't a"
							 + " BracketView." ) )
					
					def view = (BracketView)declaration
					def doc = view.doc
					def brackets = view.brackets
					
					def stringContents = Documents.contents(
						doc,
						DocumentSelection.
							from( brackets.first().start ).
							to( brackets.last().stop )
					).join( System.getProperty( "line.separator" ) )
					
					return new CalcResult(
						value: Eval.me( stringContents ) )
				}
			}
		),
		next: BuiltIn.of { new CalcResult( value: new LeadEnd() ) }
	)
	
	return TopLevel.bladeTopLevel(
		initialLeads, bladeDefinitionIsoMaker, bladeReducerIsoMaker,
		bladeTruthyInteractive, calcCall, namespaceReducer, sigBase )
}

// This should have a rather empty result; resource.txt isn't even a
// .blade file, so it will be completely ignored. However, the result
// will still contain the things that are automatically contributed.
println bladeCore( BladeTests.getResourceFile( "/resource.txt" ) )

// For now, this should have an empty result too, since it only
// contains one declaration and that one doesn't contribute anything.
println bladeCore( BladeTests.getResourceFile( "/workingproject" ) )

println "Finishing BladeTestSandbox"
