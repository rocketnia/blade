// Sig.groovy
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


package com.rocketnia.blade.declare

import com.rocketnia.blade.*


// A sig is a list of values representing a path of namespaces.
class Sig extends RefMap {
	Blade getDerivative() { get "derivative" }
	Blade setDerivative( Blade val ) { set "derivative", val }
	Blade getParent() { get "parent" }
	Blade setParent( Blade val ) { set "parent", val }
	
	String toString()
	{
		def sig = this
		
		def revPath = [];
		
		while ( sig in Sig )
		{
			def sigSig = (Sig)sig
			revPath.add sigSig.getDerivative()
			sig = sigSig.getParent()
		}
		
		revPath.add sig
		
		def path = revPath.reverse()
		
		return "Sig( ${path.head()} . ${path.tail().join( ' ' )} )"
	}
}