/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stuart McCulloch (Sonatype, Inc.) - initial API and implementation
 *******************************************************************************/
package org.eclipse.sisu.wire;

import java.util.Arrays;

import org.eclipse.sisu.inject.DefaultBeanLocator;
import org.eclipse.sisu.wire.WireModule.Strategy;

import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;

/**
 * Child {@link WireModule} that avoids wiring dependencies that already exist in a parent {@link Injector}.
 */
public final class ChildWireModule
    implements Module
{
    // ----------------------------------------------------------------------
    // Implementation fields
    // ----------------------------------------------------------------------

    private final Injector parent;

    private final Iterable<Module> modules;

    private Strategy strategy = Strategy.DEFAULT;

    // ----------------------------------------------------------------------
    // Constructors
    // ----------------------------------------------------------------------

    public ChildWireModule( final Injector parent, final Module... modules )
    {
        this( parent, Arrays.asList( modules ) );
    }

    public ChildWireModule( final Injector parent, final Iterable<Module> modules )
    {
        this.modules = modules;
        this.parent = parent;
    }

    // ----------------------------------------------------------------------
    // Public methods
    // ----------------------------------------------------------------------

    public Module with( final Strategy _strategy )
    {
        strategy = _strategy;
        return this;
    }

    public void configure( final Binder binder )
    {
        // make sure we're added to locator as early as possible
        binder.requestStaticInjection( DefaultBeanLocator.class );

        // ignore any inherited bindings/dependencies
        final ElementAnalyzer analyzer = new ElementAnalyzer( binder );
        for ( Injector i = parent; i != null; i = i.getParent() )
        {
            analyzer.ignoreKeys( i.getAllBindings().keySet() );
        }

        // rest of this is the same as WireModule.configure...
        for ( final Element e : Elements.getElements( modules ) )
        {
            e.acceptVisitor( analyzer );
        }
        analyzer.apply( strategy.wiring( binder ) );
    }
}
