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
	
	private static LeadSoftAsk softAsk1(
		Ref source, Blade key, Closure body )
		{ new LeadSoftAsk( source: source, key: key, next:
			of { List< Blade > args ->
				
				if ( args.size() != 1 )
					return new CalcErr( error: BladeString.of(
							"Expected 1 argument to a CalcSoftAsk"
						 + " continuation and got ${args.size()}." ) )
				
				return new CalcResult( value: body( args[ 0 ] ) )
			}
		) }
	
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
		
		return new CalcHardAsk( ref: derefed, next:
			of { List< Blade > args ->
				
				if ( args.size() != 0 )
					return new CalcErr( error: BladeString.of(
							"Expected 0 arguments to a CalcHardAsk"
						 + " continuation and got ${args.size()}." ) )
				
				def derefedAgain = derefedRef.derefSoft()
				if ( derefedAgain in Ref )
					return new CalcErr( error: BladeString.of(
							"A hard ask was continued before it was"
						 + " fulfilled." ) )
				
				return new CalcResult( value: body( derefedAgain ) )
			}
		)
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

final class Misc
{
	private Misc() {}
	
	static let( Closure f ) { f() }
	
	static boolean anyNonDir( File file, Closure body )
	{
		// We don't really care about the traversal order, but we're
		// making a point to avoid JVM recursion so that we don't get
		// a StackOverflowError.
		
		def toGo = [ file ]
		
		while ( !toGo.isEmpty() )
		{
			def thisFile = (File)toGo.pop()
			
			if ( thisFile.isFile() )
			{
				if ( body( thisFile ) )
					return true
			}
			else for ( child in thisFile.listFiles() )
				toGo.add child
		}
		
		return false
	}
	
	static void eachNonDir( File file, Closure body )
	{
		anyNonDir file, {
			
			body it
			return false
		}
	}
	
	static Set< File > getNonDirs( File directory )
	{
		if ( null.is( directory ) || !directory.exists() )
			return null
		
		def result = []
		eachNonDir( new File( directory.toURI() ), result.&add )
		return result as Set
	}
}
