// BracketUtils.groovy
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


final class BracketUtils
{
	private BracketUtils() {}
	
	private static final whitespacePattern = ~/\s/
	
	static List ltrim(
		Document doc, List brackets, condition = whitespacePattern )
	{
		def ( List before, List after ) =
			splitBeforeFirst( doc, brackets ) { !(it in condition) }
		
		if ( null.is( after ) )
			return brackets
		
		return after
	}
	
	static List splitBeforeFirst(
		Document doc, List brackets, condition )
	{
		def ( List before, middle, List after ) =
			splitAtFirst( doc, brackets, condition )
		
		if ( null.is( middle ) )
			return [ brackets, null ]
		
		def beforeStop = ((DocumentSelection)before.last()).stop
		if ( middle in List )
			return [ before, [
				DocumentSelection.of( beforeStop, beforeStop ),
				middle
			] + after ]
		else
			return [ before, [ DocumentSelection.of(
				beforeStop,
				((DocumentSelection)after.head()).stop
			) ] + after.tail() ]
	}
	
	static List splitAtFirst( Document doc, List brackets, condition )
	{
		def conditionClosure = { it in condition }
		
		def before = []
		def middle = null
		def after = null
		
		for ( part in brackets )
		{
			if ( !null.is( middle ) )
			{
				after.add part
				continue
			}
			
			if ( part in List )
			{
				if ( part in condition )
				{
					middle = part
					after = []
				}
				else
					before.add part
				
				continue
			}
			
			if ( !(part in DocumentSelection) )
				throw new IllegalArgumentException()
			
			part = (DocumentSelection)part
			
			def contents = Documents.contents( doc, part )
			
			assert !contents.isEmpty() &&
				contents.every { it in String }
			
			def head = contents.head()
			def tail = contents.tail()
			
			def i = head.findIndexOf( conditionClosure )
			if ( i != -1 )
			{
				def start = part.start
				def splitLineLocation = start.lineLocation.plus(
					head.substring( 0, i + 1 ) )
				before.add DocumentSelection.
					from( start ).to( splitLineLocation.prefix() )
				middle = head[ i ]
				after = [ DocumentSelection.
					from( start.lineNumber, splitLineLocation ).
					to( part.stop ) ]
			}
			else if ( tail.isEmpty() )
				before.add part
			else if ( '\n' in condition )
			{
				before.add DocumentSelection.from( part.start ) + head
				middle = '\n'
				after = []
				after.add DocumentSelection.
					from( part.start.lineNumber + 1 ).to( part.stop )
			}
			else
			{
				for ( lineIndex in 0..<tail.size() )
				{
					def line = tail[ lineIndex ]
					def j = line.findIndexOf( conditionClosure )
					if ( j == -1 )
						continue
					
					def start = part.start
					def splitLineNumber =
						start.lineNumber + 1 + lineIndex
					def splitLineLocation =
						LineLocation.of( tail.substring( 0, j + 1 ) )
					
					before.add DocumentSelection.from( start ).to(
							splitLineNumber,
							splitLineLocation.prefix()
						)
					middle = line[ j ]
					after = [ DocumentSelection.
						from( splitLineNumber, splitLineLocation ).
						to( part.stop ) ]
					break
				}
				
				if ( null.is( middle ) )
					before.add part
			}
		}
		
		return [ before, middle, after ]
	}
	
	// This will split one bracket-set into an odd-sized list of
	// bracket-sets such that every even (zero-indexed) element of the
	// result list satisfies the condition all the way through, and
	// every odd element of the result list satisfies the complement
	// of the condition all the way through. In this way, the first
	// and last elements are the padding, the other even elements are
	// the inner spacing, and the odd elements are the tokens.
	static List splitAlternating(
		Document doc, List brackets, condition )
	{
		def result = []
		def resultElement = []
		def conditionExpectation = true
		def previousStop = null
		def surprise = { conditionExpectation != (it in condition) }
		
		def bank = { ->
			
			assert resultElement.first() in DocumentSelection
			assert resultElement.last() in DocumentSelection
			result.add resultElement
			resultElement = []
			conditionExpectation = !conditionExpectation
		}
		
		for ( part in brackets )
		{
			if ( part in List )
			{
				if ( surprise( part ) )
				{
					bank()
					
					if ( null.is( previousStop ) )
						throw new IllegalArgumentException()
					
					resultElement.add DocumentSelection.of(
						previousStop, previousStop )
					
					previousStop = null
				}
				
				resultElement.add part
				continue
			}
			
			if ( !(
				part in DocumentSelection && null.is( previousStop )
			) )
				throw new IllegalArgumentException()
			
			part = (DocumentSelection)part
			
			def contents =
				Documents.contents( doc, part ).join( '\n' )
			def contentsStart = part.start
			previousStop = part.stop
			
			while ( true )
			{
				def i = contents.findIndexOf( surprise )
				if ( i == -1 )
				{
					resultElement.add DocumentSelection.of(
						contentsStart, previousStop )
					break
				}
				
				def splitLocation =
					contentsStart + contents.substring( 0, i )
				
				resultElement.add DocumentSelection.of(
					contentsStart, splitLocation )
				bank()
				
				contents = contents.substring( i )
				contentsStart = splitLocation
			}
		}
		
		bank()
		
		if ( null.is( previousStop ) )
			throw new IllegalArgumentException()
		
		if ( result.size() % 2 == 0 )
			result.add( [ DocumentSelection.of(
				previousStop, previousStop ) ] )
		
		return result
	}
	
	private static List everyOther(
		Iterable input, boolean takingFirst )
	{
		def result = []
		def takingThisOne = takingFirst
		
		for ( elem in input )
		{
			if ( takingThisOne )
				result.add elem
			
			takingThisOne = !takingThisOne
		}
		
		return result
	}
	
	static List evens( Iterable input ) { everyOther input, true }
	static List odds( Iterable input ) { everyOther input, false }
	
	static List tokens(
		Document doc, List brackets, condition = whitespacePattern )
		{ odds splitAlternating( doc, brackets, condition ) }
}
