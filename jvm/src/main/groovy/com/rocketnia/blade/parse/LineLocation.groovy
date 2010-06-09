// LineLocation.groovy
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


class LineLocation
{
	// This is the number of space-width things, followed by the
	// number of tab-width things, followed by the number of
	// space-width things, etc. that come before this location in the
	// line.
	protected List< Integer > counts
	
	// This is the sum of counts, precalculated so that accessing it
	// takes constant rather than linear time.
	protected int mySize
	
	protected LineLocation( List< Integer > counts, int mySize )
	{
		if ( !counts.isEmpty() )
		{
			def head = counts.head()
			if ( !(head in { it in Integer && 0 <= it }) )
				throw new IllegalArgumentException(
					"The first count must be nonnegative, not $head."
				)
			
			def tail = counts.tail()
			if ( !tail.every { it in Integer && 0 < it } )
				throw new IllegalArgumentException()
			
			if ( head == 0 && tail.isEmpty() )
				throw new IllegalArgumentException()
		}
		
		if ( counts.sum( 0 ) != mySize )
			throw new IllegalArgumentException()
		
		this.counts = counts
		this.mySize = mySize
	}
	
	protected LineLocation( List< Integer > counts )
		{ this( counts, counts.sum( 0 ) ) }
	
	static LineLocation valueOf( List< Integer > counts )
	{
		if ( !counts.isEmpty() )
		{
			def head = counts.head()
			if ( !(head in { it in Integer && 0 <= it }) )
				throw new IllegalArgumentException(
					"The first count must be nonnegative, not $head."
				)
			
			def tail = counts.tail()
			if ( !tail.every { it in Integer && 0 < it } )
				throw new IllegalArgumentException()
			
			if ( head == 0 && tail.isEmpty() )
				throw new IllegalArgumentException()
		}
		
		return new LineLocation( [] + counts )
	}
	
	static LineLocation of( int... counts )
	{
		if ( counts == null )
			throw new NullPointerException()
		
		return valueOf( counts as List )
	}
	
	static LineLocation of( String model )
	{
		def modelSize = model.size()
		
		if ( modelSize == 0 )
			return new LineLocation( [], 0 )
		
		List resultList = []
		boolean tabs = false
		int currentNumber = 0
		
		def bank = { ->
			
			resultList.add currentNumber
			currentNumber = 0
			tabs = !tabs
		}
		
		def addSpace = { -> if ( tabs ) bank(); currentNumber++ }
		def addTab = { -> if ( !tabs ) bank(); currentNumber++ }
		
		for ( i in 0..<modelSize )
		{
			switch ( model[ i ] )
			{
			case '\t':
				addTab()
				break
				
			case '\r':
			case '\n':
				throw new IllegalArgumentException()
				
			default:
				addSpace()
			}
		}
		
		bank()
		
		return new LineLocation( resultList, modelSize )
	}
	
	private isoRep() { [ LineLocation, counts ] }
	int hashCode() { isoRep().hashCode() }
	boolean equals( Object o ) { !null.is( o ) && (is( o ) ||
		Misc.let { Class c = owner.class, oc = o.class -> c.is( oc ) ?
			((LineLocation)o).isoRep().equals( isoRep() ) :
			c.isAssignableFrom( oc ) && o.equals( this ) }) }
	
	private static final noCase = new Object()
	private static final comparableCase = { null.is it }
	private static final ltCase = -1
	private static final lteCase = [ -1, 0 ]
	
