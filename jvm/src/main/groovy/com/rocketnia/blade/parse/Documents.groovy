// Documents.groovy
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

import java.util.regex.Pattern


final class Documents
{
	private Documents() {}
	
	static String contents( String line, LineLocation start )
	{
		def startSize = start.size()
		def before = line.substring( 0, startSize )
		if ( LineLocation.of( before ) != start )
			throw new IndexOutOfBoundsException(
				"The line ${line.inspect()} has no location $start." )
		
		return line.substring( startSize )
	}
	
	static String contents( String line, int start, LineLocation end )
	{
		if ( null.is( line ) )
			throw new NullPointerException()
		
		def endSize = end.size()
		
		if ( endSize < start )
			throw new IndexOutOfBoundsException()
		
		def before = line.substring( 0, endSize )
		if ( LineLocation.of( before ) != end )
			throw new IndexOutOfBoundsException()
		
		return before.substring( start )
	}
	
	static String contents(
		String line, LineLocation start, LineLocation end )
	{
		if ( null.is( line ) )
			throw new NullPointerException()
		
		if ( !start.prefixLte( end ) )
			throw new IndexOutOfBoundsException()
		
		return contents( line, start.size(), end )
	}
	
	static List< String > contents(
		Document document, DocumentSelection selection )
	{
		DocumentLocation stop = selection.stop
		int stopLine = stop.lineNumber
		
		if ( document.size() <= stopLine )
			throw new IndexOutOfBoundsException()
		
		DocumentLocation start = selection.start
		int startLine = start.lineNumber
		
		if ( startLine == stopLine )
			return [ contents( document[ startLine ],
				start.lineLocation, stop.lineLocation ) ]
		
		def result =
			[ contents( document[ startLine ], start.lineLocation ) ]
		
		// Calculate this early so we can fail fast.
		def lastResultLine =
			contents( document[ stopLine ], 0, stop.lineLocation )
		
		for ( int i = startLine + 1; i < stopLine; i++ )
			result.add document[ i ]
		
		result.add lastResultLine
		
		return result
	}
	
	static DocumentSelection selectLines(
		Document document, int firstLine, int lastLine )
		{ DocumentSelection.of DocumentLocation.of( firstLine ),
			DocumentLocation.of( lastLine, document[ lastLine ] ) }
	
	static DocumentSelection selectAll( Document document )
	{
		int documentSize = document.size()
		if ( documentSize == 0 )
			throw new IllegalArgumentException()
		
		return selectLines( document, 0, documentSize - 1 )
	}
	
	static String getAt( String line, LineLocation index )
		{ contents( line, index )[ 0 ] }
	
	static String getAt( Document document, DocumentLocation index )
		{ getAt document[ index.lineNumber ], index.lineLocation }
}

interface Document extends Iterable< String >
{
	String getAt( int index )
	int size()
	Iterator< String > iterator()
	List< String > asList()
}

class ListDocument implements Document
{
	protected List< String > list
	
	private static final Pattern newline = ~/\n|\r\n?/
	private static final Pattern rtrimmer = ~/[ \t]*$/
	
	protected ListDocument( List< String > list )
	{
		this.list = list
	}
	
	static ListDocument of( List< String > list )
	{
		if ( list.any { null.is it } )
			throw new NullPointerException()
		
		if ( list.any{ (
			!(it in String)
			|| newline.matcher( it ).matches()
		) } )
			throw new IllegalArgumentException()
		
		def contents =
			list.collect { rtrimmer.matcher( it ).replaceFirst '' }
		
		while (
			!contents.isEmpty()
			&& ((String)contents.last()).isEmpty()
		)
			contents.pop()
		
		return new ListDocument( contents )
	}
	
	static ListDocument of( String string )
		{ of newline.split( string ) as List }
	
	String getAt( int index ) { list[ index ] }
	int size() { list.size() }
	
	Iterator< String > iterator()
	{
		def innerIterator = list.iterator()
		
		return new Iterator< String >() {
			private Iterator inner = innerIterator
			
			boolean hasNext() { inner.hasNext() }
			String next() { inner.next() }
			
			void remove()
				{ throw new UnsupportedOperationException() }
		}
	}
	
	List< String > asList() { [] + list }
}
