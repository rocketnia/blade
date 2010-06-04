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

import com.rocketnia.blade.declare.*


class Ref implements Blade {
	private List< Blade > partialBag
	private Map< Blade, Ref > map
	private Blade value
	final Blade sig
	private final Closure registrar
	
	public Ref( Blade sig, Closure registrar )
	{
		this.sig = sig
		this.registrar = registrar
		registrar( this )
	}
	
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
			if ( !null.is( this.value ) )
				throw new IllegalStateException(
						"The resolveTo method was called on an"
					 + " already resolved reference." )
			
			if ( isFinishable() )
				throw new IllegalStateException(
						"The resolveTo method was called on a"
					 + " finishable reference." )
			
			this.value = value
		}
	}
	
	boolean isPartialBag() { !null.is( partialBag ) }
	boolean couldBePartialBag()
		{ null.is( value ) && null.is( map ) }
	
	synchronized void addToBag( Blade element )
	{
		if ( !null.is( value ) )
			throw new IllegalStateException(
					"The addToBag method was called on an already"
				 + " resolved reference." )
		
		if ( !null.is( map ) )
			throw new IllegalStateException(
					"The addToBag method was called on a map"
				 + " reference." )
		
		if ( null.is( partialBag ) )
			partialBag = [ element ]
		else
			partialBag.add element
	}
	
	boolean isPartialMap() { null.is( value ) && !null.is( map ) }
	boolean couldBePartialMap()
		{ null.is( value ) && null.is( partialBag ) }
	boolean canGetFromMap() { null.is( partialBag ) }
	
	Ref getFromMap( Blade key )
	{
		key = Refs.derefSoft( key )
		
		if ( key in Ref )
			throw new IllegalArgumentException(
				"The key must be a non-Ref Blade value." )
		
		synchronized ( this )
		{
			if ( !null.is( value ) )
			{
				if ( null.is( map ) )
					throw new IllegalStateException(
							"The getFromMap method was called on a"
						 + " resolved, non-map reference." )
				
				def result = map[ key ]
				
				if ( null.is( result ) )
					throw new IllegalStateException(
							"The getFromMap method was called with a"
						 + " new key on an already resolved"
						 + " reference." )
				
				return result
			}
			
			if ( !null.is( partialBag ) )
				throw new IllegalStateException(
						"The getFromMap method was called on a"
					 + " partial bag reference." )
			
			if ( null.is( map ) )
				map = [:]
			
			return map[ key ] ?: (map[ key ] = new Ref(
				new Sig( parent: sig, derivative: key ),
				registrar
			))
		}
	}
	
	boolean isFinishable() { isPartialBag() || isPartialMap() }
	
	synchronized void finish()
	{
		if ( isPartialBag() )
		{
			value = new BladeMultiset( contents: partialBag )
			partialBag = null
		}
		else if ( isPartialMap() )
		{
			value = new BladeNamespace( map: map )
		}
		else
			throw new IllegalStateException(
					"The finish method was called on an a"
				 + " non-finishable reference." )
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
	
	Blade getRaw( String name ) { refs[ name ] }
	Blade set( String name, Blade value ) { refs[ name ] = value }
	
	Set< String > refKeys() { new HashSet< String >( refs.keySet() ) }
	Set< Blade > refVals() { refKeys().collect this.&get }
}

class BladeMultiset implements Blade {
	List< Blade > contents
	
	String toString() { "BladeMultiset$contents" }
}

class BladeNamespace implements Blade {
	Map< Blade, Blade > map
	
	String toString() { "BladeNamespace$map" }
	
	public Blade getAt( Blade key )
	{
		if ( key in Ref )
			throw new IllegalArgumentException(
				"The key must be a non-Ref Blade value." )
		
		def ref = map[ key ]
		
		if ( !(ref in Ref) )
			return ref
		
		return map[ key ] = ((Ref)ref).derefSoft()
	}
}
