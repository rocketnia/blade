// Leads.groovy
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


abstract class Lead extends RefMap {}

// A request for the source map Ref to have the given key. The value
// isn't needed yet, so it can be filled in later using mutation. The
// next field is a nullary Blade function that will return a new Lead.
class LeadSoftAsk extends Lead {
	Ref source
	Blade getKey() { get "key" }
	Blade setKey( Blade val ) { set "key", val }
	Blade getNext() { get "next" }
	Blade setNext( Blade val ) { set "next", val }
}

// A definition of a target Ref as value. The next field is a nullary
// Blade function that will return a new Lead.
class LeadDefine extends Lead {
	Ref target
	Blade getValue() { get "value" }
	Blade setValue( Blade val ) { set "value", val }
	Blade getNext() { get "next" }
	Blade setNext( Blade val ) { set "next", val }
}

// A contribution of value to a target Ref multiset. The next field is
// a nullary Blade function that will return a new Lead. Note that
// value can be a soft reference.
class LeadBagContrib extends Lead {
	Ref target
	Blade getValue() { get "value" }
	Blade setValue( Blade val ) { set "value", val }
	Blade getNext() { get "next" }
	Blade setNext( Blade val ) { set "next", val }
}

// A promise not to contribute to any Ref with a sig that doesn't
// satisfy the filter, even by just a LeadSoftAsk for a new child of
// that Ref. The next field is a nullary Blade function that will
// return a new Lead.
class LeadPromise extends Lead {
	Blade getFilter() { get "filter" }
	Blade setFilter( Blade val ) { set "filter", val }
	Blade getNext() { get "next" }
	Blade setNext( Blade val ) { set "next", val }
}

// The result of a lead that has errored out. Note that this has no
// next continuation.
class LeadErr extends Lead {
	Blade getError() { get "error" }
	Blade setError( Blade val ) { set "error", val }
}

// The result of a lead that has run its course. Note that this has no
// next continuation.
class LeadEnd extends Lead {}

// A lead which will continue according to whatever Lead object is
// returned from a Blade calculation.
class LeadCalc extends Lead {
	Blade getCalc() { get "calc" }
	Blade setCalc( Blade val ) { set "calc", val }
}

// A lead which will continue as two separate leads.
class LeadSplit extends Lead {
	Blade getFirst() { get "first" }
	Blade setFirst( Blade val ) { set "first", val }
	Blade getSecond() { get "second" }
	Blade setSecond( Blade val ) { set "second", val }
}


final class Leads
{
	private Leads() {}
	
