// DocumentLocations.groovy
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


package com.rocketnia.blade.parse

import com.rocketnia.blade.*


class DocumentLocation
{
	final int lineNumber
	final LineLocation lineLocation
	
	protected DocumentLocation(
		int lineNumber, LineLocation lineLocation )
	{
		this.lineNumber = lineNumber
		this.lineLocation = lineLocation
	}
	
	static DocumentLocation of(
		int lineNumber, LineLocation lineLocation )
		{ new DocumentLocation( lineNumber, lineLocation ) }
	
	static DocumentLocation of( int lineNumber, int... counts )
		{ of lineNumber, LineLocation.of( counts ) }
	
	static DocumentLocation of( int lineNumber, String model )
		{ of lineNumber, LineLocation.of( model ) }
	
	private isoRep()
		{ [ DocumentLocation, lineNumber, lineLocation ] }
	int hashCode() { isoRep().hashCode() }
	boolean equals( Object o ) { !null.is( o ) && (is( o ) ||
		Misc.let { Class c = owner.class, oc = o.class -> c.is( oc ) ?
			((DocumentLocation)o).isoRep().equals( isoRep() ) :
			c.isAssignableFrom( oc ) && o.equals( this ) }) }
	
	String toString() { "$lineNumber:$lineLocation" }
	
	// This returns null, -1, 0, or 1. A result of null means that the
	// values are incomparable.
	Integer lenientCompare( DocumentLocation other )
	{
		def otherLineNumber = other.lineNumber
		
		if ( lineNumber < otherLineNumber )
			return -1
		else if ( lineNumber == otherLineNumber )
			return lineLocation.prefixCompare( other.lineLocation )
		else
			return 1
	}
	
	boolean lte( DocumentLocation other )
		{ lenientCompare( other ) in [ -1, 0 ] }
}

class DocumentSelection
{
	final DocumentLocation start
	final DocumentLocation stop
	
	protected DocumentSelection(
		DocumentLocation start, DocumentLocation stop )
	{
		this.start = start
		this.stop = stop
	}
	
	static DocumentSelection of(
		DocumentLocation start, DocumentLocation stop )
	{
		if ( !start.lte( stop ) )
			throw new IllegalArgumentException(
				"A selection can't start with $start and stop with"
			 + " $stop." )
		
		return new DocumentSelection( start, stop )
	}
	
	private isoRep() { [ DocumentSelection, start, stop ] }
	int hashCode() { isoRep().hashCode() }
	boolean equals( Object o ) { !null.is( o ) && (is( o ) ||
		Misc.let { Class c = owner.class, oc = o.class -> c.is( oc ) ?
			((DocumentSelection)o).isoRep().equals( isoRep() ) :
			c.isAssignableFrom( oc ) && o.equals( this ) }) }
	
	String toString() { "$start-$stop" }
	
	static DslFrom from( DocumentLocation from )
		{ new DslFrom( from: from ) }
	
	static DslFrom from( int lineNumber, LineLocation lineLocation )
		{ from DocumentLocation.of( lineNumber, lineLocation ) }
	
	static DslFrom from( int lineNumber, int... lineLocation )
		{ from DocumentLocation.of( lineNumber, lineLocation ) }
	
	static DslFrom from( int lineNumber, String lineLocation )
		{ from DocumentLocation.of( lineNumber, lineLocation ) }
	
	static class DslFrom
	{
		DocumentLocation from
		
		DocumentSelection to( DocumentLocation to )
			{ DocumentSelection.of from, to }
		
		DocumentSelection to(
			int lineNumber, LineLocation lineLocation )
			{ to DocumentLocation.of( lineNumber, lineLocation ) }
		
		DocumentSelection to( int lineNumber, int... lineLocation )
			{ to DocumentLocation.of( lineNumber, lineLocation ) }
		
		DocumentSelection to( int lineNumber, String lineLocation )
			{ to DocumentLocation.of( lineNumber, lineLocation ) }
		
		DocumentSelection to( LineLocation lineLocation )
			{ to from.lineNumber, lineLocation }
		
		DocumentSelection to( String lineLocation )
			{ to from.lineNumber, lineLocation }
		
		DocumentSelection plus( int spaces )
			{ to from.lineNumber, from.lineLocation + spaces }
	}
}
