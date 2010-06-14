// Misc.groovy
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
import com.rocketnia.blade.weak.*


interface Blade {}

// TODO: Use this class less often. It's okay for experimentation, but
// uses of it in this project (JVM-Blade) should eventually be changed
// wherever possible so that other, more tailor-made types are used
// instead.
class BuiltIn implements Blade {
	def value
	
	static BuiltIn of( value ) { new BuiltIn( value: value ) }
	
	String toString() { "BuiltIn(${value.inspect()})" }
	
	private static Lead softAsk1(
		Ref source, Blade key, Closure body )
	{
		def firstTry = source.getFromMapSoft( key )
		
		if ( !null.is( firstTry ) )
			return body( firstTry )
		
		return new LeadSoftAsk(
			source: new ReflectedRef( ref: source ),
			key: key,
			next: of { List< Blade > args ->
				
				if ( args.size() != 1 )
					return new CalcErr( error: BladeString.of(
							"Expected 1 argument to a CalcSoftAsk"
						 + " continuation and got ${args.size()}." ) )
				
				def arg = args[ 0 ]
				
				if ( !(arg in ReflectedRef) )
					return new CalcErr( error: BladeString.of(
							"A non-ReflectedRef was passed to a"
						 + " CalcSoftAsk continuation." ) )
				
				if ( null.is( source.getFromMapSoft( key ) ) )
					return new CalcErr( error: BladeString.of(
							"A soft ask was continued before it was"
						 + " fulfilled." ) )
				
				return new CalcResult(
					value: body( ((ReflectedRef)arg).ref ) )
			}
		)
	}
	
	static Lead softAsk(
		Ref refBase, List< Blade > derivs, Closure body )
	{
		if ( derivs.isEmpty() )
			return body( refBase )
		
		def derivsTail = derivs.tail()
		
		return softAsk1( refBase, derivs.head() ) {
			
			return softAsk( it, derivsTail, body )
		}
	}
	
	static Calc hardAsk( Blade ref, Closure body )
	{
		def derefed = Refs.derefSoft( ref )
		
		if ( !(derefed in Ref) )
			return body( derefed )
		
		def derefedRef = (Ref)derefed
		
		return new CalcHardAsk(
			ref: new ReflectedRef( ref: derefed ),
			next: of { List< Blade > args ->
				
				if ( args.size() != 1 )
					return new CalcErr( error: BladeString.of(
							"Expected 1 argument to a CalcHardAsk"
						 + " continuation and got ${args.size()}." ) )
				
				def arg = Refs.derefSoft( args[ 0 ] )
				if ( arg in Ref )
					return new CalcErr( error: BladeString.of(
							"A hard ask was continued with an"
						 + " unresolved Ref." ) )
				
				return new CalcResult( value: body( arg ) )
			}
		)
	}
	
	static Lead leadHardAsk( Blade ref, Closure body )
	{
		def derefed = Refs.derefSoft( ref )
		
		if ( !(derefed in Ref) )
			return body( derefed )
		
		def derefedRef = (Ref)derefed
		
		return new LeadCalc( calc: new CalcHardAsk(
			ref: new ReflectedRef( ref: derefed ),
			next: of { List< Blade > args ->
				
				if ( args.size() != 1 )
					return new CalcErr( error: BladeString.of(
							"Expected 1 argument to a CalcHardAsk"
						 + " continuation and got ${args.size()}." ) )
				
				def arg = Refs.derefSoft( args[ 0 ] )
				if ( arg in Ref )
					return new CalcErr( error: BladeString.of(
							"A hard ask was continued with an"
						 + " unresolved Ref." ) )
				
				return new CalcResult( value: body( arg ) )
			}
		) )
	}
}

class BladeString implements Blade, Internable {
	protected String value
	
	protected static final Interner< BladeString > interner =
		new Interner< BladeString >()
	
	protected BladeString( String value ) { this.value = value }
	
	static BladeString of( String value )
		{ interner[ new BladeString( new String( value ) ) ] }
	
	String getInternKey() { value }
	
	String toString() { "bs" + value.inspect() }
	
	String toJava() { value }
}

final class BladeBoolean implements Blade {
	final boolean value
	
	private BladeBoolean( boolean value ) { this.value = value }
	
	static final TRUE = new BladeBoolean( true )
	static final FALSE = new BladeBoolean( false )
}

final class Misc
{
	private Misc() {}
	
	static let( Closure f ) { f() }
	
	static boolean anyNonDir(
		Map options = [:], File file, Closure body )
	{
		// We don't really care about the traversal order, but we're
		// making a point to avoid JVM recursion so that we don't get
		// a StackOverflowError.
		
		def toGo = [ file ]
		
		def dirNameFilter = options?.get( "dirNameFilter" )
		if ( null.is( dirNameFilter ) )
			dirNameFilter = { true }
		
		def nonDirNameFilter = options?.get( "nonDirNameFilter" )
		if ( null.is( nonDirNameFilter ) )
			nonDirNameFilter = { true }
		
		while ( !toGo.isEmpty() )
		{
			def thisFile = (File)toGo.pop()
			
			if ( thisFile.isFile() )
			{
				if ( thisFile.getName() in nonDirNameFilter
					&& body( thisFile ) )
					return true
			}
			else if ( thisFile.getName() in dirNameFilter )
				for ( child in thisFile.listFiles() )
					toGo.add child
		}
		
		return false
	}
	
	static void eachNonDir(
		Map options = [:], File file, Closure body )
	{
		anyNonDir options, file, {
			
			body it
			return false
		}
	}
	
	static Set< File > getNonDirs( Map options = [:], File directory )
	{
		if ( null.is( directory ) || !directory.exists() )
			return null
		
		def result = []
		eachNonDir(
			options, new File( directory.toURI() ), result.&add )
		return result as Set
	}
}
