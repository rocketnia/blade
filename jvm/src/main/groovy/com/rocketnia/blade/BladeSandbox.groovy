// BladeSandbox.groovy
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


// This file started as almost an exact duplicate of the source to
// com.rocketnia.blade.arcversion.ArcBlade. As development continues,
// the contents of this file should split apart into other, less
// experimental files.


package com.rocketnia.blade


// A sig is a list of values representing a path of namespaces.
// Contributing to a sig is the same as contributing a contribution
// object to the sig's parent, where the sig's parent uses a
// particular reducer which creates a namespace full of multivals out
// of a bunch of contribution objects.
class Sig extends RefMap {
	Blade getDerivative() { get "derivative" }
	Blade setDerivative( Blade val ) { set "derivative", val }
	Blade getParent() { get "parent" }
	Blade setParent( Blade val ) { set "parent", val }
}


abstract class Lead extends RefMap {}

// A contribution of value to sig, expecting reducer to ultimately
// reduce the values. The next field is a nullary Blade function that
// will return a new Lead. Note that value can be a soft reference.
class LeadContrib extends Lead {
	Blade getSig() { get "sig" }
	Blade setSig( Blade val ) { set "sig", val }
	Blade getReducer() { get "reducer" }
	Blade setReducer( Blade val ) { set "reducer", val }
	Blade getValue() { get "value" }
	Blade setValue( Blade val ) { set "value", val }
	Blade getNext() { get "next" }
	Blade setNext( Blade val ) { set "next", val }
}

// A promise not to contribute to any sig that doesn't satisfy the
// filter. The next field is a nullary Blade function that will return
// a new Lead.
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

