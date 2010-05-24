// Intern.groovy
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


package com.rocketnia.blade.weak

import java.lang.ref.WeakReference
import java.util.WeakHashMap


class Interner< T extends Internable >
{
	protected WeakHashMap< ?, WeakReference< T > > contents
	
	Interner()
		{ contents = new WeakHashMap< ?, WeakReference< T > >() }
	
	T getAt( T model )
	{
		if ( null.is( model ) )
			throw new NullPointerException()
		
		def key = model.getInternKey()
		
		T result = contents[ key ]?.get()
		if ( !null.is( result ) )
			return result
		
		contents[ key ] = new WeakReference( model )
		return model
	}
}

// The intern key of an Internable is the value that actually provides
// an equals() implementation (as well as a hashCode() one). If
// uninterned versions of the Internable type are never used, then the
// equals() method on the Internable itself should really do nothing
// but reference comparison, the Object.equals() behavior, since
// that's sufficient. However, something still needs to define the
// equals() behavior so that the value can be interned in the first
// place, and that's the intern key's job.
//
// The intern key should be an object strongly referred to only by the
// Internable object (if that). Otherwise an Interner which has
// interned the Internable may end up keeping a (blanked-out) map
// entry for it long after the entry isn't needed.
//
interface Internable { def getInternKey() }