	// One position is prefix-earlier than another if and only if its
	// tab-and-spaces string is a proper prefix of the other's string.
	//
	// Every position is as at least as prefix-early as itself. No
	// pairs of distinct positions are equivalent this way.
	//
	// The result of this is null, -1, 0, or 1. A result of null means
	// that the values are incomparable.
	//
	// If expectedResult is boolean false, this returns the result.
	// Otherwise, expectedResult must be either incomparableCase,
	// ltCase, or lteCase, and this returns true if the result is in
	// { it in expectedResult }. Specifying expectedResult aids in
	// efficiency.
	//
	private _prefixCompare( LineLocation other, expectedResult )
	{
		if ( other == null )
			throw new NullPointerException()
		
		def res = { expectedResult.is( noCase ) ?
			it : it in expectedResult }
		
		def otherCounts = other.counts
		
		if ( counts == otherCounts )
			return res( 0 )
		
		// The result can no longer be 0.
		
		if ( otherCounts.size() < counts.size()
			&& [ ltCase, lteCase ].any { it.is expectedResult } )
			return false
		
		def a = counts.reverse()
		def b = otherCounts.reverse()
		
		while ( !(a.isEmpty() || b.isEmpty()) )
		{
			switch ( a.pop() <=> b.pop() )
			{
			case 0:	break
			case { it < 0 }: return res( a.isEmpty() ? -1 : null )
			default: return res( b.isEmpty() ? 1 : null )
			}
		}
		
		return res( a.isEmpty() ? -1 : 1 )
	}
	
	// This returns null, -1, 0, or 1. A result of null means that the
	// values are incomparable.
	Integer prefixCompare( LineLocation other )
		{ _prefixCompare other, noCase }
	
	// This returns -1, 0, or 1. A result of 0 means that the values
	// are either equal or incomparable.
	int prefixForceCompare( LineLocation other )
	{
		def unforcedResult = prefixCompare( other )
		return unforcedResult == null ? 0 : unforcedResult
	}
	
	boolean prefixComparable( LineLocation other )
		{ _prefixCompare other, comparableCase }
	
	boolean prefixLt( LineLocation other )
		{ _prefixCompare other, ltCase }
	
	boolean prefixLte( LineLocation other )
		{ _prefixCompare other, lteCase }
	
	boolean prefixGt( LineLocation other ) { other.prefixLt this }
	boolean prefixGte( LineLocation other ) { other.prefixLte this }
	
	// One position is insert-earlier than another if and only if the
	// tabs and spaces in its string occur in the same order in the
	// other's string but with at least one additional tab or space
	// among or beside them.
	//
	// Every position is as at least as insert-early as itself. No
	// pairs of distinct positions are equivalent this way.
	//
	// This returns null, -1, 0, or 1. A result of null means that the
	// values are incomparable.
	//
	private _insertCompare( LineLocation other, expectedResult )
	{
		if ( other == null )
			throw new NullPointerException()
		
		def res = { expectedResult.is( noCase ) ?
			it : it in expectedResult }
		
		def a = counts
		def b = other.counts
		int aLtB = -1
		
		switch ( a.size() <=> b.size() )
		{
		case 0:
			if ( expectedResult.is( ltCase ) )
				return false
			
			return res( a == b ? 0 : null )
			
		case { it < 0 }:
			if ( [ ltCase, lteCase ].any { it.is expectedResult } )
				return false
			
			def temp = a
			a = b
			b = temp
			aLtB = 1
			break
			
		default: break
		}
		
		a = a.reverse()
		b = b.reverse()
		
		while ( !(a.isEmpty() || b.isEmpty()) )
		{
			def diff = a.pop() - b.pop()
			if ( diff <= 0 )
				continue
			else if ( b.isEmpty() )
				return res( null )
			else
			{
				a.push diff
				b.pop()
			}
		}
		
		return res( a.isEmpty() ? aLtB : null )
	}
	
	// This returns null, -1, 0, or 1. A result of null means that the
	// values are incomparable.
	Integer insertCompare( LineLocation other )
		{ _insertCompare other, noCase }
	
	// This returns -1, 0, or 1. A result of 0 means that the values
	// are either equal or incomparable.
	int insertForceCompare( LineLocation other )
	{
		def unforcedResult = insertCompare( other )
		return unforcedResult == null ? 0 : unforcedResult
	}
	
	boolean insertComparable( LineLocation other )
		{ _insertCompare other, comparableCase }
	
	boolean insertLt( LineLocation other )
		{ _insertCompare other, ltCase }
	
	boolean insertLte( LineLocation other )
		{ _insertCompare other, lteCase }
	
	boolean insertGt( LineLocation other ) { other.insertLt this }
	boolean insertGte( LineLocation other ) { other.insertLte this }
	
