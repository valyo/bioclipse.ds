/*******************************************************************************
 * Copyright (c) 2009 Ola Spjuth.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Ola Spjuth - initial API and implementation
 ******************************************************************************/
package net.bioclipse.ds.business;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.IProgressMonitor;

import net.bioclipse.core.business.BioclipseException;
import net.bioclipse.core.domain.IMolecule;
import net.bioclipse.ds.model.IDSTest;
import net.bioclipse.jobs.IReturner;
import net.bioclipse.managers.business.IBioclipseManager;

/**
 * A Bioclipse Manager Decision Support
 * 
 * @author ola
 */
public class DSManager implements IBioclipseManager {

    private static final Logger logger =Logger.getLogger( DSManager.class );

    private volatile List<IDSTest> tests; 
    
    /**
     * Defines the Bioclipse namespace for DS.
     * Appears in the scripting language as the namespace/prefix
     */
    public String getManagerName() {
        return "ds";
    }

    public List<String> getTests() throws BioclipseException{

        if (tests==null)
            tests = TestHelper.readTestsFromEP();
        if (tests==null)
            throw new BioclipseException("No existing tests available.");
        
        List<String> testIDS=new ArrayList<String>();
        for (IDSTest test : tests){
            testIDS.add( test.getId());
        }

        return testIDS;
    }


    public IDSTest getTest( String testID ) throws BioclipseException {

        if (testID==null)
            throw new BioclipseException(
                          "Test: " + testID + " must not be null." );
        
        if (tests==null)
            tests = TestHelper.readTestsFromEP();
        if (tests==null)
            throw new BioclipseException("No existing tests available.");

        for (IDSTest test : tests){
            if (testID.equals( test.getId() ))
                return test;
        }

        logger.debug("Test: " + testID + " could not be found.");
        throw new BioclipseException(
                      "Test: " + testID + " could not be found." );
    }
 
    
    public void runTest( String testID, IMolecule mol, 
                             IReturner returner, IProgressMonitor monitor) 
                             throws BioclipseException{

        IDSTest test = getTest( testID );
        returner.completeReturn( test.runWarningTest( mol, monitor));
    }

}
