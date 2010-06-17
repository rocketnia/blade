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


package com.rocketnia.blade.declare

import com.rocketnia.blade.*


enum ReductionType { Constant, Bag, Map }

class Ref implements Blade {
	private ReductionType type
	private List< Blade > partialBag
	private Map< BladeKey, Ref > map
	private Blade value
	final Blade sig
	private final Closure registrar
	private boolean readyToCollapse = false
	
	public Ref( Blade sig, Closure registrar )
	{
		this.sig = sig
		this.registrar = registrar
		registrar( this )
	}
	
	boolean isResolved() { !null.is( value ) }
	boolean isReadyToCollapse() { readyToCollapse }
	
	void becomeReadyToCollapse()
	{
		readyToCollapse = true
		derefSoft()
	}
	
	synchronized boolean isntIt( ReductionType type )
	{
		if ( type == null )
			throw new NullPointerException()
		
		return type != (this.type = this.type ?: type)
	}
	
	boolean isntItConstant() { isntIt ReductionType.Constant }
	boolean isntItBag() { isntIt ReductionType.Bag }
	boolean isntItMap() { isntIt ReductionType.Map }
	
	boolean couldBe( ReductionType type )
		{ this.type == null || this.type == type }
	
	boolean couldBeConstant() { couldBe ReductionType.Constant }
	boolean couldBeBag() { couldBe ReductionType.Bag }
	boolean couldBeMap() { couldBe ReductionType.Map }
	
	String toString()
		{ isResolved() ? "Ref>$value" : "Ref(unresolved)" }
	
	synchronized Blade derefSoft()
	{
		if ( null.is( value ) )
			return this
		
		def result = Refs.derefSoft( value )
		
		if ( readyToCollapse )
		{
			value = result
			map = null
		}
		
		return result
	}
	
	void resolveTo( Blade value )
	{
		if ( is( Refs.derefSoft( value ) ) )
			throw new IllegalArgumentException(
				"A reference can't be set to itself." )
		
		synchronized ( this )
		{
			if ( !null.is( this.value ) )
				throw new IllegalStateException(
						"The resolveTo method was called on an"
					 + " already resolved reference." )
			
			if ( type != ReductionType.Constant )
				throw new IllegalStateException(
						"The resolveTo method was called on a"
					 + " non-constant reference." )
			
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
		
		if ( type != ReductionType.Bag )
			throw new IllegalStateException(
					"The addToBag method was called on a non-bag"
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
	
	synchronized Ref getFromMapHard( BladeKey key )
	{
		if ( type != ReductionType.Map )
			throw new IllegalStateException(
					"The getFromMapHard method was called on a"
				 + " non-map reference." )
		
		if ( !null.is( value ) )
		{
			def result = map[ key ]
			
			if ( null.is( result ) )
				throw new IllegalStateException(
						"The getFromMap method was called with a new"
					 + " key on an already resolved reference." )
			
			return result
		}
		
		if ( null.is( map ) )
			map = [:]
		
		return map[ key ] ?: (map[ key ] = new Ref(
			new Sig( parent: sig, derivative: key ),
			registrar
		))
	}
	
	Ref getFromMapSoft( BladeKey key ) { map?.get key }
	
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
			value = new BladeNamespace( map )
			derefSoft()
		}
		else
			throw new IllegalStateException(
					"The finish method was called on an a"
				 + " non-finishable reference." )
	}
}

// Although a Ref is an instance of the Blade interface, it isn't
// first-class. It's a Blade so it can be stored in places a Blade is
// stored, but it isn't a value in the language. On the contrary, a
// ReflectedRef is a first-class Blade values with the explicit intent
// to be used for manipulating Refs. In particular, it should be used
// when constructing Leads and Calcs from within Blade.
class ReflectedRef implements Blade { Ref ref }

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
		Blade result = refs[ name ]
		
		if ( result in Ref )
		{
			def ref = (Ref)result
			if ( ref.isReadyToCollapse() )
				result = refs[ name ] = ref.derefSoft()
		}
		
		return result
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
	private final Map< BladeKey, Blade > map
	
	BladeNamespace( Map< ? extends BladeKey, ? extends Blade > map )
		{ this.map = map + [:] }
	
	String toString()
		{ map.each { k, v -> getAt k }; return "BladeNamespace$map" }
	
	public Blade getAt( BladeKey key )
	{
		def result = map[ key ]
		
		if ( result in Ref )
		{
			def ref = (Ref)result
			if ( ref.isReadyToCollapse() )
				result = map[ key ] = ref.derefSoft()
		}
		
		return result
	}
}
