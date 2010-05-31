// BladeParser.groovy
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


final class BladeParser
{
	private static final int openCp = '['.codePointAt( 0 )
	private static final int closeCp = ']'.codePointAt( 0 )
	private static final List< Integer > whiteCps =
		[ ' '.codePointAt( 0 ), '\t'.codePointAt( 0 ) ]
	
	private BladeParser() {}
	
	// This returns a three-element list containing a
	// DocumentSelection selecting the string, an Integer representing
	// the closing code point, and a final DocumentLocation. If this
	// reaches the end of the selection, the nonexistent code point
	// will be represented as null.
	private static List parseCloseableStringIncremental(
		Document code, DocumentSelection range )
	{
		def start = range.start
		def limit = range.stop
		def startLineNumber = start.lineNumber
		def startLocation = start.lineLocation
		def limitLineNumber = limit.lineNumber
		
		def closeResult = { lineNumber, String line, index ->
			
			def nextColumn =
				LineLocation.of( line.substring( 0, index + 1 ) )
			def stopColumn = nextColumn.prefix()
			def cp = line.codePointAt( index )
			def stop = DocumentLocation.of( lineNumber, stopColumn )
			def next = DocumentLocation.of( lineNumber, nextColumn )
			return [ DocumentSelection.of( start, stop ), cp, next ]
		}
		
		def closeIndex = { String string ->
			
			def openIndex = string.indexOf( openCp )
			def closeIndex = string.indexOf( closeCp )
			
			if ( openIndex < 0 )
				return closeIndex < 0 ? null : closeIndex
			else
				return closeIndex < 0 ?
					openIndex : Math.min( openIndex, closeIndex )
		}
		
		// We assume that the location is actually in the document.
		def startLine = code[ startLineNumber ]
		
		def oneLine = startLineNumber == limitLineNumber
		
		def startRemainder = oneLine ?
			Documents.contents( startLine, startLocation,
				limit.lineLocation ) :
			Documents.contents( startLine, startLocation )
		
		def remainderIndex = closeIndex( startRemainder )
		if ( remainderIndex != null )
			return closeResult( startLineNumber, startLine,
				startLocation.size() + remainderIndex )
		
		if ( oneLine )
			return [ range, null, limit ]
		
		for ( lineNumber in (startLineNumber + 1)..<limitLineNumber )
		{
			def line = code[ lineNumber ]
			
			def index = closeIndex( line )
			if ( index != null )
				return closeResult( lineNumber, line, index )
		}
		
		def limitLine = code[ limitLineNumber ]
		def limitOnset =
			Documents.contents( limitLine, 0, limit.lineLocation )
		def index = closeIndex( limitOnset )
		if ( index != null )
			return closeResult( limitLineNumber, limitLine, index )
		
		return [ range, null, limit ]
	}
	
	// This parses a selection that contains matching square brackets.
	// It returns a markup list, where a markup list is an alternating
	// list of DocumentSelection values and markup lists, beginning
	// and ending with DocumentSelection values, such that the
	// DocumentSelection values in the flattened list are in order,
	// with one-space-width gaps between them (to account for the
	// brackets).
	//
	// For instance, a document with one line containing "ab[cd[]]"
	// will end up parsed so that, when the DocumentSelection values
	// are replaced with their selected strings, the result is
	// [ "ab", [ "cd", [ "" ], "", ] "" ].
	//
	// If this encounters unbalanced brackets, it will throw a
	// ParseException.
	//
	static List parseBrackets(
		Document code, DocumentSelection range )
	{
		def thisStart = range.start
		def limit = range.stop
		
		def pendingResults = []
		def result = []
		
		def pushDown = { -> pendingResults.add result; result = [] }
		
		def popUpFails = { ->
			
			if ( pendingResults.isEmpty() )
				return true
			
			def oldResult = result
			result = (List)pendingResults.pop()
			result.add oldResult
			return false
		}
		
		while ( true )
		{
			def ( selection, codePoint, nextStart ) =
				parseCloseableStringIncremental(
					code, DocumentSelection.of( thisStart, limit ) )
			
			thisStart = nextStart
			result.add selection
			
			if ( codePoint == null )
			{
				if ( pendingResults.isEmpty() )
					return result
				else
					throw new ParseException(
						"There were open-heavy [ ] brackets." )
			}
			
			assert codePoint in [ openCp, closeCp ]
			
			if ( codePoint == openCp )
				pushDown()
			else if ( popUpFails() )
				throw new ParseException(
					"There were close-heavy [ ] brackets." )
		}
	}
	
