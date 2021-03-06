package net.bioclipse.ds.r;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;

import net.bioclipse.cdk.domain.ICDKMolecule;
import net.bioclipse.core.business.BioclipseException;
import net.bioclipse.ds.model.ITestResult;
import net.bioclipse.ds.model.result.DoubleResult;
import net.bioclipse.ds.model.result.PosNegIncMatch;

/**
 * A class building on R and Signatures for prediction with 
 * sparse signature representation
 * 
 * @author ola
 *
 */
public class SparseSignaturesRModelMatcher extends SignaturesRModelMatcher{


	@Override
	protected List<? extends ITestResult> doRunTest(ICDKMolecule cdkmol,
			IProgressMonitor monitor) {
		
        //Make room for results
        List<ITestResult> results=new ArrayList<ITestResult>();

        //Calculate the frequency of the signatures
        Map<String, Integer> moleculeSignatures;
		try {
			moleculeSignatures = signaturesMatcher.countSignatureFrequency(cdkmol);
		} catch (BioclipseException e) {
            return returnError( "Error generating signatures",e.getMessage());
		}

		//Set up the values for the sparse R-matrix
		Iterator<String> signaturesIter = signaturesMatcher.getSignatures().iterator();
		int no = 0;
		List<Integer> values = new ArrayList<Integer>();
		List<Integer> indices = new ArrayList<Integer>();
		while (signaturesIter.hasNext()){
			no++;
			String currentSignature = signaturesIter.next();
			
			//If we have match, store its index in the signatures array along with its frequency
			if (moleculeSignatures.containsKey(currentSignature)){
				values.add(moleculeSignatures.get(currentSignature));
				indices.add(signaturesMatcher.getSignatures().indexOf(currentSignature));
			}

		}
		
		//Formulate input to R in sparse format

//		> tmp <- new("matrix.csr", 
//		"ra" = c(1,2,3), 			   //VALUES
//		"ja"=as.integer(c(3,5,7)),     //COLUMN INDICES
//		"ia" = as.integer(c(1,4)),     //WHERE DO NEW LINES START IN THE VALUES ARRAY
//		dimension=as.integer(c(1,9)))  //TOTAL SIZE
//		
//		> as.matrix(tmp)
//	     [,1] [,2] [,3] [,4] [,5] [,6] [,7] [,8] [,9]
//	[1,]    0    0    1    0    2    0    3    0    0

		String rCommand= "new(\"matrix.csr\", " +
				"\"ra\" = c(" + values.toString().substring(1,values.toString().length()-1) + "), " + 
				"\"ja\" = as.integer(c(" + indices.toString().substring(1,indices.toString().length()-1) + ")), " +
				"\"ia\" = as.integer(c(1," + (values.size()+1) + "))," +
				"\"dimension\" = as.integer(c(1," + signaturesMatcher.getSignatures().size() + ")))";				;
		
		R.eval("tmp <- " + rCommand);
		
		//Do predictions in R
		String ret="";
		for (String rcmd : getPredictionString("tmp")){
			System.out.println(rcmd);
			ret = R.eval(rcmd);
	        System.out.println("R said: " + ret);
		}
		        
        //Parse result and create testresults
        double posProb = Double.parseDouble(ret.substring(4));
        
		int overallPrediction;
        if (posProb>=0.5)
        	overallPrediction = ITestResult.POSITIVE;
        else
        	overallPrediction = ITestResult.NEGATIVE;

		DoubleResult accuracy = new DoubleResult("Probability", posProb, overallPrediction);
		results.add(accuracy);

		//Try to predict important signatures
        String mostImportantRcmd = getMostImportantSignaturesCommand();
		ret = R.eval(mostImportantRcmd);
		if (ret.contains("An error occurred") || ret.startsWith("Error")){
			return results;
		}

		//Result should be on form: [1]  191  434 1683

		//Parse and create TestResults
		String[] parts = ret.trim().substring(4).split(" ");
		int pos = Integer.parseInt(parts[0]);
		int neg = Integer.parseInt(parts[1]);
		int zero = Integer.parseInt(parts[2]);
		
		String posSign = signaturesMatcher.getSignatures().get(pos);
		String negSign = signaturesMatcher.getSignatures().get(neg);
		String zeroSign = signaturesMatcher.getSignatures().get(zero);
        	
		PosNegIncMatch posMatch = new PosNegIncMatch("pos: " + posSign, overallPrediction);
		PosNegIncMatch negMatch = new PosNegIncMatch("neg: " + negSign, overallPrediction);
		PosNegIncMatch zeroMatch = new PosNegIncMatch("zero: " + zeroSign, overallPrediction);

		results.add(posMatch);
		results.add(negMatch);
		results.add(zeroMatch);

        return results;
		
	}

	
	/**
	 * Provide the R commands to deliver the prediction command to R
	 * from the input String (dense numerical vector with signature frequency).
	 */
	protected List<String> getPredictionString(String input){
		
		List<String> ret = new ArrayList<String>();
        ret.add("predicted <- predict(" + rmodel + "," + input + ", probability=T)");
        ret.add("attributes(predicted)$probabilities[1,1]\n");
		return ret;
	}

	protected String getMostImportantSignaturesCommand() {
		return "getMostImportantSignature.svm(" + rmodel + ", tmp)";
	}

}