	// One position is substitute-earlier than another if and only if
	// the second position's tab-and-spaces string can be obtained by
	// transforming one or more spaces in the first position's string
	// into tabs.
	//
	// Every position is as at least as substitute-early as itself. No
	// pairs of distinct positions are equivalent this way.
	//
	// This returns null, -1, 0, or 1. A result of null means that the
	// values are incomparable.
	//
	private _substituteCompare( LineLocation other, expectedResult )
	{
		if ( other == null )
			throw new NullPointerException()
		
		def res = { expectedResult.is( noCase ) ?
			it : it in expectedResult }
		
		if ( isEmpty() != other.isEmpty() )
			return res( null )
		
		def a = [] + counts
		def b = [] + other.counts
		
		if ( a == b )
			return res( 0 )
		
		def aSuspectedLonger = false
		def bSuspectedLonger = false
		
		def mustntTheLongerBeA = { ->
			
			if ( !aSuspectedLonger
				&& [ ltCase, lteCase ].any { it.is expectedResult } )
				return true
			
			aSuspectedLonger = true
			return bSuspectedLonger
		}
		
		def mustntTheLongerBeB =
			{ -> bSuspectedLonger = true; return aSuspectedLonger }
		
		def aTabs = false
		def bTabs = false
		while ( !(a.isEmpty() || b.isEmpty()) )
		{
			def diff = a.pop() - b.pop()
			if ( diff == 0 )
			{
				aTabs = !aTabs
				bTabs = !bTabs
			}
			else if ( diff < 0 )
			{
				if ( aTabs )
				{
					if ( bTabs )
						if ( mustntTheLongerBeB() )
							return res( null )
				}
				else
				{
					if ( !bTabs )
						if ( mustntTheLongerBeA() )
							return res( null )
				}
				
				aTabs = !aTabs
				b.push -diff
			}
			else
			{
				if ( aTabs )
				{
					if ( bTabs )
						if ( mustntTheLongerBeA() )
							return res( null )
				}
				else
				{
					if ( !bTabs )
						if ( mustntTheLongerBeB() )
							return res( null )
				}
				
				a.push diff
				bTabs = !bTabs
			}
		}
		
		if ( aSuspectedLonger )
			return res( 1 )
		
		assert bSuspectedLonger
		return res( -1 )
	}
	
	// This returns null, -1, 0, or 1. A result of null means that the
	// values are incomparable.
	Integer substituteCompare( LineLocation other )
		{ _substituteCompare other, noCase }
	
	// This returns -1, 0, or 1. A result of 0 means that the values
	// are either equal or incomparable.
	int substituteForceCompare( LineLocation other )
	{
		def unforcedResult = substituteCompare( other )
		return unforcedResult == null ? 0 : unforcedResult
	}
	
	boolean substituteComparable( LineLocation other )
		{ _substituteCompare other, comparableCase }
	
	boolean substituteLt( LineLocation other )
		{ _substituteCompare other, ltCase }
	
	boolean substituteLte( LineLocation other )
		{ _substituteCompare other, lteCase }
	
	boolean substituteGt( LineLocation other )
		{ other.substituteLt this }
	
	boolean substituteGte( LineLocation other )
		{ other.substituteLte this }
	
