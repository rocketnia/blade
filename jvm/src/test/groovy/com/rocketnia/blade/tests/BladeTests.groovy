// BladeTests.groovy
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


package com.rocketnia.blade.tests


class BladeTests extends GroovyTestCase
{
	void testTestResources()
	{
		assertEquals getResourceLines( "/resource.txt" ), [
			"Hello!",
			"",
			"I",
			"am",
			"a",
			"resource.",
			"",
			"Have a nice day."
		]
	}
	
	void testTestResourceTraversal()
	{
		def bladeProjectUrl =
			getClass().getResource( "/bladeproject" )
		
		assertEquals bladeProjectUrl.getProtocol(), "file"
		def filesFound = 0
		traverse( bladeProjectUrl.getPath() as File ) { filesFound++ }
		assertEquals filesFound, 3
		
		assertEquals getResourceFiles( "/bladeproject" ).size(), 3
	}
	
	static List getResourceLines( String filename )
	{
		def stream = getClass().getResourceAsStream( filename )
		
		def lines = []
		new InputStreamReader( stream, "UTF-8" ).
			eachLine { lines.add it }
		return lines
	}
	
	static void traverse( File file, Closure body )
	{
		// We don't really care about the traversal order, but we're
		// making a point to avoid JVM recursion so that we don't get
		// a StackOverflowError.
		
		def toGo = [ file ]
		
		while ( !toGo.isEmpty() )
		{
			def thisFile = (File)toGo.pop()
			
			if ( thisFile.isFile() )
				body thisFile
			else for ( child in thisFile.listFiles() )
				toGo.add child
		}
	}
	
	static List< File > getResourceFiles( String directory )
	{
		def result = []
		traverse(
			getClass().getResource( directory ).getPath() as File,
			result.&add
		)
		return result
	}
}
