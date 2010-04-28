// CurrentArc.groovy
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

// This file is an almost direct port of JVM-Blade's
// notes/arcblade.arc to Groovy. Some of the mechanisms change
// because, for instance, Groovy actually has classes. The rest of
// JVM-Blade takes off from here, but may incorporate improvements
// and extra features which haven't been ported back to Arc.


package com.rocketnia.blade.arcversion


interface Blade {}

// A sig is a list of values representing a path of namespaces.
// Contributing to a sig is the same as contributing a contribution
// object to the sig's parent, where the sig's parent uses a
// particular reducer which creates a namespace full of multivals out
// of a bunch of contribution objects.
class Sig implements Blade {
	Blade derivative, parent
	
	private List getIsoList() { [ Sig, derivative, parent ] }
	
	int hashCode() { isoList.hashCode() }
	
	boolean equals( Object other ) { !null.is( other ) && (
		(other.getClass() == this.getClass() &&
			isoList == ((Sig)other).getIsoList())
		|| (this.getClass().isAssignableFrom( other.getClass() )
			&& other.equals( this ))
	) }
}


interface Lead extends Blade {}

// A contribution of value to sig, expecting reducer to ultimately
// reduce the values. The next field is a continuation of the lead.
// Note that reducer and value don't need to be hard values; either of
// them can be a result or a structure containing a result of an
// answer to LeadSoftAsk or CalcSoftAsk.
class LeadContrib implements Lead { Blade sig, reducer, value, next }

// A promise not to contribute to any sig that doesn't satisfy the
// filter. The next field is a continuation of the lead.
class LeadPromise implements Lead { Blade filter, next }

// A request for a reference to the reduced value of sig. The value
// isn't needed yet, so it can be filled in later using mutation. The
// next field is a function that will take the answer and return a
// continuation of the lead.
class LeadSoftAsk implements Lead { Blade sig, next }

// A demand for the value of sig to be mutated into the existing soft
// reference to it. The next field is a continuation of the lead.
// Unlike LeadSoftAsk, next doesn't take a parameter.
class LeadHardAsk implements Lead { Blade sig, next }

// The result of a lead that has errored out. Note that this has no
// next continuation.
class LeadErr implements Lead { Blade error }

// The result of a lead that has run its course. Note that this has no
// next continuation.
class LeadEnd implements Lead {}


// To call a reducer or a filter may require reducing some other
// multivals first, so they have the same kinds of soft and hard
// asking capabilities as Leads do. However, they return results.
interface Calc extends Blade {}
class CalcSoftAsk implements Calc { Blade sig, next }
class CalcHardAsk implements Calc { Blade sig, next }
class CalcErr implements Calc { Blade error }
class CalcResult implements Calc { Blade value }

// This returns a two-element list containing a Calc and a boolean
// indicating whether any advancement actually happened. The Calc will
// be either a CalcResult or a CalcHardAsk. However, it will never be
// a CalcHardAsk for which getRef already returns a filled reference.
def CurrentArcalcRepeatedly( Calc calc, Closure getRef )
{
	def refIsSet = { bladeRefIsSetDirect getRef( it ) }
	
	for ( boolean didAnything = false; ; didAnything = true )
	{
		switch ( calc )
		{
		case CalcResult: return [ calc, didAnything ]
			
		case CalcErr: throw new RuntimeException(
			"A calculation resulted in this error: "
			+ ((CalcErr)calc).error )
			
		case CalcSoftAsk:
			def calc2 = (CalcSoftAsk)calc
			calc = bladeCall( calc2.next, getRef( calc2.sig ) )
			break
			
		case CalcHardAsk:
			def calc2 = (CalcHardAsk)calc
			if ( !refIsSet( calc2.sig ) )
				return [ calc, didAnything ]
			calc = bladeCall( calc2.next )
			break
			
		default: throw new RuntimeException(
			"An unknown Calc type was encountered." )
		}
	}
}