	// One position is indent-earlier than another if and only if some
	// position at least as insert-late as the first position is
	// prefix-earlier than some position at least as substitute-early
	// as the second position.
	//
	// In other words, one position is indent-earlier than another if
	// and only if it can have characters inserted anywhere within its
	// tab-and-spaces string, with at least one at the end, in order
	// for that string to become the same as some improper
	// tab-to-space substitution of the second position's string.
	//
	// In other words, one position is indent-earlier than another if
	// and only if, for every tab-start-to-tab-end function f on the
	// nonnegative integers with f( m ) <= f( n ) for all nonnegative
	// integers m <= n and n < f( n ) for all nonnegative integers n,
	// the space-only monospace rendering of the first position's
	// tab-and-spaces string has strictly fewer characters than that
	// of the second position's string. This is the actual intent of
	// this function; if either of the other descriptions is
	// inconsistent with this one, this one wins out.
	//
	// Every position is as at least as indent-early as itself. No
	// pairs of distinct positions are equivalent this way.
	//
	// This returns null, -1, 0, or 1. A result of null means that the
	// values are incomparable.
	//
	private _indentCompare( LineLocation other, expectedResult )
	{
		if ( other == null )
			throw new NullPointerException()
		
		def res = { expectedResult.is( noCase ) ?
			it : it in expectedResult }
		
		def a = counts.reverse()
		def b = other.counts.reverse()
		
		if ( a == b )
			return res( 0 )
		
		def aSuspectedLonger = false
		def bSuspectedLonger = false
		
		def mustntTheLongerBeA = { ->
			
			if ( !aSuspectedLonger
				&& [ ltCase, lteCase ].any { it.is expectedResult } )
				return true
			
			aSuspectedLonger = true
			return bSuspectedLonger
		}
		
		def mustntTheLongerBeB =
			{ -> bSuspectedLonger = true; return aSuspectedLonger }
		
		def aTabs = false
		def bTabs = false
		while ( !(a.isEmpty() || b.isEmpty()) )
		{
			def aCount = a.pop()
			def bCount = b.pop()
			
			if ( aTabs == bTabs )
			{
				if ( aCount == bCount )
				{
					aTabs = !aTabs
					bTabs = !bTabs
				}
				else if ( aCount < bCount )
				{
					aTabs = !aTabs
					b.push bCount - aCount
				}
				else
				{
					a.push aCount - bCount
					bTabs = !bTabs
				}
			}
			else if ( aTabs )
			{
				if ( mustntTheLongerBeB() )
				{
					if ( [ ltCase, lteCase ].
						any { it.is expectedResult } )
						return false
					
					return res(
						b.isEmpty() && bCount < a.sum( aCount ) ?
							1 : null
					)
				}
				
				a.push aCount
				bTabs = true
			}
			else
			{
				if ( mustntTheLongerBeA() )
					return res(
						a.isEmpty() && aCount < b.sum( bCount ) ?
							-1 : null
					)
				
				aTabs = true
				b.push bCount
			}
		}
		
		if ( aSuspectedLonger )
			return res( a.isEmpty() ? null : 1 )
		
		assert bSuspectedLonger
		return res( b.isEmpty() ? null : -1 )
	}
	
	// This returns null, -1, 0, or 1. A result of null means that the
	// values are incomparable.
	Integer indentCompare( LineLocation other )
		{ _indentCompare other, noCase }
	
	// This returns -1, 0, or 1. A result of 0 means that the values
	// are either equal or incomparable.
	int indentForceCompare( LineLocation other )
	{
		def unforcedResult = indentCompare( other )
		return unforcedResult == null ? 0 : unforcedResult
	}
	
	boolean indentComparable( LineLocation other )
		{ _indentCompare other, comparableCase }
	
	boolean indentLt( LineLocation other )
		{ _indentCompare other, ltCase }
	
	boolean indentLte( LineLocation other )
		{ _indentCompare other, lteCase }
	
	boolean indentGt( LineLocation other ) { other.indentLt this }
	boolean indentGte( LineLocation other ) { other.indentLte this }
	
	LineLocation plus( LineLocation other )
	{
		def newSize = mySize + other.mySize
		
		def otherCounts = other.counts
		if ( otherCounts.isEmpty() )
			return this
		
		if ( counts.isEmpty() )
			return other
		
		def otherCountsHead = otherCounts.head()
		
		def skipOver = 1
		if ( counts.size() % 2 == 0 )
		{
			if ( otherCountsHead != 0 )
				return new LineLocation(
					counts + otherCounts, newSize )
			
			skipOver = 2
		}
		
		def start = [] + counts
		start[ -1 ] += otherCounts[ skipOver - 1 ]
		return new LineLocation(
			start + otherCounts.subList(
				skipOver, otherCounts.size() ),
			newSize
		)
	}
	
	LineLocation plus( int spaces )
	{
		if ( spaces == 0 )
			return this
		
		if ( spaces < 0 )
			throw new IllegalArgumentException()
		
		def newSize = mySize + spaces
		
		if ( counts.size() % 2 == 0 )
			return new LineLocation( counts + [ spaces ], newSize )
		
		def newCounts = [] + counts
		newCounts[ -1 ] += spaces
		return new LineLocation( newCounts, newSize )
	}
	
