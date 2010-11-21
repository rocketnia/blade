// ParseErrors.groovy
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


class ParseException extends RuntimeException
{
	ParseException() { super() }
	ParseException( String message ) { super( message ) }
	
	def selected( DocumentSelection selection )
		{ new ErrorSelection( this.toString(), selection ) }
	
	String toString()
		{ getClass().getSimpleName() +
			(null.is( message ) ? "" : "($message)") }
}

class ErrorSelection
{
	final message
	final DocumentSelection selection
	
	ErrorSelection( message, DocumentSelection selection )
	{
		this.message = message
		this.selection = selection
	}
	
	String toString() { "$message@$selection" }
	
	private isoRep() { [ ErrorSelection, message, selection ] }
	int hashCode() { isoRep().hashCode() }
	boolean equals( Object o ) { !null.is( o ) && (is( o ) ||
		Misc.let { Class c = owner.class, oc = o.class -> c.is( oc ) ?
			((ErrorSelection)o).isoRep().equals( isoRep() ) :
			c.isAssignableFrom( oc ) && o.equals( this ) }) }
}