// This returns a two-element list containing a Lead and a boolean
// indicating whether any advancement actually happened. The Lead will
// be either a LeadEnd, an unsatisfied LeadHardAsk, or a LeadContrib.
// It will only be a LeadContrib if none of the lead's promises reject
// the sig and at least one of them requires an unsatisfied hard ask.
//
// The addContrib parameter should be a function with side effects
// that takes a sig, a reducer, and a contributed value. It shouldn't
// test the contribution against the lead's promises; this takes care
// of that step already.
//
def advanceLeadRepeatedly( Lead lead, Closure getRef,
	Closure addContrib, Closure addPromise, Closure getPromises )
{
	def refIsSet = { bladeRefIsSetDirect getRef( it ) }
	
	for ( boolean didAnything = false; ; didAnything = true )
	{
		switch ( lead )
		{
		case LeadEnd: return [ lead, didAnything ]
			
		case LeadErr: throw new RuntimeException(
			"A lead resulted in this error: "
			+ ((LeadErr)lead).error );
			
		case LeadSoftAsk:
			def lead2 = (LeadSoftAsk)lead
			lead = bladeCall( lead2.next, getRef( lead2.sig ) )
			break
			
		case LeadHardAsk:
			def lead2 = (LeadHardAsk)lead
			if ( !refIsSet( lead2.sig ) )
				return [ lead, didAnything ]
			lead = bladeCall( lead2.next )
			break
			
		case LeadContrib:
			def lead2 = (LeadContrib)lead
			boolean anyAsks = false
			for ( filter in getPromises() )
			{
				Calc advanced = advanceCalcRepeatedly(
					bladeCallTl( filter, lead2.sig ), getRef )
					
				if ( advanced in CalcHardAsk )
					anyAsks = true
				else if (
					!bladeTruthy( ((CalcResult)advanced).value ) )
					throw new RuntimeException(
						   "A lead broke a promise not to contribute"
						+ " to this sig: $lead2.sig" )
			}
			
			if ( anyAsks )
				return [ lead, didAnything ]
			
			addContrib lead2.sig, lead2.reducer, lead2.value
			lead = bladeCall( lead2.next )
			break
			
		case LeadPromise:
			def lead2 = (LeadPromise)lead
			addPromise lead2.filter
			lead = bladeCall( lead2.next )
			break
			
		default: throw new RuntimeException(
			"An unknown Lead type was encountered." )
		}
	}
}


// TODO: See if this is sufficient.
boolean bladeTruthy( Blade val ) { val }

// TODO: See if this is sufficient. It probably isn't, considering
// that one may be a ref pointing to the other or that they both may
// be refs pointing to the same thing.
boolean bladeReducersAreEquivalent( Blade a, Blade b ) { a == b }

// Call a Blade function which yields Calcs.
// TODO: Actually implement this, rather than just pretending to.
Calc bladeCallTl( Blade func, Blade... parms ) { func parms }

// TODO: Actually implement this, rather than just pretending to.
def bladeCall( Blade func, Blade... parms ) { func parms }

class Ref implements Blade {
	Blade sig, value
	
	static to( sig ) { new Ref( sig: sig ) }
	boolean isResolved() { null.is sig }
	synchronized void resolve( Blade value )
		{ sig = null; this.value = value }
}

void bladeSetRef( Ref ref, Blade val )
{
	if ( bladeRefIsSetDirect( ref ) )
		throw new RuntimeException(
			"A reference sent to bladeSetRef was already set." )
	
	while ( bladeRefIsSetDirect( val ) )
		val = ((Ref)val).value
	
	if ( ref.is( val ) )
		throw new RuntimeException(
			"A reference can't be set to itself." )
	
	ref.resolve val
}

Blade bladeDerefSoft( Blade ref )
{
	if ( !bladeRefIsSetDirect( ref ) )
		return ref
	
	Blade val = ((Ref)ref).value
	
	if ( !bladeRefIsSetDirect( val ) )
		return val
	
	List< Ref > thingsToSet = [ ref, val ]
	
	while ( bladeRefIsSetDirect( val = ((Ref)val).value ) )
		thingsToSet.add val
		
	for ( Ref thing in thingsToSet )
		thing.value = val
	
	return val
}

boolean bladeRefIsSetIndirect( Blade ref )
	{ bladeDerefSoft( ref ) in Ref }

boolean bladeRefIsSetDirect( Blade ref )
	{ ref in Ref && ((Ref)ref).isResolved() }

// This is the only reducer which isn't a function. It's a special
// case.
class NamespaceReducer implements Blade {
	
	private static NamespaceReducer instance;
	synchronized static NamespaceReducer getInstance()
	{
		if ( instance == null )
			instance = new NamespaceReducer()
		
		return instance
	}
}

//TODO: See if this is sufficient.
boolean sigIso( Blade a, Blade b ) { a == b }

List< Blade > sigAncestors( Blade sig )
{
	List< Blade > revResult = [ sig ]
	
	while ( sig in Sig )
		revResult.add sig = ((Sig)sig).parent
	
	return revResult.reverse()
}

def sigIsParent( Blade sig, Blade child )
	{ sig in Sig && sigIso( ((Sig)sig).parent, child ) }

// TODO: See if this is the only needed base.
class SigBase implements Blade {
	
	private static SigBase instance;
	synchronized static SigBase getInstance()
	{
		if ( instance == null )
			instance = new SigBase()
		
		return instance
	}
}

class BladeNamespace implements Blade { Map map }

class IsBox< T > {
	T value
	
	int hashCode() { value.hashCode() }
	
	boolean equals( Object other ) { !null.is( other ) && (
		(other.getClass() == this.getClass() &&
			((IsBox)other).value.is( value ))
		|| (this.getClass().isAssignableFrom( other.getClass() )
			&& other.equals( this ))
	) }
}

class SigMap {
	private Map< IsBox< Blade >, ? > entries = [:]
	
	def getAt( Blade key ) { entries[ new IsBox< Blade >( key ) ] }
	