	LineLocation plus( String between ) { plus of( between ) }
	
	LineLocation multiply( int occurrences )
	{
		if ( occurrences < 0 )
			throw new IllegalArgumentException()
		
		if ( occurrences == 1 || counts.isEmpty() )
			return this
		
		if ( occurrences == 0 )
			return new LineLocation( [], 0 )
		
		def newSize = mySize * occurrences
		
		def header = []
		List part
		if ( counts.size() % 2 == 0 )
		{
			if ( counts.head() != 0 )
				return new LineLocation(
					counts * occurrences, newSize )
			
			part = counts.tail()
			header.add 0
		}
		else
		{
			if ( counts.head() == 0 )
				return new LineLocation(
					[ 0 ] + counts.tail() * occurrences, newSize )
			
			part = [] + counts
		}
		
		def last = part.pop()
		
		if ( part.isEmpty() )
			return new LineLocation(
				header + [ last * occurrences ], newSize )
		
		def start = header + part
		part[ 0 ] += last
		return new LineLocation(
			start + part * (occurrences - 1) + [ last ], newSize )
	}
	
	String toString()
	{
		if ( counts.isEmpty() )
			return "0"
		
		if ( counts.size() == 1 )
			return "" + counts[ 0 ]
		
		def builder = new StringBuilder()
		
		def tabs = false
		for ( count in counts )
		{
			builder.append "+$count${tabs ? 't' : 's'}"
			tabs = !tabs
		}
		
		return builder.toString().
			substring( counts.head() == 0 ? 4 : 1 )
	}
	
	String toWhitespace()
	{
		def builder = new StringBuilder()
		
		def tabs = false
		for ( count in counts )
		{
			builder.append( (tabs ? '\t' : ' ') * count )
			tabs = !tabs
		}
		
		return builder.toString()
	}
	
	boolean isEmpty() { counts.isEmpty() }
	
	LineLocation prefix()
	{
		if ( mySize == 0 )
			throw new NoSuchElementException()
		
		if ( mySize == 1 )
			return new LineLocation( [], 0 )
		
		def result = [] + counts
		if ( (result[ -1 ] -= 1) == 0 )
			result.pop()
		
		return new LineLocation( result, mySize - 1 )
	}
	
	def anyPrefix( Closure body )
	{
		for ( def prefix = this; !prefix.isEmpty(); )
		{
			prefix = prefix.prefix()
			
			def result = body( prefix )
			if ( result )
				return result
		}
		
		return false
	}
	
	void eachPrefix( Closure body )
	{
		anyPrefix {
			
			body it
			return false
		}
	}
	
	List< LineLocation > prefixes()
	{
		def revResult = []
		eachPrefix revResult.&add
		return revResult
	}
	
	static LineLocation longestCommonPrefix(
		List< LineLocation > list )
	{
		if ( list.isEmpty() )
			throw new IllegalArgumentException()
		
		def copyList =
			list.collect { ((LineLocation)it).counts.reverse() }
		
		def resultCounts = []
		
		while ( !copyList.any { ((List)it).isEmpty() } )
		{
			def heads = ((List)copyList*.pop()).unique()
			
			if ( heads.size() != 1 )
			{
				def min = ((List)heads).min()
				if ( min != 0 )
					resultCounts.add min
				
				break
			}
			
			resultCounts.add heads.head()
		}
		
		return new LineLocation( resultCounts )
	}
	
	int size() { mySize }
	
	LineLocation prefix( int prefixSize )
	{
		if ( !(prefixSize in 0..mySize) )
			throw new IndexOutOfBoundsException()
		
		if ( prefixSize == mySize )
			return this
		else if ( prefixSize == 0 )
			return new LineLocation( [], 0 )
		
		def resultCounts = []
		
		def furtherSize = prefixSize
		for ( count in counts )
		{
			if ( furtherSize < count )
			{
				resultCounts.add furtherSize
				break
			}
			
			resultCounts.add count
			furtherSize -= count
		}
		
		return new LineLocation( resultCounts )
	}
}
