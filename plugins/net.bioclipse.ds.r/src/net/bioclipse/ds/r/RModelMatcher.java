package net.bioclipse.ds.r;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import libsvm.svm_node;

import org.eclipse.core.runtime.IProgressMonitor;

import net.bioclipse.cdk.business.ICDKManager;
import net.bioclipse.cdk.domain.ICDKMolecule;
import net.bioclipse.core.business.BioclipseException;
import net.bioclipse.core.util.FileUtil;
import net.bioclipse.core.util.LogUtils;
import net.bioclipse.ds.matcher.BaseSignaturesMatcher;
import net.bioclipse.ds.model.AbstractDSTest;
import net.bioclipse.ds.model.DSException;
import net.bioclipse.ds.model.IDSTest;
import net.bioclipse.ds.model.ITestResult;
import net.bioclipse.ds.model.result.DoubleResult;
import net.bioclipse.ds.model.result.PosNegIncMatch;
import net.bioclipse.ds.model.result.SimpleResult;
import net.bioclipse.ds.signatures.business.ISignaturesManager;
import net.bioclipse.r.business.Activator;
import net.bioclipse.r.business.IRBusinessManager;

/**
 * An abstract class for using R models for predictions
 * 
 * @author ola
 *
 */
public abstract class RModelMatcher extends AbstractDSTest implements IDSTest{
	
	private static final String R_DATA_PARAMETER = "rdata";
	private static final String R_TRAINED_MODEL = "trainedModel";
	private static final String R_REQUIRED_PACKAGES = "requiredPackages";

	protected IRBusinessManager R;

	//We need to ensure that '.' is always decimal separator in all locales
    DecimalFormat formatter=new DecimalFormat("0.000");
	protected String rmodel;
	
    @Override
    public List<String> getRequiredParameters() {
        List<String> ret=new ArrayList<String>();
        ret.add( R_DATA_PARAMETER );
        ret.add( R_REQUIRED_PACKAGES );
        ret.add( R_TRAINED_MODEL );
        return ret;
    }


	@Override
	public void initialize(IProgressMonitor monitor) throws DSException {
		super.initialize(monitor);

        R = Activator.getDefault().getJavaRBusinessManager();
        
        monitor.beginTask("Initializing " + getName(), 4);
        monitor.worked(1);
        
		//Load R with rdata file
        monitor.subTask("Loading R data into R");
        
        String rdataparams = getParameters().get( R_DATA_PARAMETER );
        String[] rmodelFiles = rdataparams.split(",");

        for (String rm : rmodelFiles){
        	
        	//Get file for param
            if (rm.isEmpty())
                throw new DSException("Error initializing file parameter " + rm + " for model "
                		+ getName() + " due to empty parameter ");

            try {
				String path = FileUtil.getFilePath(rm, getPluginID());
        	
            String loadModelResult = R.eval("load(\"" + path + "\")");

			if (loadModelResult.startsWith("Error"))
                throw new DSException("Error initializing test " + getName() 
                		+ ": Loading data file " + rmodelFiles 
                		+ " FAILED.");
		
            } catch (Exception e) {
                throw new DSException("Error initializing file parameter " + rm + " for model "
                		+ getName() + " due to path wrong: " + rm);
			}

        }

        monitor.worked(1);
        monitor.subTask("Asserting R model");

        //Assert R models to ensure loading of rdata is ok
        rmodel=getParameters().get( R_TRAINED_MODEL );
        if ( rmodel != null && rmodel.length()>0){
        	String rres = R.eval("is(" + rmodel + ")");
        	if (rres.startsWith("Error"))
        		throw new DSException("Error initializing test " + getName() 
        				+ ": Asserting R object " + rmodel 
        				+ " FAILED after loading R data file " + rmodelFiles);
        }

        monitor.worked(1);
        monitor.subTask("Loading prediction libraries into R");

        //Assert R models to ensure loading of model data is ok
        String reqPackages=getParameters().get( R_REQUIRED_PACKAGES);
        if ( reqPackages != null && reqPackages.length()>0){
            String[] rpkgs = reqPackages.split(",");
            for (String rpkg : rpkgs){
                String ret=R.eval("library("+ rpkg + ")");
            	if (ret.startsWith("Error"))
                    throw new DSException("Error initializing test " + getName() 
                    		+ ": Required R package " + rpkg 
                    		+ " could not be loaded.");
            }
        }
        
        monitor.done();
        
	}
	

}
