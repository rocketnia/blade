// Sigs.groovy
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
	
	String toString()
	{
		def isoRep = Sigs.sigIsoRep( this )
		return "Sig" +
			"( ${isoRep.head()} . ${isoRep.tail().join( ' ' )} )"
	}
}

class SigMap {
	private Map< List< Blade >, ? > entries = [:]
	
	def getAt( Blade key ) { entries[ Sigs.sigIsoRep( key ) ] }
	
	int size() { entries.size() }
	def values() { entries.values() }
	
	Set< List< Blade > > keySet()
		{ entries.keySet().collect( Sigs.&sigIsoRepToSig ) }
	
	def putAt( Blade key, value )
		{ entries[ Sigs.sigIsoRep( key ) ] = value }
	
	def push( Blade key, elem )
	{
		def keyRep = Sigs.sigIsoRep( key )
		return entries[ keyRep ] =
			[ elem ] + (entries[ keyRep ] ?: [])
	}
	
	boolean containsKey( Blade key )
		{ entries.containsKey Sigs.sigIsoRep( key ) }
	
	def remove( Blade key )
		{ entries.remove( Sigs.sigIsoRep( key ) ) }
	
	String toString() { "SigMap$entries" }
}


final class Sigs
{
	private Sigs() {}
	
	// Note that these sigs are assumed to be non-Refs that satisfy
	// { null.is( Refs.anyNeededRef( it ) ) }.
	//
	// Both this and sigIsoRep are used, even if they are a bit
	// redundant.
	//
	static boolean sigIso( Blade a, Blade b )
	{
		while ( a in Sig && b in Sig )
		{
			def aSig = (Sig)a, bSig = (Sig)b
			
			if ( aSig.getDerivative() != bSig.getDerivative() )
				return false
			
			a = aSig.getParent()
			b = bSig.getParent()
		}
		
		return a == b
	}
	
	static List< Blade > sigAncestors( Blade sig )
	{
		List< Blade > result = [ sig ]
		
		while ( sig in Sig )
			result.add sig = ((Sig)sig).getParent()
		
		return result
	}
	
	static boolean sigIsParent( Blade parent, Blade child )
		{ child in Sig && sigIso( parent, ((Sig)child).getParent() ) }
	
	// Note that the sig is assumed to be a non-Ref that satisfies
	// { null.is( Refs.anyNeededRef( it ) ) }.
	static List< Blade > sigIsoRep( Blade sig )
	{
		def revResult = [];
		
		while ( sig in Sig )
		{
			def sigSig = (Sig)sig
			revResult.add( sigSig.getDerivative() )
			sig = sigSig.getParent()
		}
		
		revResult.add sig
		
		return revResult.reverse()
	}
	
	static Blade sigIsoRepToSig( List< Blade > isoRep )
		{ ( isoRep.tail().inject( isoRep.head() )
			{ parent, derivative -> new Sig(
				parent: parent, derivative: derivative ) } ) }
}