	// This returns a two-element list containing a Lead and a boolean
	// indicating whether any advancement actually happened. The Lead
	// will be either a LeadEnd, a LeadSplit, a LeadSoftAsk, a
	// LeadDefine, a LeadBagContrib, or a LeadCalc whose inner Calc is
	// a valid result for
	// { a, b -> Calcs.advanceCalcRepeatedly( a, b )[ 0 ] }. However,
	// it will only be a LeadSoftAsk, a LeadDefine, or a
	// LeadBagContrib if none of the lead's promises are known to be
	// broken and and at least one of them requires an unsatisfied
	// hard ask.
	//
	// The bladeTruthy parameter should be a closure that accepts a
	// Blade value and returns either true, false, or a hard-asked-for
	// ref.
	//
	// TODO: See if the errors here would be better as LeadErr values
	// instead.
	//
	// TODO: See if reduction type errors and definition errors can be
	// avoided.
	//
	static List advanceLeadRepeatedly( Lead lead, Closure calcCall,
		Closure addPromise, Closure getPromises, Closure bladeTruthy )
	{
		def harden = { [
			new LeadCalc( calc: new CalcHardAsk(
				ref: it, next: BuiltIn.of { lead } ) ),
			true
		] }
		
		def satisfiesPromises = { sig ->
			
			for ( filter in getPromises() )
			{
				def ( Calc advanced, did ) =
					Calcs.advanceCalcRepeatedly(
						calcCall( filter, [ sig ] ), calcCall )
				
				if ( advanced in CalcHardAsk )
					return null
				
				def truth = bladeTruthy(
					((CalcResult)advanced).getValue() )
				
				if ( truth == false )
					return false
				else if ( truth != true )
					return null
			}
			
			return true
		}
		
		for ( boolean didAnything = false; ; didAnything = true )
		{
			switch ( lead )
			{
			case LeadEnd:
			case LeadSplit:
				return [ lead, didAnything ]
				
			case LeadErr:
				def error = ((LeadErr)lead).getError()
				if ( error in Ref )
					return harden( error )
				
				throw new RuntimeException(
					"A lead resulted in this error: $error" )
				
			case LeadSoftAsk:
				def lead2 = (LeadSoftAsk)lead
				
				def key = lead2.getKey()
				if ( key in Ref )
					return harden( key )
				
				def source = lead2.source
				
				if ( !source.canGetFromMap() )
					throw new RuntimeException(
						"A reduction type conflict occurred." )
				
				def works = satisfiesPromises( source.sig )
				if ( works == false )
					throw new RuntimeException(
							"A lead broke a promise not to"
						 + " contribute to this sig:"
						 + " ${source.sig}" )
				else if ( works != true )
					return [ lead, didAnything ]
				
				source.getFromMapHard( key )
				
				lead = new LeadCalc(
					calc: calcCall( lead2.getNext(), [] ) )
				break
				
			case LeadDefine:
				def lead2 = (LeadDefine)lead
				
				def value = lead2.getValue()
				if ( value in Ref )
					return harden( value )
				
				def target = lead2.target
				
				if ( target.isResolved() )
				{
					if ( !value.is( target ) )
						throw new RuntimeException(
							"A definition conflict occurred." )
				}
				else
				{
					if ( target.isFinishable() )
						throw new RuntimeException(
							"A reduction type conflict occurred." )
					
					def works = satisfiesPromises( target.sig )
					
					if ( works == false )
						throw new RuntimeException(
								"A lead broke a promise not to"
							 + " contribute to this sig:"
							 + " ${target.sig}" )
					else if ( works != true )
						return [ lead, didAnything ]
					
					((Ref)target).resolveTo value
				}
				
				lead = new LeadCalc(
					calc: calcCall( lead2.getNext(), [] ) )
				break
				
			case LeadBagContrib:
				def lead2 = (LeadBagContrib)lead
				
				def target = lead2.target
				
				def works = !target.isResolved()
				if ( works )
				{
					if ( !target.couldBePartialBag() )
						throw new RuntimeException(
							"A reduction type conflict occurred." )
					
					works = satisfiesPromises( target.sig )
				}
				
				if ( works == false )
					throw new RuntimeException(
							"A lead broke a promise not to contribute"
						 + " to this sig: ${((Ref)target).sig}" )
				else if ( works != true )
					return [ lead, didAnything ]
				
				((Ref)target).addToBag lead2.getValue()
				
				lead = new LeadCalc(
					calc: calcCall( lead2.getNext(), [] ) )
				break
				
			case LeadPromise:
				def lead2 = (LeadPromise)lead
				addPromise lead2.getFilter()
				lead = calcCall( lead2.getNext(), [] )
				break
				
			case LeadCalc:
				def initialInnerCalc = ((LeadCalc)lead).getCalc()
				switch ( initialInnerCalc )
				{
				case Ref: return harden( initialInnerCalc )
					
				case CalcResult:
					def value =
						((CalcResult)initialInnerCalc).getValue()
					
					if ( value in Ref )
						return harden( value )
					
					if ( !(value in Lead) )
						throw new RuntimeException(
							   "A LeadCalc's inner result wasn't a"
							+ " Lead." )
					
					lead = value
					break
					
				default:
					// TODO: Figure out the best way to treat inner
					// errors with respect to their outer
					// calculations.
					def ( finalInnerCalc, innerDid ) =
						Calcs.advanceCalcRepeatedly(
							initialInnerCalc, calcCall )
					
					if ( !innerDid )
						return [ lead, didAnything ]
					
					lead = new LeadCalc( calc: finalInnerCalc )
					break
				}
				break
				
			default: throw new RuntimeException(
				"An unknown Lead type was encountered." )
			}
		}
	}
}
