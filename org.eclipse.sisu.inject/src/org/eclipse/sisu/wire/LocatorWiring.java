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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Qualifier;

import org.eclipse.sisu.BeanEntry;
import org.eclipse.sisu.inject.BeanLocator;
import org.eclipse.sisu.inject.HiddenBinding;
import org.eclipse.sisu.inject.Implicit;
import org.eclipse.sisu.inject.TypeArguments;

import com.google.inject.Binder;
import com.google.inject.ImplementedBy;
import com.google.inject.Key;
import com.google.inject.ProvidedBy;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.spi.InjectionPoint;

/**
 * Adds {@link BeanLocator}-backed bindings for non-local bean dependencies.
 */
@SuppressWarnings( { "unchecked", "rawtypes" } )
final class LocatorWiring
    implements Wiring
{
    // ----------------------------------------------------------------------
    // Constants
    // ----------------------------------------------------------------------

    private static final HiddenBinding HIDDEN_SOURCE = new HiddenBinding()
    {
        @Override
        public String toString()
        {
            return LocatorWiring.class.getName();
        }
    };

    // ----------------------------------------------------------------------
    // Implementation fields
    // ----------------------------------------------------------------------

    private final Binder binder;

    // ----------------------------------------------------------------------
    // Constructors
    // ----------------------------------------------------------------------

    LocatorWiring( final Binder binder )
    {
        this.binder = binder.withSource( HIDDEN_SOURCE );
    }

    // ----------------------------------------------------------------------
    // Public methods
    // ----------------------------------------------------------------------

    public boolean wire( final Key<?> key )
    {
        final Class<?> clazz = key.getTypeLiteral().getRawType();
        if ( Map.class == clazz )
        {
            bindMapImport( key );
        }
        else if ( List.class == clazz || Collection.class == clazz || Iterable.class == clazz )
        {
            bindListImport( key );
        }
        else if ( Set.class == clazz )
        {
            bindSetImport( key );
        }
        else
        {
            bindBeanImport( key );
        }
        return true;
    }

    // ----------------------------------------------------------------------
    // Implementation methods
    // ----------------------------------------------------------------------

    /**
     * Adds an imported {@link Map} binding; uses the generic type arguments to determine the search details.
     * 
     * @param key The dependency key
     */
    private void bindMapImport( final Key<?> key )
    {
        final TypeLiteral<?>[] args = TypeArguments.get( key.getTypeLiteral() );
        if ( 2 == args.length && null == key.getAnnotation() )
        {
            final Class qualifierType = args[0].getRawType();
            if ( String.class == qualifierType )
            {
                binder.bind( key ).toProvider( new NamedBeanMapProvider( args[1] ) );
            }
            else if ( qualifierType.isAnnotationPresent( Qualifier.class ) )
            {
                binder.bind( key ).toProvider( new BeanMapProvider( Key.get( args[1], qualifierType ) ) );
            }
        }
    }

    /**
     * Adds an imported {@link List} binding; uses the generic type arguments to determine the search details.
     * 
     * @param key The dependency key
     */
    @SuppressWarnings( "deprecation" )
    private void bindListImport( final Key<?> key )
    {
        final TypeLiteral<?>[] args = TypeArguments.get( key.getTypeLiteral() );
        if ( 1 == args.length && null == key.getAnnotation() )
        {
            final TypeLiteral<?> elementType = args[0];
            if ( BeanEntry.class == elementType.getRawType()
                || org.sonatype.inject.BeanEntry.class == elementType.getRawType() )
            {
                final Provider beanEntriesProvider = getBeanEntriesProvider( elementType );
                if ( null != beanEntriesProvider )
                {
                    binder.bind( key ).toProvider( beanEntriesProvider );
                }
            }
            else
            {
                binder.bind( key ).toProvider( new BeanListProvider( Key.get( elementType ) ) );
            }
        }
    }

    @SuppressWarnings( "deprecation" )
    private static Provider getBeanEntriesProvider( final TypeLiteral<?> elementType )
    {
        final TypeLiteral<?>[] args = TypeArguments.get( elementType );
        if ( 2 == args.length )
        {
            final Class qualifierType = args[0].getRawType();
            if ( qualifierType.isAnnotationPresent( Qualifier.class ) )
            {
                final Key beanKey = Key.get( args[1], qualifierType );
                if ( BeanEntry.class == elementType.getRawType() )
                {
                    return new BeanEntryProvider( beanKey );
                }
                return org.eclipse.sisu.inject.Legacy.beanEntriesProvider( beanKey );
            }
        }
        return null;
    }

    /**
     * Adds an imported {@link Set} binding; uses the generic type arguments to determine the search details.
     * 
     * @param key The dependency key
     */
    private void bindSetImport( final Key<?> key )
    {
        final TypeLiteral<?>[] args = TypeArguments.get( key.getTypeLiteral() );
        if ( 1 == args.length && null == key.getAnnotation() )
        {
            binder.bind( key ).toProvider( new BeanSetProvider( Key.get( args[0] ) ) );
        }
    }

    /**
     * Adds an imported bean binding; uses the type and {@link Qualifier} annotation to determine the search details.
     * 
     * @param key The dependency key
     */
    private <T> void bindBeanImport( final Key<T> key )
    {
        final Annotation qualifier = key.getAnnotation();
        if ( qualifier instanceof Named )
        {
            if ( ( (Named) qualifier ).value().length() == 0 )
            {
                // special case for wildcard @Named dependencies: match any @Named bean regardless of actual name
                binder.bind( key ).toProvider( new BeanProvider<T>( Key.get( key.getTypeLiteral(), Named.class ) ) );
            }
            else
            {
                binder.bind( key ).toProvider( new PlaceholderBeanProvider<T>( key ) );
            }
        }
        else
        {
            binder.bind( key ).toProvider( new BeanProvider<T>( key ) );

            // capture original implicit binding?
            if ( null == key.getAnnotationType() )
            {
                bindImplicitType( key.getTypeLiteral() );
            }
        }
    }

    /**
     * Captures the original implicit binding that would have been used by Guice; see the {@link BeanLocator} code.
     * 
     * @param type The implicit type
     */
    private void bindImplicitType( final TypeLiteral type )
    {
        try
        {
            final Class<?> clazz = type.getRawType();
            if ( TypeArguments.isConcrete( clazz ) )
            {
                final Member ctor = InjectionPoint.forConstructorOf( type ).getMember();
                binder.bind( Key.get( clazz, Implicit.class ) ).toConstructor( (Constructor) ctor );
            }
            else
            {
                final ImplementedBy implementedBy = clazz.getAnnotation( ImplementedBy.class );
                if ( null != implementedBy )
                {
                    binder.bind( Key.get( clazz, Implicit.class ) ).to( (Class) implementedBy.value() );
                }
                else
                {
                    final ProvidedBy providedBy = clazz.getAnnotation( ProvidedBy.class );
                    if ( null != providedBy )
                    {
                        binder.bind( Key.get( clazz, Implicit.class ) ).toProvider( (Class) providedBy.value() );
                    }
                }
            }
        }
        catch ( final RuntimeException e ) // NOPMD
        {
            // can safely ignore
        }
        catch ( final LinkageError e ) // NOPMD
        {
            // can safely ignore
        }
    }
}
