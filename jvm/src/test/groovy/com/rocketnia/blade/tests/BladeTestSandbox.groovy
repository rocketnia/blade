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

class BracketChunk implements Blade
{
	String path
	List bracketSelections
	
	BracketChunk( String path, List bracketSelections )
	{
		this.path = path
		this.bracketSelections = bracketSelections
	}
	
	String toString()
		{ "BracketChunk( ${path.inspect()}, $bracketSelections )" }
}

def bladeCore = { File projectFile ->
	
	Map< String, List > parsedProject =
		BladeParser.parseProject( projectFile )
	
	Blade bladeReducerIso = BuiltIn.of { List< Blade > args ->
		
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
	
	Closure bladeReducerIsoMaker = { Closure getRef ->
		
		return bladeReducerIso
	}
	
	Closure bladeTruthyInteractive = { Blade blade, Closure getRef ->
		
		// TODO: Support more type-specific behavior.
		// TODO: Support extending this from within Blade.
		
		if ( blade in BuiltIn
			&& ((BuiltIn)blade).getValue() == false )
			return false
		
		return true
	}
	
	Closure calcCall = { Blade fn, List< Blade > args ->
		
		// TODO: Support more type-specific behavior.
		// TODO: support extending this from within Blade, all the
		// while maintaining the semantics that the fn here should act
		// as a pure function that returns only Calcs.
		
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
		
		return new CalcErr( error:
				"Tried to invoke something other than a built-in"
			 + " function." )
	}
	
	Blade namespaceReducer =
		[ toString: { "namespaceReducer" } ] as Blade
	Blade sigBase = [ toString: { "sigBase" } ] as Blade
	
	
	def hardAsk = { ref, Closure body ->
		
		def derefed = Refs.derefSoft( ref )
		
		if ( !(derefed in Ref) )
			return body( derefed )
		
		return new CalcHardAsk( ref: derefed, next: BuiltIn.
			of { List< Blade > args ->
				
				if ( args.size() != 0 )
					return new CalcErr( error: BladeString.of(
							"Expected 0 arguments to a CalcHardAsk"
						 + " continuation and got ${args.size()}." ) )
				
				def derefedAgain = Refs.derefSoft( derefed )
				if ( derefedAgain in Ref )
					return new CalcErr( error: BladeString.of(
							"A hard ask was continued before it was"
						 + " fulfilled." ) )
				
				return new CalcResult( value: body( derefedAgain ) )
			}
		)
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
		
		def ( arg ) = args
		
		// If not for the type sanity check here, the call to hardAsk
		// could be avoided and the CalcResult could be returned
		// right here.
		return hardAsk( arg ) { contribs ->
			
			if ( !(contribs in BladeMultiset) )
				return new CalcErr( error: BladeString.of(
						"The argument to contribReducer wasn't a"
					 + " multiset." ) )
			
			return new CalcResult( value: contribs )
		}
	}
	
	Closure sig = { String... derivs -> derivs.inject sigBase,
		{ p, d -> new Sig(
			parent: p, derivative: BladeString.of( d ) ) } }
	
	Blade interpretDeclaration = BuiltIn.of { List< Blade > args ->
		
		if ( args.size() != 1 )
			return new CalcErr( error: BladeString.of(
					"Expected 1 argument to interpretDeclaration and"
				 + " got ${args.size()}." ) )
		
		def ( declaration ) = args
		
		// TODO: Make declarations more meaningful than just
		// contributing to a multiset of declarations.
		return new CalcResult( value: new LeadContrib(
			sig: sig( "declarations" ),
			reducer: contribReducer,
			value: declaration,
			next:
				BuiltIn.of { new CalcResult( value: new LeadEnd() ) }
		) )
	}
	
	Set< Lead > initialLeads = []
	
	parsedProject.each { path, declarations ->
		
		for ( declaration in declarations )
		{
			if ( declaration in ErrorSelection )
				initialLeads.add new LeadErr(
					error: BuiltIn.of( declaration ) )
			else
				initialLeads.add new LeadCalc(
					calc: calcCall( interpretDeclaration, [
						new BracketChunk( path, declaration ) ] ) )
		}
	}
	
	// If the Blade program makes no explicit contributions and we
	// don't make any automatic ones, then sigBase won't have any
	// contributions, and it won't be able to reduce.
	//
	// We are in fact planning to have some automatic contributions,
	// but for now all we have is a sample of one.
	//
	// TODO: Contribute something actually meaningful here.
	//
	initialLeads.add new LeadContrib(
		sig: sig( "sample-var" ),
		reducer: BuiltIn.of { new CalcResult(
			value: BladeString.of( "sample-value" ) ) },
		value: BuiltIn.of( null ),
		next: BuiltIn.of { new CalcResult( value: new LeadEnd() ) }
	)
	
	return TopLevel.bladeTopLevel(
		initialLeads, bladeReducerIsoMaker, bladeTruthyInteractive,
		calcCall, namespaceReducer, sigBase )
}

// This should have a rather empty result; resource.txt isn't even a
// .blade file, so it will be completely ignored. As an effect of
// this, the "declarations" multiset which appears in the results of
// the other lists won't appear at all in this one, since it isn't
// contributed to at all here. Although intuitively a "declarations"
// built-in variable should still exist when it's an empty set,
// these examples don't go to the trouble to achieve that, so the
// absence of the variable is indeed the expected result here.
println bladeCore( BladeTests.getResourceFile( "/resource.txt" ) )

// This is a test with two declarations.
println bladeCore( BladeTests.getResourceFile(
	"/bladeproject/something.blade" ) )

// This is a test with two declarations in one file and one
// declaration in another.
println bladeCore( BladeTests.getResourceFile( "/bladeproject" ) )

println "Finishing BladeTestSandbox"
