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
	private Blade value
	
	boolean isResolved() { !null.is( value ) }
	
	String toString()
		{ isResolved() ? "Ref>$value" : "Ref(unresolved)" }
	
	synchronized Blade derefSoft()
	{
		if ( null.is( value ) )
			return this
		
		if ( !(value in Ref) )
			return value
		
		return value = ((Ref)value).derefSoft()
	}
	
	void resolveTo( Blade value )
	{
		value = Refs.derefSoft( value )
		
		if ( is( value ) )
			throw new IllegalArgumentException(
				"A reference can't be set to itself." )
		
		synchronized ( this )
		{
			if ( null.is( this.value ) )
				this.value = value
			else
				throw new IllegalStateException(
						"The resolveTo method was called on an"
					 + " already resolved reference." )
		}
	}
}

final class Refs
{
	private Refs() {}
	
	static Blade derefSoft( Blade ref )
		{ ref in Ref ? ((Ref)ref).derefSoft() : ref }
	
	static boolean isSetIndirect( Blade ref )
		{ !(derefSoft( ref ) in Ref) }
	
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
		
		if ( !(ref in Ref) )
			return ref
		
		return refs[ name ] = ((Ref)ref).derefSoft()
	}
	
	Blade set( String name, Blade value ) { refs[ name ] = value }
	
	Set< String > refKeys() { new HashSet< String >( refs.keySet() ) }
	Set< Blade > refVals() { refKeys().collect this.&get }
}
