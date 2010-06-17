// Builder.groovy
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


package com.rocketnia.blade.build

import com.rocketnia.blade.*
import com.rocketnia.blade.declare.*
import com.rocketnia.blade.parse.*


final class Builder
{
	private Builder() {}
	
	static Blade build( File root )
	{
		def parsedProject = BladeParser.parseProject( root )
		
		Blade sigBase = [ toString: { "sigBase" } ] as BladeKey
		
		Ref refBase
		
		def PURE = DynamicEnv.PURE
		Closure calcCall
		calcCall = { Blade fnRef, List< Blade > args,
			DynamicEnv dynamicEnv = PURE ->
			
			// TODO: Support more type-specific behavior.
			// TODO: Support extending this from within Blade, all the
			// while maintaining the semantics that the fn here should
			// act as a pure function that returns only Calcs.
			
			while ( true )
			{
				Blade fn = Refs.derefSoft( fnRef )
				
				if ( fn in Ref )
					return BuiltIn.hardAsk( fn ) { calcCall it, args }
				
				if ( fn in BuiltIn )
				{
					def value = ((BuiltIn)fn).getValue()
					if ( value in Closure )
					{
						value = (Closure)value
						
						def result
						def hasResult = true
						
						switch (
							value.getMaximumNumberOfParameters() )
						{
						case 1: result = value( args ); break
						case 2:
							result = value( args, dynamicEnv ); break
						default: hasResult = false; break
						}
						
						if ( result in Calc )
							return result
						
						if ( result in TrampolineCalcCall )
						{
							result = (TrampolineCalcCall)result
							fnRef = result.fn
							args = result.args
							dynamicEnv = result.dynamicEnv
							continue
						}
						
						assert !hasResult
					}
				}
				
				return new CalcErr( error: BladeString.of(
						"Tried to invoke something other than a"
					 + " built-in function." ) )
			}
		}
		
		return TopLevel.bladeTopLevel( sigBase, calcCall ) {
			
			refBase = it
			
			Closure mySoftAsk =
				{ List derivs, Closure body -> BuiltIn.softAsk(
					refBase,
					derivs.collect {
						
						switch ( it )
						{
						case Blade: return it
						case String: return BladeString.of( it )
						default: throw new IllegalArgumentException()
						}
					},
					body
				) }
			
			Set< Lead > initialLeads = []
			
			Closure myLeadDefine = { List derivs, Blade value ->
				
				return mySoftAsk( derivs ) { new LeadDefine(
					target: new ReflectedRef( ref: it ),
					value: value,
					next: BuiltIn.
						of { new CalcResult( value: new LeadEnd() ) }
				) }
			}
			
			Closure myDefine = { List derivs, Blade value ->
				
				initialLeads.add myLeadDefine( derivs, value )
			}
			
			def topLevelOpToken =
				[ toString: { "top-level-op-token" } ] as BladeKey
			
			Blade interpretDeclaration =
				BuiltIn.of { List< Blade > args ->
					
					if ( args.size() != 1 )
						return new CalcErr( error: BladeString.of(
								"Expected 1 argument to"
							 + " interpretDeclaration and got"
							 + " ${args.size()}." ) )
					
					return BuiltIn.hardAsk(
						args.head() ) { declaration ->
						
						if ( !(declaration in BracketView) )
							return new CalcErr( error: BladeString.of(
									"The argument to"
								 + " interpretDeclaration wasn't a"
								 + " BracketView." ) )
						
						def view = (BracketView)declaration
						
						def path = view.path
						def doc = view.doc
						
						def ( List token, List body ) =
							BracketUtils.splitOffFirstToken(
								doc, view.brackets )
						
						if ( token.size() != 1 || null.is( body ) )
							return new CalcResult(
								value: new LeadEnd() )
						
						def headWord =
							Documents.contents( doc, token[ 0 ] )[ 0 ]
						
						def newView = new BracketView(
							path: path, doc: doc, brackets: body )
						
						return new CalcResult( value: mySoftAsk(
							[ "model", "blade", path, "private",
								topLevelOpToken, headWord ] ) {
								
								return new LeadCalc( calc:
									calcCall( it, [ newView ] ) )
							} )
					}
				}
			
			def bladeTopLevelOp = BuiltIn.of { List< Blade > args ->
				
				if ( args.size() != 1 )
					return new CalcErr( error: BladeString.of(
							'Expected 1 argument to the "blade"'
						 + " top-level op and got ${args.size()}." ) )
				
				return BuiltIn.hardAsk(
					args.head() ) { declaration ->
					
					if ( !(declaration in BracketView) )
						return new CalcErr( error: BladeString.of(
								'The argument to the "blade"'
							 + " top-level op wasn't a BracketView."
						) )
					
					def view = (BracketView)declaration
					def doc = view.doc
					
					def ( List header, middle, List body ) =
						BracketUtils.splitAtFirst(
							doc, view.brackets, '\n' )
					
					if ( null.is( body ) )
						return new CalcErr( error: BladeString.of(
								'A "blade" top-level operation must'
							 + " have a sig line followed by a body"
							 + " to pass to the value of that sig."
						) )
					
					def errors = []
					
					def siggedHeader = []
					
					for ( headerPart in
						BracketUtils.tokens( doc, header ) )
					{
						def headerSize = headerPart.size()
						def first = headerPart.first()
						def last = headerPart.last()
						
						if ( headerSize == 1 )
						{
							siggedHeader.add Documents.
								contents( doc, first )[ 0 ]
							continue
						}
						
						def inner = headerPart[ 1 ]
						def innerSelection = inner[ 0 ]
						if ( !(
							headerSize == 3 && inner.size() == 1
							&& innerSelection.linesSpanned() == 1
							&& first.isEmpty() && last.isEmpty()
						) )
							errors.add DocumentSelection.
								from( first.start ).to( last.stop )
						else
							siggedHeader.add Documents.
								contents( doc, innerSelection )[ 0 ]
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
					
					return new CalcResult( value:
						mySoftAsk( siggedHeader ) { new LeadCalc(
							calc: calcCall( it, [ bodyView ] ) ) }
					)
				}
			}
			
			for ( declaration in parsedProject.values().sum( [] ) )
			{
				if ( declaration in ErrorSelection )
					initialLeads.add new LeadErr(
						error: BuiltIn.of( declaration ) )
				else
					initialLeads.add new LeadCalc( calc: calcCall(
						interpretDeclaration, [ declaration ] ) )
			}
			
			// If the Blade program makes no explicit contributions
			// and we don't make any automatic ones, then refBase
			// won't have any contributions, and it won't be able to
			// resolve.
			//
			// Since we are in fact making a bunch of automatic
			// contributions, we don't have to worry about that.
			
			myDefine(
				[ "base", "blade", "exports", "top-level-op-token" ],
				topLevelOpToken
			)
			
			for ( path in parsedProject.keySet() )
			{
				myDefine(
					[ "model", "blade", path, "private",
						topLevelOpToken, "blade" ],
					bladeTopLevelOp
				)
			}
			
			myDefine(
				[ "base", "blade", "exports", "top-level-ops",
					"blade" ],
				bladeTopLevelOp
			)
			
			myDefine(
				[ "base", "jvm-blade", "exports", "groovy-eval" ],
				BuiltIn.of { List< Blade > args ->
					
					if ( args.size() != 1 )
						return new CalcErr( error: BladeString.of(
								"Expected 1 argument to groovy-eval"
							 + " and got ${args.size()}." ) )
					
					return BuiltIn.hardAsk(
						args[ 0 ] ) { declaration ->
						
						if ( !(declaration in BracketView) )
							return new CalcErr( error: BladeString.of(
									"The argument to groovy-eval"
								 + " wasn't a BracketView." ) )
						
						def view = (BracketView)declaration
						def doc = view.doc
						def brackets = view.brackets
						
						def stringContents = Documents.contents(
							doc,
							DocumentSelection.
								from( brackets.first().start ).
								to( brackets.last().stop )
						).join( '\n' )
						
						return new CalcResult( value:
							new GroovyShell( new Binding( [
								calcCall: calcCall,
								refBase: refBase,
								softAsk: mySoftAsk,
								define: myLeadDefine,
								path: view.path
							] ) ).evaluate( stringContents ) )
					}
				}
			)
			
			return initialLeads
		}
	}
}
