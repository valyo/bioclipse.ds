package net.bioclipse.ds.business;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;

import net.bioclipse.cdk.domain.ICDKMolecule;
import net.bioclipse.cdk.ui.sdfeditor.business.IPropertyCalculator;
import net.bioclipse.core.util.LogUtils;
import net.bioclipse.ds.Activator;
import net.bioclipse.ds.model.TestRun;
import net.bioclipse.ds.model.report.ReportHelper;


public class DSConsensusCalculator implements IPropertyCalculator<TestRun>{

    private static final Logger logger = 
                                  Logger.getLogger(DSConsensusCalculator.class);

    
    public TestRun calculate( ICDKMolecule molecule ) {

        List<Integer> classifications=new ArrayList<Integer>();
        for(IPropertyCalculator<TestRun> calculator:getCalculators()) {
            TestRun tr = calculator.calculate( molecule );
            classifications.add( new Integer(tr.getConsensusStatus()));
        }
        
        TestRun consrun=new TestRun();
        consrun.setClassification( ConsensusCalculator.calculate( classifications ) );
        
        return consrun;
    }

    public String getPropertyName() {
        return "ds.consensus";
    }

    public TestRun parse( String value ) {
        TestRun consrun=new TestRun();
        consrun.setClassification( ReportHelper.stringToStatus( value ) );
        return consrun;
    }

    public String toString( Object value ) {
        return (String)value;
    }

    protected Collection<IPropertyCalculator<TestRun>> getCalculators() {
        
        List<IPropertyCalculator<TestRun>> calculators=new 
                                      ArrayList<IPropertyCalculator<TestRun>>();

        //Read EP for all calculators and select the ones in DS
        IExtensionRegistry registry = Platform.getExtensionRegistry();
        IConfigurationElement[] elements = registry.getConfigurationElementsFor(
                                       "net.bioclipse.cdk.propertyCalculator" );

        for(IConfigurationElement element:elements) {

            try {
                IPropertyCalculator<?> calculator = (IPropertyCalculator<?>)
                                   element.createExecutableExtension( "class" );

                //Only DS ones
                if ( calculator instanceof BaseDSPropertyCalculator ) {
                    BaseDSPropertyCalculator bda = (BaseDSPropertyCalculator) calculator;
                    calculators.add( bda );
                    
                }
                
            } catch ( CoreException e ) {
                LogUtils.handleException( e, logger, Activator.PLUGIN_ID);
            }
        }
        
        return calculators;
    }

}
