//
// FloatCoercer
//
// Copyright (C) 2007 Brian Stevens.
//
//  This file is part of the Fauxjo Library.
//
//  The Fauxjo Library is free software; you can redistribute it and/or
//  modify it under the terms of the GNU Lesser General Public
//  License as published by the Free Software Foundation; either
//  version 2.1 of the License, or (at your option) any later version.
//
//  The Fauxjo Library is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
//  Lesser General Public License for more details.
//
//  You should have received a copy of the GNU Lesser General Public
//  License along with the Fauxjo Library; if not, write to the Free
//  Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
//  02111-1307 USA.
//

package net.fauxjo.coercer;

import net.fauxjo.*;

public class FloatCoercer implements TypeCoercer<Float>
{
    // ============================================================
    // Methods
    // ============================================================

    // ----------
    // public
    // ----------

    public Object coerce( Float value, Class<?> destClass )
        throws FauxjoException
    {
        if ( destClass.equals( Double.class ) )
        {
            return value.doubleValue();
        }

        throw new FauxjoException( "The FloatCoercer does not know how to convert to type " +
            destClass );
    }

}