	int size() { entries.size() }
	
	Set< IsBox< Blade > > keySet() { entries.keySet()*.value as Set }
	
	def setAt( Blade key, value )
		{ entries[ new IsBox< Blade >( key ) ] = value }
	
	List push( Blade key, elem )
	{
		def keyBox = new IsBox< Blade >( key );
		return entries[ keyBox ] =
			[ elem ] + (entries[ keyBox ] ?: [])
	}
	
	boolean containsKey( Blade key )
		{ entries.containsKey new IsBox< Blade >( key ) }
	
	boolean remove( Blade key )
		{ entries.remove new IsBox< Blade >( key ) }
}

class BladeSet implements Blade { Set< Blade > contents }

def let( Closure f ) { f() }

class LeadInfo { Lead lead; List< Blade > promises = [] }

// This takes a bunch of initial Leads, follows them, and returns the
// reduced value associated with SigBase.instance, which usually turns
// out to be a BladeNamespace. Even if the return value can be
// determined early, the leads will still be followed to their
// conclusions so that promise breaking can be detected, and as those
// are being looked for, a dependency loop may be detected instead.
Blade bladeTopLevel( Set< Lead > initialLeads )
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
	
	def refIsSet = { bladeRefIsSetDirect getRef( it ) }
	
	def setRef = { sig, val -> bladeSetRef getRef( sig ), val }
	
	def addContrib = { sig, reducer, value ->
		
		def namespaceReducer = NamespaceReducer.instance
		
		if ( bladeReducersAreEquivalent( reducer, namespaceReducer ) )
			throw new RuntimeException(
				   "A contribution was made using the namespace"
				+ " reducer directly." )
		
		for ( ancestor in sigAncestors( sig ).tail() )
		{
			def existingReducer = reducers[ ancestor ]
			if ( null.is( existingReducer ) )
				reducers[ ancestor ] = namespaceReducer
			else if ( !bladeReducersAreEquivalent(
				existingReducer, namespaceReducer ) )
				throw new RuntimeException(
					"A reducer conflict occurred." )
		}
		
		def existingReducer = reducers[ sig ]
		if ( null.is( existingReducer ) )
			reducers[ sig ] = reducer
		else if ( !bladeReducersAreEquivalent(
			existingReducer, reducer ) )
			throw new RuntimeException(
				"A reducer conflict occurred." )
		
		contribs.push sig, value
	}
	
	def promiseRejects1 = { filter, sig ->
		
		def ( Calc result, boolean didAnything ) =
			advanceCalcRepeatedly(
				bladeCallTl( filter, sig ), getRef )
		
		return result in CalcResult && !bladeTruthy( result.value )
	}
	
	def promiseRejects = { filter, sig -> (
		sigAncestors( sig ).any { promiseRejects1 filter, it }
	) }
	
	def advanceLead = { leadInfo ->
		
		def ( Lead newLead, boolean didAnything ) =
			advanceLeadRepeatedly(
				leadInfo.lead,
				getRef,
				addContrib,
				{ leadInfo.promises = [ it ] + leadInfo.promises },
				{ -> leadInfo.promises }
			)
		
		leadInfo.lead = newLead
		
		return didAnything
	}
	
	def advanceReduction = { sig ->
		
		def ( Calc result, boolean didAnything ) =
			advanceCalcRepeatedly( reductions[ sig ], getRef )
		
		if ( !(result in CalcResult) )
			return didAnything
		
		reductions.remove sig
		setRef sig, result.value
		
		return true
	}
	
	getRef( SigBase.instance )
	
	while ( true )
	{
		boolean didAnything = false
		
		for ( leadInfo in leadInfos )
			if ( advanceLead( leadInfo ) )
				didAnything = true
		
		for ( sig in reductions.keySet() )
			if ( advanceReduction( sig ) )
				didAnything = true
		
		if ( leadInfos.removeAll { it.lead in LeadEnd } )
			didAnything = true
		
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
			if ( bladeReducersAreEquivalent(
				reducer, namespaceReducer ) )
			{
				def kids =
					refs.keySet().findAll { sigIsParent sig, it }
				
				if ( !kids.every( refIsSet ) )
					continue
				
				Map map = [:]
				for ( kid in kids )
					map[ new IsBox< Blade >( kid.derivative ) ] =
						bladeDerefSoft( getRef( kid ) )
				
				setRef sig, new BladeNamespace( map: map )
			}
			else
			{
				reductions[ sig ] = bladeCallTl(
					reducer,
					new BladeSet( contents: contribs[ sig ] as Set )
				)
			}
			
			didAnything = true
		}
		
		didAnything = didAnything || refs.size() != oldSize
		
		if ( leadInfos.empty && refs.keySet().every( refIsSet ) )
			return bladeDerefSoft( getRef( SigBase.instance ) )
		
		if ( !didAnything )
			throw new RuntimeException(
				"There was a dependency loop." )
	}
}
