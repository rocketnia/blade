// TopLevel.groovy
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


class BladeNamespace implements Blade {
	Map map
	
	String toString() { "BladeNamespace$map" }
}

class BladeMultiset implements Blade {
	List< Blade > contents
	
	String toString() { "BladeMultiset$contents" }
}

class LeadInfo { Blade lead; List< Blade > promises = [] }

final class TopLevel
{
	private TopLevel() {}
	
	// This takes a bunch of initial Leads, follows them, and returns
	// the reduced value associated with the sigBase parameter. Even
	// if the return value can be determined early, the leads will
	// still be followed to their conclusions so that promise breaking
	// can be detected, and as those breaches are being looked for, a
	// dependency loop may be detected instead.
	//
	// One special property of this process is that if a value can be
	// reduced without hard-asking for its contribution set, it can be
	// used before all its contributions have been collected. This
	// means that a constant function can be used as the reducer for a
	// value that is completely defined by a single declaration. This
	// should be useful for fundamental Blade values which are meant
	// to be used very often during top-level calculation, since it
	// means they can be immune to hard-ask/promise deadlock (where
	// two or more declarations hard-ask for values which other
	// declarations in the set haven't promised not to contribute to)
	// rather than that deadlock showing up as a potential dependency
	// loop.
	//
	// The bladeDefinitionIsoMaker parameter should be a Groovy
	// closure that takes a getRef closure and returns a Blade
	// functon. The getRef parameter will be a function that
	// translates sigs into the (possibly unfulfilled) Refs this
	// top-level computation associates with them. The resulting Blade
	// function will be called using calcCall, and it should take two
	// definition calcs and return a Blade-style boolean value
	// (translatable by bladeTruthyInteractive).
	//
	// The bladeReducerIsoMaker parameter should work the same way as
	// the bladeDefinitionIsoMaker does, but the resulting Blade
	// function should instead take two reducers.
	//
	// The bladeTruthyInteractive parameter should be a closure that
	// takes a Blade value and a getRef closure and returns either
	// true, false, or a hard-asked-for ref. The getRef parameter will
	// be a function that translates sigs into their (possibly
	// unfulfilled) Refs.
	//
	// The calcCall parameter should be a closure that takes a Blade
	// value and a Groovy List of Blade values and returns a Calc
	// representing the result of a Blade function application. Any or
	// all of the Blade values may be unresolved Refs; if their values
	// are needed in the calculation, that's the point of CalcHardAsk.
	//
	// The namespaceReducer and sigBase parameters should be whatever
	// Blade values are appropriate for representing the namespace
	// reducer (a reducer value with special treatment so that its
	// contents can be indexed by sigs and used before the whole
	// namespace has been determined) and the sig base, which is the
	// sig that stands for the ultimate result of this calculation.
	// Neither of these values needs to have any functionality besides
	// identity; they can each be given as "new Blade() {}" if there's
	// no more appropriate alternative.
	//
	// TODO: See how useful it would be to have a generic multiset
	// reducer that has special abilities. For instance, a Calc could
	// ask "does any element of the multiset satisfy this property?"
	// and a Lead could split into one lead per element of the
	// multiset, such each of those Leads can spawn and begin
	// calculating as soon as its element is contributed. A
	// contribution set could be one of these behind the scenes.
	// However, if this isn't done carefully, it could get out of
	// hand; if a Calc asks "does any sub-multiset of the multiset
	// satisfy this property?" it'll make sense, but it'll be horribly
	// inefficient.
	//
	// TODO: The right way to report errors here is to blame them on
	// regions of the source documents, so that they can be quickly
	// found and corrected. For instance, every LeadInfo (if not every
	// Lead itself) should be associated with a source region to blame
	// if the Lead breaks a promise.
	//
	// TODO: Once errors are being collected like that rather than
	// stopping the whole top level calculation, it might be nice if
	// the calculation could be aborted somewhere after error have
	// been reported and before further intensive calculation takes
	// place. For instance, this might be done by having a special
	// LeadWait Lead to signal that an processing of that lead should
	// be put on hold until there are only LeadWaits and no errors
	// have been reported. An even cooler idea would be for the
	// developer to be able to see errors as they're found and to
	// abort manually.
	//
	static Blade bladeTopLevel( Set< Lead > initialLeads,
		Closure bladeDefinitionIsoMaker, Closure bladeReducerIsoMaker,
		Closure bladeTruthyInteractive, Closure calcCall,
		Blade namespaceReducer, Blade sigBase )
	{
		Set< LeadInfo > leadInfos =
			initialLeads.collect { new LeadInfo( lead: it ) }
		
		SigMap reductionRefs = new SigMap()
		SigMap definitions = new SigMap()
		SigMap originalDefinitions = new SigMap()
		SigMap reductions = new SigMap()
		SigMap reducers = new SigMap()
		SigMap contribSetRefs = new SigMap()
		SigMap contribs = new SigMap()
		
		def getRef = { Blade sig -> reductionRefs[ sig ] ?: Misc.let {
			
			for ( ancestor in Sigs.sigAncestors( sig ).tail() )
				reductionRefs[ ancestor ] ?:
					(reductionRefs[ ancestor ] = new Ref())
			
			return reductionRefs[ sig ] = new Ref()
		} }
		
		def refIsSet = { Refs.isSetDirect getRef( it ) }
		
		def setRef = { sig, val -> Refs.set getRef( sig ), val }
		
		def bladeTruthy = { bladeTruthyInteractive it, getRef }
		
		Blade bladeDefinitionIso = bladeDefinitionIsoMaker( getRef )
		Blade bladeReducerIso = bladeReducerIsoMaker( getRef )
		
		def reducerIso = { a, b ->
			
			def ( Calc isoCalc, did ) = Calcs.advanceCalcRepeatedly(
				calcCall( bladeReducerIso, [ a, b ] ),
				calcCall,
				getRef
			)
			
			if ( isoCalc in CalcResult )
				return bladeTruthy( ((CalcResult)isoCalc).value )
			
			while ( isoCalc in CalcCalc )
				isoCalc = ((CalcCalc)isoCalc).calc
			
			return ((CalcHardAsk)isoCalc).ref
		}
		
		def isNamespaceReducer = { reducerIso it, namespaceReducer }
		
		def makeAncestorsNamespaces = { sig ->
			
			for ( ancestor in Sigs.sigAncestors( sig ).tail() )
			{
				def existingReducer = reducers[ ancestor ]
				if ( null.is( existingReducer ) )
				{
					getRef ancestor
					reducers[ ancestor ] = namespaceReducer
				}
				else
				{
					def compatible =
						isNamespaceReducer( existingReducer )
					
					if ( compatible == false )
						throw new RuntimeException(
							"A reducer conflict occurred." )
					else if ( compatible != true )
						return compatible
				}
			}
			
			return null
		}
		
		def addDefinition = { sig, calc ->
			
			if ( reducers.containsKey( sig ) )
				throw new RuntimeException(
					"A definition/contrib conflict occurred." )
			
			def compatible = makeAncestorsNamespaces( sig )
			if ( compatible != null )
				return compatible
			
			def existingDefinition = originalDefinitions[ sig ]
			if ( null.is( existingDefinition ) )
			{
				getRef sig
				originalDefinitions[ sig ] = calc
				definitions[ sig ] = calc
			}
			else
			{
				def isoResult =
					definitionIso( calc, existingDefinition )
				if ( isoResult == false )
					throw new RuntimeException(
						"A reducer conflict occurred." )
				else if ( isoResult != true )
					return isoResult
			}
			
			return null
		}
		
		def addContrib = { sig, reducer, value ->
			
			def directly = isNamespaceReducer( reducer )
			if ( directly == true )
				throw new RuntimeException(
					   "A contribution was made using the namespace"
					+ " reducer directly." )
			else if ( directly != false )
				return directly
			
			if ( originalDefinitions.containsKey( sig ) )
				throw new RuntimeException(
					"A definition/contrib conflict occurred." )
			
			def compatible = makeAncestorsNamespaces( sig )
			if ( compatible != null )
				return compatible
			
			def existingReducer = reducers[ sig ]
			if ( null.is( existingReducer ) )
			{
				getRef sig
				reducers[ sig ] = reducer
				
				def contribSetRef = new Ref()
				contribSetRefs[ sig ] = contribSetRef
				reductions[ sig ] =
					calcCall( reducer, [ contribSetRef ] )
			}
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
			
			def ( Calc result, did ) = Calcs.advanceCalcRepeatedly(
				calcCall( filter, [ sig ] ), calcCall, getRef )
			
			return result in CalcResult &&
				(bladeTruthy( ((CalcResult)result).value ) == false)
		}
		
		def promiseRejects = { filter, sig -> Sigs.
			sigAncestors( sig ).any { promiseRejects1 filter, it } }
		
		def advanceLead = { leadInfo ->
			
			def ( Lead newLead, boolean didAnything ) =
				Leads.advanceLeadRepeatedly(
					leadInfo.lead,
					calcCall,
					getRef,
					addDefinition,
					addContrib,
					{ leadInfo.promises =
						[ it ] + leadInfo.promises },
					{ -> leadInfo.promises },
					bladeTruthy
				)
			
			leadInfo.lead = newLead
			
			return didAnything
		}
		
		def advanceDefinition = { sig ->
			
			def ( Calc result, boolean didAnything ) =
				Calcs.advanceCalcRepeatedly(
					definitions[ sig ], calcCall, getRef )
			
			if ( result in CalcResult )
			{
				definitions.remove sig
				setRef sig, result.value
				return true
			}
			else if ( didAnything )
			{
				definitions[ sig ] = result
				return true
			}
			
			return false
		}
		
		def advanceReduction = { sig ->
			
			def ( Calc result, boolean didAnything ) =
				Calcs.advanceCalcRepeatedly(
					reductions[ sig ], calcCall, getRef )
			
			if ( result in CalcResult )
			{
				reductions.remove sig
				setRef sig, result.value
				return true
			}
			else if ( didAnything )
			{
				reductions[ sig ] = result
				return true
			}
			
			return false
		}
		
		getRef sigBase
		
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
			
			for ( sig in definitions.keySet().clone() )
				if ( advanceDefinition( sig ) )
					didAnything = true
			
			for ( sig in reductions.keySet().clone() )
				if ( advanceReduction( sig ) )
					didAnything = true
			
			for ( leadInfo in leadInfos.clone() )
			{
				if ( leadInfo.lead in LeadEnd )
				{
					leadInfos.remove leadInfo
					didAnything = true
				}
			}
			
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
			
			int oldSize = reductionRefs.size()
			for ( sig in reductionRefs.keySet().clone() )
			{
				if (
					!reducers.containsKey( sig )
					|| (refIsSet( sig )
						&& Refs.isSetDirect( contribSetRefs[ sig ] ))
					|| !leadInfos.every { (
						it.promises.any { promiseRejects it, sig }
					) }
				)
					continue
				
				def reducer = reducers[ sig ]
				def namespacing = isNamespaceReducer( reducer )
				if ( namespacing == true )
				{
					if ( refIsSet( sig ) )
						continue
					
					def kids = reductionRefs.
						keySet().findAll { Sigs.sigIsParent sig, it }
					
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
					if ( Refs.isSetDirect( contribSetRefs[ sig ] ) )
						continue
					
					Refs.set contribSetRefs[ sig ],
						new BladeMultiset( contents: contribs[ sig ] )
					
					didAnything = true
				}
			}
			
			didAnything = didAnything ||
				reductionRefs.size() != oldSize
			
			if ( leadInfos.empty &&
				reductionRefs.values().every( Refs.&isSetDirect ) &&
				contribSetRefs.values().every( Refs.&isSetDirect ) )
				return Refs.derefSoft( getRef( sigBase ) )
			
			if ( !didAnything )
				throw new RuntimeException(
						"Either there was a dependency loop or"
					 + " something undefined was requested." )
		}
	}
}
