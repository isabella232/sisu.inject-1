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
package org.eclipse.sisu.space;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;

import org.eclipse.sisu.inject.Logs;

/**
 * Command-line utility that generates a qualified class index for a space-separated list of JARs.
 */
public final class SisuIndex
    extends AbstractSisuIndex
    implements QualifiedTypeListener
{
    // ----------------------------------------------------------------------
    // Constants
    // ----------------------------------------------------------------------

    public static final String NAMED = Named.class.getName();

    // ----------------------------------------------------------------------
    // Implementation fields
    // ----------------------------------------------------------------------

    private final File targetDirectory;

    // ----------------------------------------------------------------------
    // Constructors
    // ----------------------------------------------------------------------

    public SisuIndex( final File targetDirectory )
    {
        this.targetDirectory = targetDirectory;
    }

    // ----------------------------------------------------------------------
    // Public entry points
    // ----------------------------------------------------------------------

    public static void main( final String[] args )
    {
        final List<URL> indexPath = new ArrayList<URL>( args.length );
        for ( final String path : args )
        {
            try
            {
                indexPath.add( new File( path ).toURI().toURL() );
            }
            catch ( final MalformedURLException e )
            {
                Logs.warn( "Bad classpath element: {}", path, e );
            }
        }

        final ClassLoader parent = SisuIndex.class.getClassLoader();
        final URL[] urls = indexPath.toArray( new URL[indexPath.size()] );
        final ClassLoader loader = urls.length > 0 ? URLClassLoader.newInstance( urls, parent ) : parent;

        new SisuIndex( new File( "." ) ).index( new URLClassSpace( loader ) );
    }

    // ----------------------------------------------------------------------
    // Public methods
    // ----------------------------------------------------------------------

    public void index( final ClassSpace space )
    {
        try
        {
            new SpaceScanner( space ).accept( new QualifiedTypeVisitor( this ) );
        }
        finally
        {
            flushIndex();
        }
    }

    public void hear( final Class<?> qualifiedType, final Object source )
    {
        addClassToIndex( SisuIndex.NAMED, qualifiedType.getName() );
    }

    // ----------------------------------------------------------------------
    // Customized methods
    // ----------------------------------------------------------------------

    @Override
    protected void info( final String message )
    {
        System.out.println( "[INFO] " + message );
    }

    @Override
    protected void warn( final String message )
    {
        System.out.println( "[WARN] " + message );
    }

    @Override
    protected Reader getReader( final String path )
        throws IOException
    {
        return new InputStreamReader( new FileInputStream( new File( targetDirectory, path ) ), "UTF-8" );
    }

    @Override
    protected Writer getWriter( final String path )
        throws IOException
    {
        final File index = new File( targetDirectory, path );
        final File parent = index.getParentFile();
        if ( parent.isDirectory() || parent.mkdirs() )
        {
            return new OutputStreamWriter( new FileOutputStream( index ), "UTF-8" );
        }
        throw new IOException( "Error creating: " + parent );
    }
}
