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


class LeadInfo { Blade lead; List< Blade > promises = [] }

final class TopLevel
{
	private TopLevel() {}
	
	// This follows Leads until they end, then returns the resolved
	// value associated with the sigBase parameter. Even if the return
	// value can be determined early, the leads will still be followed
	// to their conclusions so that promise breaking can be detected,
	// and as those breaches are being looked for, a dependency loop
	// or undefined value may be detected instead.
	//
	// The sigBase parameter should be whatever Blade value is
	// appropriate for representing the sig that stands for the base
	// of the namespace tree and the ultimate result of this
	// calculation. This value doesn't need to have any functionality
	// besides identity; it can be given as "new Blade() {}" if
	// there's no more appropriate alternative.
	//
	// The calcCall parameter should be a closure that takes a Blade
	// value and a Groovy List of Blade values and returns a Calc
	// representing the result of a Blade function application. Any or
	// all of the Blade values may be unresolved Refs; if their values
	// are needed in the calculation, that's the point of CalcHardAsk.
	//
	// The refBaseToInitial parameter should be a closure that takes a
	// Ref corresponding to sigBase, records it somewhere where
	// calcCall can find it (if it needs to), and returns a set of
	// initial Leads. It can also be used in order to set up further
	// Refs for calcCall, the initial Leads, and other code in the
	// caller to rely on.
	//
	// TODO: See what special abilities a bag Ref can have. For
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
	static Blade bladeTopLevel(
		Blade sigBase, Closure calcCall, Closure refBaseToInitial )
	{
		Set< Ref > managedRefs = [] as Set
		
		def refRegistrar = { managedRefs.add it }
		
		def refBase = new Ref( sigBase, refRegistrar )
		
		Set< LeadInfo > leadInfos = refBaseToInitial( refBase ).
			collect { new LeadInfo( lead: it ) }
		
		def promiseRejects = { filter, sig ->
			
			def ( Calc advanced, did ) = Calcs.advanceCalcRepeatedly(
				calcCall( filter, [ sig ] ), calcCall )
			
			if ( !(advanced in CalcResult) )
				return false
			
			def truth = ((CalcResult)advanced).getValue()
			
			if ( truth in Ref )
				return false
			
			if ( !(truth in BladeBoolean) )
				throw new RuntimeException(
					"A promise filter returned a non-BladeBoolean." )
			
			return !((BladeBoolean)truth).value
		}
		
		def advanceLead = { leadInfo ->
			
			def ( Lead newLead, boolean didAnything ) =
				Leads.advanceLeadRepeatedly(
					leadInfo.lead,
					calcCall,
					{ leadInfo.promises =
						[ it ] + leadInfo.promises },
					{ -> leadInfo.promises }
				)
			
			leadInfo.lead = newLead
			
			return didAnything
		}
		
		while ( true )
		{
			boolean didAnything = false
			
			for ( leadInfo in leadInfos.clone() )
			{
				def lead = leadInfo.lead
				switch ( lead )
				{
				case LeadEnd:
					leadInfos.remove leadInfo
					break
					
				case LeadSplit:
					def lead2 = (LeadSplit)lead
					def promises = leadInfo.promises
					
					leadInfos.remove leadInfo
					leadInfos.add new LeadInfo(
						lead: lead2.first, promises: promises )
					leadInfos.add new LeadInfo(
						lead: lead2.second, promises: promises )
					break
					
				case Ref:
					def lead2 = (Ref)lead
					if ( !lead2.isResolved() )
						continue
					
					leadInfo.lead = lead2.derefSoft()
					break
					
				case Lead:
					if ( !advanceLead( leadInfo ) )
						continue
					break
					
				default: throw new RuntimeException(
					"A LeadSplit split into at least one non-Lead." )
				}
				
				didAnything = true
			}
			
			int oldSize = managedRefs.size()
			for ( ref in managedRefs.clone() )
			{
				if ( Refs.isSetIndirect( ref ) )
				{
					managedRefs.remove ref
					didAnything = true
					continue
				}
				
				if ( !ref.isFinishable() )
					continue
				
				def sig = ref.sig
				if ( !leadInfos.
					every { it.promises.
						any { promiseRejects it, sig } } )
					continue
				
				ref.finish()
				ref.becomeReadyToCollapse()
				managedRefs.remove ref
				didAnything = true
			}
			
			didAnything = didAnything || managedRefs.size() != oldSize
			
			if ( leadInfos.isEmpty() && managedRefs.isEmpty() )
				return Refs.derefSoft( refBase )
			
			if ( !didAnything )
			{
				if ( leadInfos.isEmpty() )
					throw new RuntimeException(
						"Something requested was never defined." )
				else
					throw new RuntimeException(
							"Either there was a dependency loop or"
						 + " something requested was never defined." )
			}
		}
	}
}
