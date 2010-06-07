// Calcs.groovy
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


package com.rocketnia.blade.declare

import com.rocketnia.blade.*


abstract class Calc extends RefMap {}

// A demand for the given constant ReflectedRef to be resolved. The
// next field is a Blade function that will take the resolved value of
// the ref and return a new Calc.
class CalcHardAsk extends Calc {
	Blade getRef() { get "ref" }
	Blade setRef( Blade val ) { set "ref", val }
	Blade getNext() { get "next" }
	Blade setNext( Blade val ) { set "next", val }
}

// The result of a calc that has errored out. Note that this has no
// next continuation.
class CalcErr extends Calc {
	Blade getError() { get "error" }
	Blade setError( Blade val ) { set "error", val }
}

// The result of a calc that has run its course. Note that this has no
// next continuation.
class CalcResult extends Calc {
	Blade getValue() { get "value" }
	Blade setValue( Blade val ) { set "value", val }
}

// A calc which will continue according to whatever Calc object is
// returned from a Blade calculation.
class CalcCalc extends Calc {
	Blade getCalc() { get "calc" }
	Blade setCalc( Blade val ) { set "calc", val }
}


final class Calcs
{
	private Calcs() {}
	
	// This returns a two-element list containing a Calc and a boolean
	// indicating whether any advancement actually happened. The Calc
	// will be either a CalcResult, a CalcHardAsk, or a CalcCalc whose
	// inner Calc is also an allowable result. However, it will never
	// be a CalcHardAsk with a currently resolved reference.
	static List advanceCalcRepeatedly( Calc calc, Closure calcCall )
	{
		def originalCalc = calc
		
		def harden = { [
			new CalcHardAsk(
				ref: new ReflectedRef( ref: it ),
				next: BuiltIn.of { calc }
			),
			true
		] }
		
		
		// We avoid recursion here so as to avoid JVM stack overflows.
		// Instead of using recursion (for when calc is a CalcCalc),
		// we do an iterative loop which increments the number of
		// recursions that are happening, and then we do another
		// iterative loop to unwind that pseudo-stack and determine
		// the result.
		
		int recursions = 0
		
		def ( Calc innerResult, boolean innerDid ) = Misc.let {
			
			boolean didAnything = false
			
			while ( true )
			{
				switch ( calc )
				{
				case CalcResult: return [ calc, didAnything ]
					
				case CalcErr:
					def error = ((CalcErr)calc).getError()
					if ( error in Ref )
						return harden( error )
					
					throw new RuntimeException(
						   "A calculation resulted in this error:"
						+ " $error" )
					
				case CalcHardAsk:
					def calc2 = (CalcHardAsk)calc
					
					def reflectedRef = calc2.getRef()
					if ( reflectedRef in Ref )
						return harden( reflectedRef )
					
					if ( !(reflectedRef in ReflectedRef) )
						throw new RuntimeException(
								"The ref of a CalcHardAsk wasn't a"
							 + " ReflectedRef." )
					
					def value =
						((ReflectedRef)reflectedRef).ref.derefSoft()
					
					if ( value in Ref )
						return [ calc, didAnything ]
					
					calc = new CalcCalc(
						calc: calcCall( calc2.getNext(), [ value ] ) )
					
					break
					
				case CalcCalc:
					def initialInnerCalc = ((CalcCalc)calc).getCalc()
					switch ( initialInnerCalc )
					{
					case Ref: return harden( initialInnerCalc )
						
					case CalcResult:
						def value =
							((CalcResult)initialInnerCalc).getValue()
						
						if ( value in Ref )
							return harden( value )
						
						// TODO: See if this would be better as a
						// CalcErr instead.
						if ( !(value in Calc) )
							throw new RuntimeException(
								   "A CalcCalc's inner result wasn't"
								+ " a Calc." )
						
						calc = value
						break
						
					default:
						// TODO: Figure out the best way to treat
						// inner errors with respect to their outer
						// calculations.
						calc = initialInnerCalc
						recursions++
						
						// This continue avoids "didAnything = true"
						// below.
						continue
					}
					break
					
				default: throw new RuntimeException(
					"An unknown Calc type was encountered." )
				}
				
				didAnything = true
			}
		}
		
		if ( !innerDid )
			return [ originalCalc, false ]
		
		recursions.
			times { innerResult = new CalcCalc( calc: innerResult ) }
		
		return [ innerResult, true ]
	}
}
