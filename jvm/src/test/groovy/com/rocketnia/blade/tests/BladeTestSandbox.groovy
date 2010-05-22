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

class BracketChunk
{
	String path
	List bracketSelections
	
	BracketChunk( String path, List bracketSelections )
		{ this( path: path, bracketSelections: bracketSelections ) }
	
	private isoRep() { [ BracketChunk, path, bracketSelections ] }
	int hashCode() { isoRep().hashCode() }
	boolean equals( Object other ) { !null.is( other ) && Misc.
		let { Class c = owner.class, oc = other.class -> c.is( oc ) ?
			((BracketChunk)other).isoRep().equals( isoRep() ) :
			c.isAssignableFrom( oc ) && other.equals( this ) }
	}
}

def bladeCore = { File projectFile ->
	
	Map< String, List > parsedProject =
		BladeParser.parseProject( projectFile )
	
	Blade bladeReducerIso = BuiltIn.of { List< Blade > args ->
		
		// TODO: Support more type-specific behavior.
		// TODO: Support extending this from within Blade.
		
		if ( args.size() != 2 )
			return new CalcErr( error: Builtin.of(
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
	
	
	Blade interpretDeclaration = BuiltIn.of { List< Blade > args ->
		
		if ( args.size() != 1 )
			return new CalcErr( error: Builtin.of(
					"Expected 1 argument to interpretDeclaration and"
				 + " got ${args.size()}." ) )
		
		def ( arg ) = args
		
		// TODO: Make declarations more meaningful than just
		// non-errors.
		return new CalcResult( value: new LeadEnd() )
	}
	
	Set< Lead > initialLeads = []
	parsedProject.each { path, declarations ->
		
		for ( declaration in declarations )
		{
			if ( declaration in ErrorSelection )
				initialLeads.add new LeadErr(
					error: BuiltIn.of( declaration ) )
			else
				initialLeads.add calcCall( interpretDeclaration, [
					new BracketChunk( path, declaration ) ] )
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
		sig: new Sig(
			parent: sigBase, derivative: BuiltIn.of( "sample-var" ) ),
		reducer: BuiltIn.of { new CalcResult(
			value: BuiltIn.of( "sample-value" ) ) },
		value: BuiltIn.of( null ),
		next: BuiltIn.of { new CalcResult( value: new LeadEnd() ) }
	)
	
	return TopLevel.bladeTopLevel(
		initialLeads, bladeReducerIsoMaker, bladeTruthyInteractive,
		calcCall, namespaceReducer, sigBase )
}

// This should have a rather empty result; resource.txt isn't even a
// .blade file, so it will be completely ignored.
println bladeCore( BladeTests.getResourceFile( "/resource.txt" ) )

println "Finishing BladeTestSandbox"
