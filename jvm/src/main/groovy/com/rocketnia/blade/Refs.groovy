// Refs.groovy
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


package com.rocketnia.blade


class Ref implements Blade {
	Blade value
	
	boolean isResolved() { !null.is( value ) }
	synchronized void resolve( Blade value ) { this.value = value }
}

final class Refs
{
	private Refs() {}
	
	static void set( Ref ref, Blade val )
	{
		if ( isSetDirect( ref ) )
			throw new RuntimeException(
				"A reference sent to Refs.set was already set." )
		
		while ( isSetDirect( val ) )
			val = ((Ref)val).value
		
		if ( ref.is( val ) )
			throw new RuntimeException(
				"A reference can't be set to itself." )
		
		ref.resolve val
	}
	
	static Blade derefSoft( Blade ref )
	{
		if ( !isSetDirect( ref ) )
			return ref
		
		Blade val = ((Ref)ref).value
		
		if ( !isSetDirect( val ) )
			return val
		
		List< Ref > thingsToSet = [ ref, val ]
		
		while ( isSetDirect( val = ((Ref)val).value ) )
			thingsToSet.add val
		
		for ( Ref thing in thingsToSet )
			thing.value = val
		
		return val
	}
	
	static boolean isSetIndirect( Blade ref )
		{ derefSoft( ref ) in Ref }
	
	static boolean isSetDirect( Blade ref )
		{ ref in Ref && ((Ref)ref).isResolved() }
	
	static Ref anyNeededRef( Blade rootNode )
	{
		Set allNodes = [ rootNode ]
		List nodesToGo = [ rootNode ]
		
		while ( !nodesToGo.isEmpty() )
		{
			def node = derefSoft( nodesToGo.pop() )
			
			if ( node in Ref )
				return node
			
			if ( node in RefMap )
			{
				for ( newNode in ((RefMap)node).refVals() - allNodes )
				{
					allNodes.add newNode
					nodesToGo.add newNode
				}
			}
		}
		
		return null
	}
}

class RefMap implements Blade
{
	private Map< String, Blade > refs = [:]
	
	Blade get( String name )
	{
		Blade ref = refs[ name ]
		
		if ( !Refs.isSetDirect( ref ) )
			return ref
		
		return refs[ name ] = Refs.derefSoft( ref )
	}
	
	Blade set( String name, Blade value ) { refs[ name ] = value }
	
	Set< String > refKeys() { new HashSet< String >( refs.keySet() ) }
	Set< Blade > refVals() { refKeys().collect get }
}