	static List parseBrackets( Document code )
		{ parseBrackets code, Documents.selectAll( code ) }
	
	static List parseBrackets( String code )
		{ parseBrackets ListDocument.of( code ) }
	
	// This will return a list of DocumentSelections, one for each
	// paragraph in the document, where a paragraph begins on any
	// unindented line preceded by an empty line and continues until
	// it reaches a sequence of empty lines followed by another
	// paragraph. Thus, a paragraph begins and ends with nonempty
	// lines, and its first line is unindented.
	//
	// If the first nonempty line of the document is indented, then
	// at least one line of the document is not part of any paragraph,
	// and there is a parse error. If this happens, an ErrorSelection
	// is included in the result list, selecting the offending lines.
	//
	static List< DocumentSelection > parseParagraphs( Document code )
	{
		if ( code.size() == 0 )
			return []
		
		def emptyLineNumbers = code.findIndexValues { it.isEmpty() }.
			collect { it as Integer }
		
		def firstIsWhite = { it.codePointAt( 0 ) in whiteCps }
		
		def isUnindented = { !(it.isEmpty() || it in firstIsWhite) }
		
		// Note that an empty line will never come at the end of a
		// Document, so the index for code will always exist.
		def startLineNumbers = ([ 0 ] + emptyLineNumbers*.plus( 1 )).
			findAll { code[ it ] in isUnindented }
		
		def beforeStart = startLineNumbers.head() - 1
		def hasFalseStart = !(
			beforeStart < 0
			|| (beforeStart < emptyLineNumbers.size()
				&& beforeStart == emptyLineNumbers[ beforeStart ])
		)
		
		if ( hasFalseStart )
		{
			int falseStart = 0
			for ( empty in emptyLineNumbers )
			{
				if ( falseStart != empty )
					break
				
				falseStart++
			}
			
			startLineNumbers = [ falseStart ] + startLineNumbers
		}
		
		def treeSetOfEmptyLines = emptyLineNumbers as TreeSet
		def stopLineNumbers =
			(startLineNumbers.tail() + [ code.size() ]).collect {
				
				int result = it - 1
				for ( empty in treeSetOfEmptyLines.headSet( it ).
					descendingIterator() )
				{
					if ( result != empty )
						return result
					
					result--
				}
				
				return result
			}
		
		def result = (0..<startLineNumbers.size()).
				collect { Documents.selectLines code,
					startLineNumbers[ it ], stopLineNumbers[ it ] }
		
		if ( hasFalseStart )
			result[ 0 ] = new ParseException(
					"Every line before the first unindented line must"
				 + " be empty." ).selected( result[ 0 ] )
		
		return result
	}
	
	// This uses parseBrackets() on the DocumentSelection values
	// output by parseParagraphs() and returns a list containing only
	// those markup lists which are inside brackets, ignoring other
	// parts of the document entirely.
	//
	// If parseParagraphs() gives an ErrorSelection in its result or
	// parseBrackets() throws a ParseError, the result of this will
	// include the appropriate ErrorSelection value in its result
	// list.
	//
	static List parseParagraphedBrackets( Document code )
	{
		return parseParagraphs( code ).
			findAll { it in ErrorSelection ||
				Documents.getAt( code, it.start ) == '[' }.
			collect {
				
				if ( it in ErrorSelection )
					return [ it ]
				
				def parsedBrackets
				
				try { parsedBrackets = parseBrackets( code, it ) }
				catch ( ParseException e )
					{ return [ e.selected( it ) ] }
				
				return parsedBrackets.findAll { it in List }
			}.sum( [] )
	}
	
	static List parseParagraphedBrackets( String code )
		{ parseParagraphedBrackets ListDocument.of( code ) }
	
	static Set parseProject( File root )
	{
		if ( null.is( root ) || !root.exists() )
			return null
		
		Set result = []
		for ( File file: Misc.getNonDirs( root ).
			findAll { it.getName() =~ /\.blade$/ } )
		{
			def relativeParts = []
			for (
				File parent = file;
				!parent.equals( root );
				parent = parent.getParentFile()
			)
				relativeParts.add parent.getName()
			
			def path = relativeParts.reverse().join( '/' )
			def doc = new ListDocument( file.readLines() )
			for ( brackets in parseParagraphedBrackets( doc ) )
				result.add brackets in ErrorSelection ? brackets :
					new BracketView(
						path: path, doc: doc, brackets: brackets )
		}
		
		return result
	}
}

class BracketView implements Blade
{
	String path
	Document doc
	List brackets
	
	String toString()
		{ "BracketView( ${path.inspect()}, ${brackets.inspect()} )" }
}
