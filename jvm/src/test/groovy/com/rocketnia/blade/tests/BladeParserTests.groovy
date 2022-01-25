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

import static com.rocketnia.blade.parse.DocumentSelection.from as sel
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse

import org.junit.jupiter.api.Test

import com.rocketnia.blade.parse.*


class BladeParserTests
{
	@Test
	void testBrackets()
	{
		assertEquals BladeParser.
			parseBrackets( "\n[]a\nbc\t[d[\ne]f\n]ghi[]\n" ),
			[
				sel( 0 ).to( 1 ),
				[ sel( 1, 1 ) + 0 ],
				sel( 1, 2 ).to( 2, 2, 1 ),
				[
					sel( 2, 2, 1, 1 ) + 1,
					[ sel( 2, 2, 1, 3 ).to( 3, 1 ) ],
					sel( 3, 2 ).to( 4 )
				],
				sel( 4, 1 ) + 3,
				[ sel( 4, 5 ) + 0 ],
				sel( 4, 6 ) + 0
			]
	}
	
	@Test
	void testParagraphedBrackets()
	{
		assertEquals BladeParser.parseParagraphedBrackets( '''

 D'oh! This isn't in a paragraph, since it's indented. Instead, it's
an error.

[This paragraph] [has [tree-like structure]] using [brackets].
This part of the paragraph is ignored, and so is "using".

This begins a new, non-[] paragraph, which is ignored.

  [This is a continuation of the same non-[] paragraph.]

[ This paragraph yields an error.

] This paragraph is an independent non-[] paragraph, not a
continuation of the above paragraph.

[]] This paragraph yields an error as well.

''' ),
		[
			new ErrorSelection(
					"ParseException(Every line before the first"
				 + " unindented line must be empty.)",
				sel( 2 ).to( 3, 9 )
			),
			[ sel( 5, 1 ) + 14 ],
			[
				sel( 5, 18 ) + 4,
				[ sel( 5, 23 ) + 19 ],
				sel( 5, 43 ) + 0
			],
			[ sel( 5, 52 ) + 8 ],
			new ErrorSelection(
					"ParseException(There were open-heavy [ ]"
				 + " brackets.)",
				sel( 12 ) + 33
			),
			new ErrorSelection(
					"ParseException(There were close-heavy [ ]"
				 + " brackets.)",
				sel( 17 ) + 43
			)
		]
	}
	
	void testEquals()
	{
		def make = { new ErrorSelection(
			[ "non-string message" ], sel( 45 ) + it ) }
		
		assertEquals make( 0 ), make( 0 )
		assertFalse make( 0 ) == make( 1 )
	}
	
	void testProject()
	{
		def sBrackets = [
			[
				sel( 2, 1 ).to( 4, 2 ),
				[ sel( 4, 3 ) + 9 ],
				sel( 4, 13 ) + 11
			],
			[ sel( 5, 5 ) + 4 ]
		]
		
		def seBrackets = [ [
			sel( 0, 1 ) + 15,
			[ sel( 0, 17 ) + 4 ],
			sel( 0, 22 ) + 1
		] ]
		
		def parseProj = { file ->
			
			def parsedProject = BladeParser.
				parseProject( BladeTests.getResourceFile( file ) )
			
			def result = [:]
			parsedProject.each { String path, Set declarations ->
				
				result[ path ] = declarations.collect {
					
					if ( !(it in BracketView) )
						return it
					
					it = (BracketView)it
					return [ it.path, it.brackets ]
				} as Set
			}
			
			return result
		}
		
		def sFile = "something.blade"
		def seFile = "subdir/somethingelse.blade"
		
		assertEquals parseProj( "/parseproject/$sFile" ),
			[ (""): sBrackets.collect { [ "", it ] } as Set ]
		
		assertEquals parseProj( "/parseproject/$seFile" ),
			[ (""): seBrackets.collect { [ "", it ] } as Set ]
		
		assertEquals parseProj( "/parseproject" ), [
			(sFile): sBrackets.collect { [ sFile, it ] } as Set,
			(seFile): seBrackets.collect { [ seFile, it ] } as Set
		]
	}
}
