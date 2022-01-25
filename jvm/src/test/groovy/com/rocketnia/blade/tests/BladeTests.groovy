// BladeTests.groovy
//
// Copyright 2010, 2022 Rocketnia
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

import static org.junit.Assert.assertEquals

import org.junit.jupiter.api.Test

import com.rocketnia.blade.*


class BladeTests
{
	@Test
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
	
	@Test
	void testTestResourceTraversal()
	{
		def parseProjectUrl =
			getClass().getResource( "/parseproject" )
		
		assertEquals parseProjectUrl.getProtocol(), "file"
		def filesFound = 0
		Misc.eachNonDir( new File( parseProjectUrl.toURI() ) )
			{ filesFound++ }
		assertEquals filesFound, 4
		
		assertEquals getResourceFiles( "/parseproject" ).size(), 4
	}
	
	static List getResourceLines( String filename )
	{
		def stream = BladeTests.class.getResourceAsStream( filename )
		
		def lines = []
		new InputStreamReader( stream, "UTF-8" ).
			eachLine { lines.add it }
		return lines
	}
	
	static Set< File > getResourceFiles( String directory )
		{ Misc.getNonDirs getResourceFile( directory ) }
	
	static File getResourceFile( String filename )
	{
		def url = BladeTests.class.getResource( filename )
		
		if ( null.is( url ) )
			return null
		
		return new File( url.toURI() ).getCanonicalFile()
	}
}
