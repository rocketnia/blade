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

// A request for a reference to the reduced value of sig. The value
// isn't needed yet, so it can be filled in later using mutation. The
// next field is a Blade function that will take the answer and return
// a new Calc.
class CalcSoftAsk extends Calc {
	Blade getSig() { get "sig" }
	Blade setSig( Blade val ) { set "sig", val }
	Blade getNext() { get "next" }
	Blade setNext( Blade val ) { set "next", val }
}

// A demand for the given ref to be resolved. The next field is a
// nullary Blade function that will return a new Calc.
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
	// be a CalcHardAsk for which getRef already returns a filled
	// reference.
	static List advanceCalcRepeatedly(
		Calc calc, Closure calcCall, Closure getRef )
	{
		def originalCalc = calc
		
		def refIsSet = { Refs.isSetDirect getRef( it ) }
		
		def harden = { [
			new CalcHardAsk( ref: it.ref, next: BuiltIn.of { calc } ),
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
					def error = ((CalcErr)calc).error
					if ( error in Ref )
						return harden( ref: error )
					
					throw new RuntimeException(
						   "A calculation resulted in this error:"
						+ " $error" )
					
				case CalcSoftAsk:
					def calc2 = (CalcSoftAsk)calc
					
					def sig = calc2.sig
					def neededRef = Refs.anyNeededRef( sig )
					if ( !null.is( neededRef ) )
						return harden( ref: neededRef )
					
					calc = new CalcCalc( calc:
						calcCall( calc2.next, [ getRef( sig ) ] ) )
					break
					
				case CalcHardAsk:
					def calc2 = (CalcHardAsk)calc
					
					def ref = calc2.ref
					
					if ( !refIsSet( ref ) )
						return [ calc, didAnything ]
					
					calc = new CalcCalc(
						calc: calcCall( calc2.next, [] ) )
					
					break
					
				case CalcCalc:
					def initialInnerCalc = ((CalcCalc)calc).calc
					switch ( initialInnerCalc )
					{
					case Ref: return harden( ref: initialInnerCalc )
						
					case CalcResult:
						def value =
							((CalcResult)initialInnerCalc).value
						
						if ( value in Ref )
							return harden( ref: value )
						
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