// A demand for the value of sig to be mutated into the existing soft
// references to it. The next field is a nullary Blade function that
// will return a new Calc.
class CalcHardAsk extends Calc {
	Blade getSig() { get "sig" }
	Blade setSig( Blade val ) { set "sig", val }
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


// This returns a two-element list containing a Calc and a boolean
// indicating whether any advancement actually happened. The Calc will
// be either a CalcResult, a CalcHardAsk, or a CalcCalc whose inner
// Calc is also an allowable result. However, it will never be a
// CalcHardAsk for which getRef already returns a filled reference.
def advanceCalcRepeatedly(
	Calc calc, Closure calcCall, Closure getRef )
{
	def refIsSet = { Refs.isSetDirect getRef( it ) }
	
	def harden = { [
		new CalcHardAsk( sig: it.sig, next: BuiltIn.of { calc } ),
		true
	] }
	
	for ( boolean didAnything = false; ; didAnything = true )
	{
		switch ( calc )
		{
		case CalcResult: return [ calc, didAnything ]
			
		case CalcErr:
			def error = ((CalcErr)calc).error
			if ( error in Ref ) return harden( error )
			
			throw new RuntimeException(
				"A calculation resulted in this error: $error" )
			
		case CalcSoftAsk:
			def calc2 = (CalcSoftAsk)calc
			
			def sig = calc2.sig
			def neededSig = Refs.anyNeededSig( sig )
			if ( !null.is( neededSig ) )
				return harden( sig: neededSig )
			
			calc = new CalcCalc(
				calc: calcCall( calc2.next, [ getRef( sig ) ] ) )
			break
			
		case CalcHardAsk:
			def calc2 = (CalcHardAsk)calc
			
			def sig = calc2.sig
			def neededSig = Refs.anyNeededSig( sig )
			if ( !null.is( neededSig ) )
				return harden( sig: neededSig )
			
			if ( !refIsSet( sig ) )
				return [ calc, didAnything ]
			
			calc = new CalcCalc( calc: calcCall( calc2.next, [] ) )
			break
			
		case CalcCalc:
			def initialInnerCalc = ((CalcCalc)calc).calc
			switch ( initialInnerCalc )
			{
			case Ref: return harden( initialInnerCalc )
				
			case CalcResult:
				def value = ((CalcResult)initialInnerCalc).value
				if ( value in Ref ) return harden( value )
				
				// TODO: See if this would be better as a CalcErr
				// instead.
				if ( !(value in Calc) ) throw new RuntimeException(
					"A CalcCalc's inner result wasn't a Calc." )
				
				calc = value
				break
				
			default:
				// TODO: Stop using recursion here. It can overflow
				// the Java stack.
				// TODO: Figure out the best way to treat inner
				// errors with respect to their outer calculations.
				def ( finalInnerCalc, innerDid ) =
					advanceCalcRepeatedly(
						initialInnerCalc, calcCall, getRef )
				
				if ( !innerDid )
					return [ calc, didAnything ]
				
				calc = new CalcCalc( calc: finalInnerCalc )
				break
			}
			break
			
		default: throw new RuntimeException(
			"An unknown Calc type was encountered." )
		}
	}
}

// This returns a two-element list containing a Lead and a boolean
// indicating whether any advancement actually happened. The Lead will
// be either a LeadEnd, a LeadSplit, a LeadContrib, or a LeadCalc
// whose inner Calc is a valid result for
// { a, b, c -> advanceCalcRepeatedly( a, b, c )[ 0 ] }. However, it
// will only be a LeadContrib if none of the lead's promises reject
// the sig and at least one of them requires an unsatisfied hard ask.
//
// The addContrib parameter should be a function with side effects
// that takes a sig, a reducer, and a contributed value. It shouldn't
// test the contribution against the lead's promises; this takes care
// of that step already. The return value of addContrib should usually
// be null, but in case the contribution is obstructed by a hard ask
// when comparing reducers, it should return the sig which is asked
// for.
//
// The bladeTruthy parameter should be a closure that accepts a Blade
// value and returns either true, false, or a hard-asked-for sig.
//
def advanceLeadRepeatedly(
	Lead lead, Closure calcCall, Closure getRef, Closure addContrib,
	Closure addPromise, Closure getPromises, Closure bladeTruthy )
{
	def refIsSet = { Refs.isSetDirect getRef( it ) }
	
	def harden = { [
		new LeadCalc( calc: new CalcHardAsk(
			sig: it.sig, next: BuiltIn.of { calc } ) ),
		true
	] }
	
	for ( boolean didAnything = false; ; didAnything = true )
	{
		switch ( lead )
		{
		case LeadEnd:
		case LeadSplit:
			return [ lead, didAnything ]
			
		case LeadErr:
			def error = ((LeadErr)lead).error
			if ( error in Ref ) return harden( error )
			
			throw new RuntimeException(
				"A lead resulted in this error: $error" )
			
		case LeadContrib:
			def lead2 = (LeadContrib)lead
			
			def sig = lead2.sig
			
			boolean anyAsks = false
			for ( filter in getPromises() )
			{
				def ( Calc advanced, did ) = advanceCalcRepeatedly(
					calcCall( filter, [ sig ] ), calcCall, getRef )
					
				if ( advanced in CalcHardAsk )
					anyAsks = true
				else
				{
					def truth =
						bladeTruthy( ((CalcResult)advanced).value )
					
					// TODO: See if this would be better as a LeadErr
					// instead.
					if ( truth == false )
						throw new RuntimeException(
							   "A lead broke a promise not to"
							+ " contribute to this sig: $lead2.sig" )
					else if ( truth != true )
						anyAsks = true
				}
			}
			
			if ( anyAsks )
				return [ lead, didAnything ]
			
			
			def neededSig = Refs.anyNeededSig( sig )
			if ( !null.is( neededSig ) )
				return harden( sig: neededSig )
			
			def reducer = lead2.reducer
			neededSig = Refs.anyNeededSig( reducer )
			if ( !null.is( neededSig ) )
				return harden( sig: neededSig )
			
			neededSig = addContrib( sig, reducer, lead2.value )
			if ( !null.is( neededSig ) )
				return harden( sig: neededSig )
			
			lead = new LeadCalc( calc: calcCall( lead2.next, [] ) )
			break
			
		case LeadPromise:
			def lead2 = (LeadPromise)lead
			addPromise lead2.filter
			lead = calcCall( lead2.next, [] )
			break
			
		case LeadCalc:
			def initialInnerCalc = ((LeadCalc)lead).calc
			switch ( initialInnerCalc )
			{
				case Ref: return harden( initialInnerCalc )
				
				case CalcResult:
				def value = ((CalcResult)initialInnerCalc).value
				if ( value in Ref ) return harden( value )
				
				// TODO: See if this would be better as a LeadErr
				// instead.
				if ( !(value in Lead) ) throw new RuntimeException(
					"A LeadCalc's inner result wasn't a Lead." )
				
				lead = value
				break
				
			default:
				// TODO: Stop using recursion here. It can overflow
				// the Java stack.
				// TODO: Figure out the best way to treat inner
				// errors with respect to their outer calculations.
				def ( finalInnerCalc, innerDid ) =
					advanceCalcRepeatedly(
						initialInnerCalc, calcCall, getRef )
				
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


// Note that these sigs are assumed to be non-Refs that satisfy
// { null.is( Refs.anyNeededSigs( it ) ) }.
//
// Both this and sigIsoRep are used, even if they are a bit redundant.
//
boolean sigIso( Blade a, Blade b )
{
	while ( a in Sig && b in Sig )
	{
		def aSig = (Sig)a, bSig = (Sig)b
		
		if ( aSig.derivative != bSig.derivative )
			return false
		
		a = aSig.parent
		b = bSig.parent
	}
	
	return a == b
}

List< Blade > sigAncestors( Blade sig )
{
	List< Blade > revResult = [ sig ]
	
	while ( sig in Sig )
		revResult.add sig = ((Sig)sig).parent
	
	return revResult.reverse()
}

def sigIsParent( Blade sig, Blade child )
	{ sig in Sig && sigIso( ((Sig)sig).parent, child ) }

class BladeNamespace implements Blade { Map map }

// Note that the sig is assumed to be a non-Ref that satisfies
// { null.is( Refs.anyNeededSigs( it ) ) }.
List< Blade > sigIsoRep( Blade sig )
{
	def revResult = [];
	
	while ( sig in Sig )
	{
		def sigSig = (Sig)sig
		revResult.add( sigSig.derivative )
		sig = sigSig.parent
	}
	
	revResult.add sig
	
	return revResult.reverse()
}

Blade sigIsoRepToSig( List< Blade > isoRep )
	{ isoRep.tail().inject( isoRep.head() ) { parent, derivative -> (
		new Sig( parent: parent, derivative: derivative )
	) } }

class SigMap {
	private Map< List< Blade >, ? > entries = [:]
	
	def getAt( Blade key ) { entries[ sigIsoRep( key ) ] }
	
	int size() { entries.size() }
	
	Set< List< Blade > > keySet()
		{ entries.keySet().collect( sigIsoRepToSig ) }
	
	def setAt( Blade key, value )
		{ entries[ sigIsoRep( key ) ] = value }
	
	def push( Blade key, elem )
	{
		def keyRep = sigIsoRep( key )
		return entries[ keyRep ] =
			[ elem ] + (entries[ keyRep ] ?: [])
	}
	
	boolean containsKey( Sig key )
		{ entries.containsKey sigIsoRep( key ) }
	
	def remove( Blade key ) { entries.remove sigIsoRep( key ) }
}

class BladeSet implements Blade { Set< Blade > contents }

def let( Closure f ) { f() }

class LeadInfo { Blade lead; List< Blade > promises = [] }

// This takes a bunch of initial Leads, follows them, and returns the
// reduced value associated with the sigBase parameter. Even if the
// return value can be determined early, the leads will still be
// followed to their conclusions so that promise breaking can be
// detected, and as those breaches are being looked for, a dependency
// loop may be detected instead.
//
// The bladeReducerIsoMaker parameter should be a Groovy closure that
// takes a getRef closure and returns a Blade functon. The getRef
// parameter will be a function that translates sigs into the
// (possibly unfulfilled) Refs this top-level computation associates
// with them. The resulting Blade function will be called using
// calcCall, and it should take two reducers and return a Blade-style
// boolean value (translatable by bladeTruthyInteractive).
//
// The bladeTruthyInteractive parameter should be a closure that takes
// a Blade value and a getRef closure and returns either true, false,
// or a hard-asked-for sig. The getRef parameter will be a function
// that translates sigs into their (possibly unfulfilled) Refs.
//
// The calcCall parameter should be a closure that takes a Blade value
// and a Groovy List of Blade values and returns a Calc representing
// the result of a Blade function application. Any or all of the Blade
// values may be unresolved Refs; if their values are needed in the
// calculation, that's the point of CalcHardAsk.
//
// The namespaceReducer and sigBase parameters should be whatever
// Blade values are appropriate for representing the namespace reducer
// (a reducer value with special treatment so that its contents can be
// indexed by sigs and used before the whole namespace has been
// determined) and the sig base, which is the sig that stands for the
// ultimate result of this calculation. Neither of these values needs
// to have any functionality besides identity; they can each be given
// as "new Blade() {}" if there's no more appropriate alternative.
//
// TODO: Add support for one or more constant reducers. Maybe this can
// be built in implicitly, so that any reducer that doesn't hard-ask
// for its contribution parameter can be treated as a constant
// reducer.
//
Blade bladeTopLevel( Set< Lead > initialLeads,
	Closure bladeReducerIsoMaker, Closure bladeTruthyInteractive,
	Closure calcCall, Blade namespaceReducer, Blade sigBase )
{
	Set< LeadInfo > leadInfos =
		initialLeads.collect { new LeadInfo( lead: it ) }
	
	SigMap refs = new SigMap()
	SigMap reductions = new SigMap()
	SigMap reducers = new SigMap()
	SigMap contribs = new SigMap()
	
	def getRef = { Blade sig -> refs[ sig ] ?: let {
		
		for ( ancestor in sigAncestors( sig ).tail() )
			refs[ ancestor ] ?: (refs[ ancestor ] = Ref.to( sig ))
		
		refs[ sig ] = Ref.to( sig )
	} }
	
	def refIsSet = { Refs.isSetDirect getRef( it ) }
	
	def setRef = { sig, val -> Refs.set getRef( sig ), val }
	
	def bladeTruthy = { bladeTruthyInteractive it, getRef }
	
	Blade bladeReducerIso = bladeReducerIsoMaker( getRef )
	
	def reducerIso = { a, b ->
		
		def ( Calc isoCalc, did ) = advanceCalcRepeatedly(
			calcCall( bladeReducerIso, [ a, b ] ), calcCall, getRef )
		
		if ( isoCalc in CalcResult )
			return bladeTruthy( ((CalcResult)isoCalc).value )
		
		while ( isoCalc in CalcCalc )
			isoCalc = ((CalcCalc)isoCalc).calc
		
		return ((CalcHardAsk)isoCalc).sig
	}
	
	def isNamespaceReducer = { reducerIso it, namespaceReducer }
	
	def addContrib = { sig, reducer, value ->
		
		def directly = isNamespaceReducer( reducer )
		if ( directly == true )
			throw new RuntimeException(
				   "A contribution was made using the namespace"
				+ " reducer directly." )
		else if ( directly != false )
			return directly
		
		for ( ancestor in sigAncestors( sig ).tail() )
		{
			def existingReducer = reducers[ ancestor ]
			if ( null.is( existingReducer ) )
				reducers[ ancestor ] = namespaceReducer
			else
			{
				def compatible = isNamespaceReducer( existingReducer )
				if ( compatible == false )
					throw new RuntimeException(
						"A reducer conflict occurred." )
				else if ( compatible != true )
					return compatible
			}
		}
		
		def existingReducer = reducers[ sig ]
		if ( null.is( existingReducer ) )
			reducers[ sig ] = reducer
		else
		{
			def isoResult = reducerIso( reducer, existingReducer )
			if ( isoResult == false )
				throw new RuntimeException(
					"A reducer conflict occurred." )
			else if ( isoResult != true )
				return isoResult
		}
		
		contribs.push sig, value
		return null
	}
	
	def promiseRejects1 = { filter, sig ->
		
		def ( Calc result, did ) = advanceCalcRepeatedly(
			calcCall( filter, [ sig ] ), calcCall, getRef )
		
		return result in CalcResult &&
			(bladeTruthy( ((CalcResult)result).value ) == false)
	}
	
	def promiseRejects = { filter, sig -> (
		sigAncestors( sig ).any { promiseRejects1 filter, it }
	) }
	
	def advanceLead = { leadInfo ->
		
		def ( Lead newLead, boolean didAnything ) =
			advanceLeadRepeatedly(
				leadInfo.lead, calcCall, getRef, addContrib,
				{ leadInfo.promises = [ it ] + leadInfo.promises },
				{ -> leadInfo.promises },
				bladeTruthy
			)
		
		leadInfo.lead = newLead
		
		return didAnything
	}
	
	def advanceReduction = { sig ->
		
		def ( Calc result, boolean didAnything ) =
			advanceCalcRepeatedly(
				reductions[ sig ], calcCall, getRef )
		
		if ( !(result in CalcResult) )
			return didAnything
		
		reductions.remove sig
		setRef sig, result.value
		
		return true
	}
	
	getRef( sigBase )
	
	while ( true )
	{
		boolean didAnything = false
		
		for ( leadInfo in leadInfos )
		{
			def lead = leadInfo.lead
			if ( lead in Lead )
			{
				if ( advanceLead( leadInfo ) )
					didAnything = true
			}
			else if ( lead in Ref )
			{
				if ( Refs.isSetDirect( lead ) )
				{
					leadInfo.lead = Refs.derefSoft( lead )
					
					didAnything = true
				}
			}
			else throw new RuntimeException(
				"A LeadSplit split into at least one non-Lead." )
		}
		
		for ( sig in reductions.keySet() )
			if ( advanceReduction( sig ) )
				didAnything = true
		
		if ( leadInfos.removeAll { it.lead in LeadEnd } )
			didAnything = true
		
		for ( LeadInfo leadInfo in leadInfos.clone() )
		{
			def lead = leadInfo.lead
			
			if ( !(lead in LeadSplit) )
				continue
			
			def lead2 = (LeadSplit)lead
			def promises = leadInfo.promises
			
			leadInfos.remove leadInfo
			leadInfos.add new LeadInfo(
				lead: lead2.first, promises: promises )
			leadInfos.add new LeadInfo(
				lead: lead2.second, promises: promises )
			
			didAnything = true
		}
		
		int oldSize = refs.size()
		for ( sig in refs.keySet() )
		{
			if (
				!reducers.containsKey( sig )
				|| reductions.containsKey( sig )
				|| refIsSet( sig )
				|| !leadInfos.every { (
					it.promises.any { promiseRejects it, sig }
				) }
			)
				continue
			
			def reducer = reducers[ sig ]
			def namespacing = isNamespaceReducer( reducer )
			if ( namespacing == true )
			{
				def kids =
					refs.keySet().findAll { sigIsParent sig, it }
				
				if ( !kids.every( refIsSet ) )
					continue
				
				Map map = [:]
				for ( kid in kids )
					map[ kid.derivative ] =
						Refs.derefSoft( getRef( kid ) )
				
				setRef sig, new BladeNamespace( map: map )
				
				didAnything = true
			}
			else if ( namespacing == false )
			{
				reductions[ sig ] = calcCall( reducer,
					[ new BladeSet(
						contents: contribs[ sig ] as Set ) ]
				)
				
				didAnything = true
			}
		}
		
		didAnything = didAnything || refs.size() != oldSize
		
		if ( leadInfos.empty && refs.keySet().every( refIsSet ) )
			return Refs.derefSoft( getRef( sigBase ) )
		
		if ( !didAnything )
			throw new RuntimeException(
				"There was a dependency loop." )
	}
}


println "Hello, Blade!"
