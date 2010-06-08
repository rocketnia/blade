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
		Set parsedProject = BladeParser.parseProject( root )
		
		Blade sigBase = [ toString: { "sigBase" } ] as Blade
		
		Ref refBase
		
		Closure calcCall = { Blade fnRef, List< Blade > args ->
			
			// TODO: Support more type-specific behavior.
			// TODO: Support extending this from within Blade, all the
			// while maintaining the semantics that the fn here should
			// act as a pure function that returns only Calcs.
			
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
						"Tried to invoke something other than a"
					 + " built-in function." ) )
			}
		}
		
		return TopLevel.bladeTopLevel( sigBase, calcCall ) {
			
			refBase = it
			
			Closure mySoftAsk =
				{ List< String > derivs, Closure body -> BuiltIn.
					softAsk(
						refBase,
						derivs.collect( BladeString.&of ),
						body
					) }
			
			Set< Lead > initialLeads = []
			
			Closure myLeadDefine =
				{ List< String > derivs, Blade value ->
				
				return mySoftAsk( derivs ) { new LeadDefine(
					target: new ReflectedRef( ref: it ),
					value: value,
					next: BuiltIn.
						of { new CalcResult( value: new LeadEnd() ) }
				) }
			}
			
			Closure myDefine = { List< String > derivs, Blade value ->
				
				initialLeads.add myLeadDefine( derivs, value )
			}
			
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
						
						def brackets = view.brackets
						
						DocumentSelection firstSelection =
							brackets.head()
						
						List firstLines = Documents.
							contents( view.doc, firstSelection )
						
						if ( firstLines.isEmpty() )
							return new CalcResult(
								value: new LeadEnd() )
						
						String firstPart = firstLines.join( '\n' )
						
						def headWordMatch = firstPart =~ /^\s*(\S+)\s/
						
						if ( !headWordMatch )
							return new CalcResult(
								value: new LeadEnd() )
						
						def ( String headSpace, String headWord ) =
							headWordMatch[ 0 ]
						
						def newlineMatch =
							headSpace =~ /^.+\n([^\n]*)$/
						
						def line = firstSelection.start.lineNumber
						def linePos
						if ( newlineMatch )
						{
							line += (headSpace =~ /\n/).getCount()
							linePos = LineLocation.
								of( newlineMatch[ 0 ][ 1 ] )
						}
						else
						{
							linePos = LineLocation.of( headSpace )
						}
						
						def newFirstSelection = DocumentSelection.
							from( line, linePos ).
							to( firstSelection.stop )
						
						def newBrackets =
							[ newFirstSelection ] + brackets.tail()
						
						def newView = new BracketView(
							path: view.path,
							doc: view.doc,
							brackets: newBrackets
						)
						
						return new CalcResult( value: mySoftAsk(
							[ "ext", "blade", headWord ] ) {
								
								return new LeadCalc( calc:
									calcCall( it, [ newView ] ) )
							} )
					}
				}
			
			for ( declaration in parsedProject )
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
			// Since we are in fact making at least these two
			// automatic contributions, we don't have to worry about
			// that.
			
			myDefine(
				[ "ext", "blade", "blade" ],
				BuiltIn.of { List< Blade > args ->
					
					if ( args.size() != 1 )
						return new CalcErr( error: BladeString.of(
								'Expected 1 argument to the "blade"'
							 + " top-level op and got ${args.size()}."
						) )
					
					return BuiltIn.hardAsk(
						args.head() ) { declaration ->
						
						if ( !(declaration in BracketView) )
							return new CalcErr( error: BladeString.of(
									'The argument to the "blade"'
								 + " top-level op wasn't a"
								 + " BracketView." ) )
						
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
								def selection =
									(DocumentSelection)elem
								
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
										from( startLine + 1 ).
										to( stop )
								}
							}
						}
						
						if ( !inBody )
							return new CalcErr( error: BladeString.of(
									'A "blade" top-level operation'
								 + " must have a sig line followed by"
								 + " a body to pass to the value of"
								 + " that sig." ) )
						
						def errors = []
						
						def siggedHeader = []
						for ( headerPart in header )
						{
							if ( headerPart in DocumentSelection )
							{
								def contents = Documents.
									contents( doc, headerPart )
								
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
								def contents = Documents.
									contents( doc, selection )
								
								if ( contents.size() != 1 )
									errors.add selection
								else
									siggedHeader.add contents[ 0 ]
							}
						}
						
						// TODO: Make this yield ErrorSelections
						// somehow.
						if ( !errors.isEmpty() )
							return new CalcErr( error: BladeString.of(
									'Parts of the sig in a "blade"'
								 + " top-level operation had multiple"
								 + " levels of bracket nesting:"
								 + " $errors" ) )
						
						def bodyView = new BracketView(
							path: view.path,
							doc: doc,
							brackets: body
						)
						
						return new CalcResult( value:
							mySoftAsk( siggedHeader ) { new LeadCalc(
								calc: calcCall( it, [ bodyView ] ) ) }
						)
					}
				}
			)
			
			myDefine(
				[ "impl", "jvm-blade", "groovy-eval" ],
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
								define: myLeadDefine
							] ) ).evaluate( stringContents ) )
					}
				}
			)
			
			return initialLeads
		}
	}
}
