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
	// the resolved value associated with the sigBase parameter. Even
	// if the return value can be determined early, the leads will
	// still be followed to their conclusions so that promise breaking
	// can be detected, and as those breaches are being looked for, a
	// dependency loop may be detected instead.
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
	// The sigBase parameter should be whatever Blade values is
	// appropriate for representing the sig that stands for the base
	// of the namespace tree and the ultimate result of this
	// calculation. This value doesn't need to have any functionality
	// besides identity; it can be given as "new Blade() {}" if
	// there's no more appropriate alternative.
	//
	// TODO: See what special abilities a typeBag can have. For
	// instance, a Calc could ask "does any element of the multiset
	// satisfy this property?" and a Lead could split into one lead
	// per element of the multiset, such each of those Leads can spawn
	// and begin calculating as soon as its element is contributed.
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
		Closure bladeDefinitionIsoMaker,
		Closure bladeTruthyInteractive, Closure calcCall,
		Blade sigBase )
	{
		Set< LeadInfo > leadInfos =
			initialLeads.collect { new LeadInfo( lead: it ) }
		
		SigMap managedRefs = new SigMap()
		SigMap types = new SigMap()
		SigMap definitions = new SigMap()
		SigMap originalDefinitions = new SigMap()
		SigMap contribs = new SigMap()
		
		def typeConstant = new Object()
		def typeBag = new Object()
		def typeNamespace = new Object()
		
		def getRef = { Blade sig -> managedRefs[ sig ] ?: Misc.let {
			
			for ( ancestor in Sigs.sigAncestors( sig ).tail() )
				managedRefs[ ancestor ] ?:
					(managedRefs[ ancestor ] = new Ref())
			
			return managedRefs[ sig ] = new Ref()
		} }
		
		def refIsSet = { Refs.isSetDirect getRef( it ) }
		
		def setRef = { sig, val -> Refs.set getRef( sig ), val }
		
		def bladeTruthy = { bladeTruthyInteractive it, getRef }
		
		Blade bladeDefinitionIso = bladeDefinitionIsoMaker( getRef )
		
		def makeAncestorsNamespaces = { sig ->
			
			for ( ancestor in Sigs.sigAncestors( sig ).tail() )
			{
				def existingType = types[ ancestor ]
				if ( existingType == typeNamespace )
					continue
				
				if ( existingType != null )
					throw new RuntimeException(
						"A reduction type conflict occurred." )
				
				types[ ancestor ] = typeNamespace
				getRef ancestor
			}
			
			return null
		}
		
		def addDefinition = { sig, calc ->
			
			def existingType = types[ sig ]
			if ( existingType == typeConstant )
				return
			
			if ( existingType != null )
				throw new RuntimeException(
					"A reduction type conflict occurred." )
			
			types[ sig ] = typeConstant
			getRef sig
			
			def compatible = makeAncestorsNamespaces( sig )
			if ( compatible != null )
				return compatible
			
			def existingDefinition = originalDefinitions[ sig ]
			if ( existingDefinition == null )
			{
				originalDefinitions[ sig ] = calc
				definitions[ sig ] = calc
			}
			else
			{
				def isoResult =
					definitionIso( calc, existingDefinition )
				if ( isoResult == false )
					throw new RuntimeException(
						"A definition conflict occurred." )
				else if ( isoResult != true )
					return isoResult
			}
			
			return null
		}
		
		def addBagContrib = { sig, value ->
			
			def existingType = types[ sig ]
			if ( existingType == typeBag )
				return
			
			if ( existingType != null )
				throw new RuntimeException(
					"A reduction type conflict occurred." )
			
			types[ sig ] = typeBag
			getRef sig
			
			def compatible = makeAncestorsNamespaces( sig )
			if ( compatible != null )
				return compatible
			
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
					addBagContrib,
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
			
			int oldSize = managedRefs.size()
			for ( sig in managedRefs.keySet().clone() )
			{
				def type = types[ sig ]
				if (
					!(type in [ typeBag, typeNamespace ])
					|| refIsSet( sig )
					|| !leadInfos.every { (
						it.promises.any { promiseRejects it, sig }
					) }
				)
					continue
				
				if ( type == typeNamespace )
				{
					def kids = managedRefs.
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
				else
				{
					setRef sig,
						new BladeMultiset( contents: contribs[ sig ] )
					
					didAnything = true
				}
			}
			
			didAnything = didAnything || managedRefs.size() != oldSize
			
			if ( leadInfos.empty &&
				managedRefs.values().every( Refs.&isSetDirect ) )
				return Refs.derefSoft( getRef( sigBase ) )
			
			if ( !didAnything )
				throw new RuntimeException(
						"Either there was a dependency loop or"
					 + " something undefined was requested." )
		}
	}
}
