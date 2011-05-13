package net.bioclipse.ds.sdk.libsvm;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.sound.midi.SysexMessage;
import javax.vecmath.Tuple2d;
import javax.vecmath.Vector2d;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

import net.bioclipse.ds.sdk.Stopwatch;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IMolecule;
import org.openscience.cdk.io.iterator.IteratingMDLReader;
import org.openscience.cdk.modeling.builder3d.ModelBuilder3D;
import org.openscience.cdk.nonotify.NoNotificationChemObjectBuilder;


public class SignModel {

	//Fields with default values
	private int nrFolds = 5, startHeight = 0, endHeight = 3;
	private int cStart = 0, cEnd = 5, gammaStart = 3, gammaEnd = 10;
	private int noParallelJobs=1;

	//Fields without default values, set by constructor
	private String positiveActivity; 
	private String pathToSDFile;
	private boolean classification;
	private String activityProperty;
	private String outputDir;

	//Filled by logic
	private String out_svmModelName;
	private String out_signaturesFilename;
	private String out_trainFilename;
	private String out_optmimizationFilename;
	private String optimizationType;
	private List<Point> arrayOptimizationParams;
	private boolean trainFinal = true;
	private String parallelType;


	//Path to SDF
	//private static String pathToSDFile = "molsWithAct.sdf";

	//AMES
	//	private static String pathToSDFile = "bursi_nosalts_molsign.sdf";
	//	private static String ACTIVITY_PROPERTY = "Ames test categorisation";
	//	private static boolean classification = true;
	//	private static String positiveActivity = "mutagen"; 

	//AHR
	//	private static String pathToSDFile = "2796_nosalts_molsign.sdf";
	//	private static String ACTIVITY_PROPERTY = "c#Activity";
	//	private static boolean classification = true;
	//	private static String positiveActivity = "2"; 

	//private static String pathToSDFile = "chang.sdf";
	//private static String ACTIVITY_PROPERTY = "BIO";
	//private static boolean classification = false;

	//private static String pathToSDFile = "/home/lc/hERG_train.sdf";
	//private static String ACTIVITY_PROPERTY = "field_1";
	//private static boolean classification = false;

	//The property in the SDF to read, e. g. as activity



	public SignModel(String pathToSDFile, String activityProperty, String outputDir, boolean classification, String positiveActivity) {
		this();
		this.positiveActivity = positiveActivity;
		this.pathToSDFile = pathToSDFile;
		this.activityProperty = activityProperty;
		this.classification = classification;
		this.outputDir=outputDir;
	}

	public SignModel() {

		//Use current dir as default output
		if (outputDir==null || outputDir.isEmpty()){
			outputDir= System.getProperty("user.dir");;  //should not end with slash
		}

		updateOutputFilenames();

	}

	private void updateOutputFilenames() {

		out_svmModelName = outputDir + "/svmModel.txt";
		out_signaturesFilename = outputDir + "/signatures.txt";
		out_trainFilename = outputDir + "/train.txt";
		out_optmimizationFilename = outputDir + "/optimization.txt";

	}

	public OptimizationResult doOptimizationAndBuildModel() throws IOException{
		assertParameters();

		//Assert input file
		File sdfile = new File(pathToSDFile);
		if (!sdfile.canRead()){
			throw new IllegalArgumentException("Cannot read file: " + sdfile.getAbsolutePath());
		}

		File outFolder = new File(outputDir);
		if (!outFolder.exists()){
			if (!outFolder.mkdir())
				throw new IllegalArgumentException("Could not create directory: " + outFolder.getAbsolutePath());
		}
		outFolder.setWritable(true, false);

		File trainFile = new File(out_trainFilename);
		if (!trainFile.exists()){
			if (!trainFile.createNewFile())
				throw new IllegalArgumentException("Could not create file: " + trainFile.getAbsolutePath());
		}
		if (!trainFile.canWrite()){
			throw new IllegalArgumentException("Cannot write output file: " + trainFile.getAbsolutePath());
		}

		File signFile = new File(out_signaturesFilename);
		if (!signFile.exists()){
			if (!signFile.createNewFile())
				throw new IllegalArgumentException("Could not create file: " + signFile.getAbsolutePath());
		}
		if (!signFile.canWrite()){
			throw new IllegalArgumentException("Cannot write output file: " + signFile.getAbsolutePath());
		}

		File optFile = new File(out_optmimizationFilename);
		if (!optFile.exists()){
			if (!optFile.createNewFile())
				throw new IllegalArgumentException("Could not create file: " + optFile.getAbsolutePath());
		}
		if (!optFile.canWrite()){
			throw new IllegalArgumentException("Cannot write output file: " + optFile.getAbsolutePath());
		}

		//Assume we can write the SVM file for now


		//================================
		//== START BUILDING THE MODEL ==
		//================================

		BufferedReader br = new BufferedReader(new FileReader(sdfile));
		IteratingMDLReader reader = new IteratingMDLReader(br, NoNotificationChemObjectBuilder.getInstance());

		System.out.println("Building SVM model...");

		try {
			List<String> signatures = new ArrayList<String>(); // Contains signatures. We use the indexOf to retrieve the order of specific signatures in descriptor array.
			svm_problem svmProblem = new svm_problem();
			List<Double> activityList = new ArrayList<Double>();
			List<svm_node[]> descriptorList = new ArrayList<svm_node[]>();
			// Also, print the descriptors to a libsvm train formatted file.
			BufferedWriter trainWriter = new BufferedWriter(new FileWriter(trainFile));
			int cnt=1;
			while (reader.hasNext()){
				IMolecule mol = (IMolecule) reader.next();

				// Check the activity.
				String activity = (String) mol.getProperty(activityProperty);

				if (activity==null){
					System.out.println("Activity property: " + activityProperty + " not found in molecule: " + cnt);
					System.out.println("Exiting.");
					System.exit(1);
				}

				double activityValue = 0.0;
				if (classification){
					if (positiveActivity.equals(activity)){
						activityValue = 1.0;
					}
				}
				else { // Regression
					activityValue = Double.valueOf(activity);
				}
				activityList.add(activityValue);

				// Create the signatures for a molecule and add them to the signatures map
				Map<String, Double> moleculeSignatures = new HashMap<String, Double>(); // Contains the signatures for a molecule and the count. We store the count as a double although it is an integer. libsvm wants a double.
				for (int height = startHeight; height <= endHeight; height++){
					List<String> signs = SignTools.calculateSignatures(mol, height);
					Iterator<String> signsIter = signs.iterator();
					while (signsIter.hasNext()){
						String currentSignature = signsIter.next();
						//System.out.println(currentSignature);
						if (signatures.contains(currentSignature)){
							if (moleculeSignatures.containsKey(currentSignature)){
								moleculeSignatures.put(currentSignature, (Double)moleculeSignatures.get(currentSignature)+1.00);
							}
							else{
								moleculeSignatures.put(currentSignature, 1.0);
							}
						}
						else{
							signatures.add(currentSignature);
							if (moleculeSignatures.containsKey(currentSignature)){
								moleculeSignatures.put(currentSignature, (Double)moleculeSignatures.get(currentSignature)+1.00);
							}
							else{
								moleculeSignatures.put(currentSignature, 1.0);
							}
						}
					}
				}
				// Add the values of the current molecule's signatures as svm data.
				// Write the output as it reads in the sdf.
				trainWriter.write(activity);

				svm_node[] moleculeArray = new svm_node[moleculeSignatures.size()];
				Iterator<String> signaturesIter = signatures.iterator();
				int i = 0;
				while (signaturesIter.hasNext()){
					String currentSignature = signaturesIter.next();
					if (moleculeSignatures.containsKey(currentSignature)){
						moleculeArray[i] = new svm_node();
						moleculeArray[i].index = signatures.indexOf(currentSignature)+1; // libsvm assumes that the index starts at one.
						moleculeArray[i].value = (Double) moleculeSignatures.get(currentSignature);
						// The train file output.
						trainWriter.write(" " + moleculeArray[i].index + ":" + moleculeArray[i].value);
						i = i + 1;
					}
				}
				trainWriter.newLine();
				descriptorList.add(moleculeArray);

				//System.out.println("Molecule " + cnt + " (Activity=" + activity + "): " +  signs);
				cnt++;
			}
			trainWriter.close();

			// Write the signatures to a file, One per line.
			try {
				BufferedWriter signaturesWriter = new BufferedWriter(new FileWriter(signFile));
				Iterator<String> signaturesIter = signatures.iterator();
				while (signaturesIter.hasNext()){
					signaturesWriter.write(signaturesIter.next());
					signaturesWriter.newLine();
				}
				signaturesWriter.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}


			// Add values to the SVM problem.
			svmProblem.l = cnt - 1;
			svmProblem.x = new svm_node[svmProblem.l][];
			svmProblem.y = new double[svmProblem.l];
			for (int exampleNr = 0; exampleNr < svmProblem.l; exampleNr++){
				svmProblem.x[exampleNr] = descriptorList.get(exampleNr);
				svmProblem.y[exampleNr] = activityList.get(exampleNr);
			}

			// Do the grid search to find the best set of gamma for the RBF kernel and C for the cost.
			double optimumValue, optimumC = 1, optimumGamma = 0.01;
			svm_parameter svmParameter = new svm_parameter();
			svmParameter.kernel_type = svm_parameter.RBF;
			svmParameter.cache_size = 1000.0; // Cache size for training in MB.
			svmParameter.eps = 0.001;
			svmParameter.C = optimumC;
			svmParameter.gamma = optimumGamma;
			if (classification){
				svmParameter.svm_type = svm_parameter.C_SVC;
				optimumValue = 0.0;
			}
			else {
				svmParameter.svm_type = svm_parameter.EPSILON_SVR;
				optimumValue = 1000.0;
			}

			System.out.println("svm_check_parameter: " + svm.svm_check_parameter(svmProblem, svmParameter));

			OptimizationResult optRes=null;
			//Do grid search to obtain best parameters
			if ("grid".equals(optimizationType))
				optRes=gridSearchNew(svmParameter, svmProblem, optimumValue, optimumC, optimumGamma);
			else if ("array".equals(optimizationType))
				optRes=arraySearchNew(svmParameter, svmProblem, optimumValue, optimumC, optimumGamma);
			else
				throw new IllegalArgumentException("optimization type neither 'grid' nor 'array'");

			if (trainFinal){
				System.out.println("Training final model on parameters: c=" + optRes.getOptimumC() + "; gamma=" + optRes.getOptimumGamma() + "...");

				//We now have the optimum values, train a model for these parameters based on all data
				svmParameter.C = optRes.getOptimumC();
				svmParameter.gamma = optRes.getOptimumGamma();
				svm_model svmModel = new svm_model();
				svmModel = svm.svm_train(svmProblem, svmParameter);
				try {
					svm.svm_save_model(out_svmModelName , svmModel);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}		
			return optRes;
		} catch (InvalidSmilesException e) {
			e.printStackTrace();
		} catch (CDKException e) {
			e.printStackTrace();
		}
		return null;
	}


	private OptimizationResult arraySearchNew(svm_parameter svmParameter, svm_problem svmProblem, double optimumValue, double optimumC, double optimumGamma) throws IOException{

		BufferedWriter optwriter = new BufferedWriter(new FileWriter(out_optmimizationFilename));

		for (Point combo : arrayOptimizationParams){

			int cExponent=combo.x;
			int gammaExponent=combo.y;

			double[] target = new double[svmProblem.l];
			svmParameter.C = Math.pow(10.0,(cExponent/2));
			svmParameter.gamma = Math.pow(2.0, -gammaExponent);
			System.out.println("Estimating SVM for c:gamma = " + svmParameter.C+" : " + svmParameter.gamma);
			svm.svm_cross_validation(svmProblem, svmParameter, nrFolds, target);

			if (classification){
				int nrCorrect = 0;
				for (int i = 0; i < svmProblem.l; i++){
					if (target[i] == svmProblem.y[i]){ // Can you compare doubles like this in java or should it be abs(target-y) < eps?
						nrCorrect = nrCorrect + 1;
					}
				}
				double objectiveValue = 1.0*nrCorrect/svmProblem.l;
				if (objectiveValue > optimumValue){
					optimumValue = objectiveValue;
					optimumC = svmParameter.C;
					optimumGamma = svmParameter.gamma;
				}
				System.out.println("Objective Value:C:gamma: "+objectiveValue+":"+svmParameter.C+":"+svmParameter.gamma);
				optwriter.write("Objective Value:C:gamma: "+objectiveValue+":"+svmParameter.C+":"+svmParameter.gamma+"\n");
			}
			else{
				double sumSquareError = 0.0;
				for (int i = 0; i < svmProblem.l; i++){
					sumSquareError = sumSquareError + (target[i] - svmProblem.y[i]) * (target[i] - svmProblem.y[i]);
				}
				double objectiveValue = sumSquareError/svmProblem.l;
				if (objectiveValue < optimumValue){
					optimumValue = objectiveValue;
					optimumC = svmParameter.C;
					optimumGamma = svmParameter.gamma;
				}
				System.out.println("Objective Value:C:gamma: "+objectiveValue+":"+svmParameter.C+":"+svmParameter.gamma);
				optwriter.write("Objective Value:C:gamma: "+objectiveValue+":"+svmParameter.C+":"+svmParameter.gamma+"\n");
			}

		}

		System.out.println("ARRAY SEARCH FINISHED. Optimum Value:C:gamma: "+optimumValue+":"+optimumC+":"+optimumGamma);
		optwriter.write("ARRAY SEARCH FINISHED. Optimum Value:C:gamma: "+optimumValue+":"+optimumC+":"+optimumGamma+"\n");
		optwriter.close();
		return new OptimizationResult(optimumValue, optimumC, optimumGamma);

	}


	private OptimizationResult gridSearchNew(svm_parameter svmParameter, svm_problem svmProblem, double optimumValue, double optimumC, double optimumGamma) throws IOException{

		BufferedWriter optwriter = new BufferedWriter(new FileWriter(out_optmimizationFilename));

		for (int cExponent = cStart; cExponent <= cEnd; cExponent++){
			for (int gammaExponent = gammaStart; gammaExponent <= gammaEnd; gammaExponent++){

				double[] target = new double[svmProblem.l];
				svmParameter.C = Math.pow(10.0,(cExponent/2));
				svmParameter.gamma = Math.pow(2.0, -gammaExponent);
				System.out.println("Estimating SVM for c:gamma = " + svmParameter.C+" : " + svmParameter.gamma);
				svm.svm_cross_validation(svmProblem, svmParameter, nrFolds, target);

				if (classification){
					int nrCorrect = 0;
					for (int i = 0; i < svmProblem.l; i++){
						if (target[i] == svmProblem.y[i]){ // Can you compare doubles like this in java or should it be abs(target-y) < eps?
							nrCorrect = nrCorrect + 1;
						}
					}
					double objectiveValue = 1.0*nrCorrect/svmProblem.l;
					if (objectiveValue > optimumValue){
						optimumValue = objectiveValue;
						optimumC = svmParameter.C;
						optimumGamma = svmParameter.gamma;
					}
					System.out.println("Objective Value:C:gamma: "+objectiveValue+":"+svmParameter.C+":"+svmParameter.gamma);
					optwriter.write("Objective Value:C:gamma: "+objectiveValue+":"+svmParameter.C+":"+svmParameter.gamma+"\n");
				}
				else{
					double sumSquareError = 0.0;
					for (int i = 0; i < svmProblem.l; i++){
						sumSquareError = sumSquareError + (target[i] - svmProblem.y[i]) * (target[i] - svmProblem.y[i]);
					}
					double objectiveValue = sumSquareError/svmProblem.l;
					if (objectiveValue < optimumValue){
						optimumValue = objectiveValue;
						optimumC = svmParameter.C;
						optimumGamma = svmParameter.gamma;
					}
					System.out.println("Objective Value:C:gamma: "+objectiveValue+":"+svmParameter.C+":"+svmParameter.gamma);
					optwriter.write("Objective Value:C:gamma: "+objectiveValue+":"+svmParameter.C+":"+svmParameter.gamma+"\n");
				}

				//				double objectiveValue = doSVM_CV(svmParameter, svmProblem, cExponent, gammaExponent);
				//				if (objectiveValue > optimumValue){
				//					optimumValue = objectiveValue;
				//					optimumC = svmParameter.C;
				//					optimumGamma = svmParameter.gamma;
				//				}


			}
		}

		System.out.println("GRID SEARCH FINISHED. Optimum Value:C:gamma: "+optimumValue+":"+optimumC+":"+optimumGamma);
		optwriter.write("GRID SEARCH FINISHED. Optimum Value:C:gamma: "+optimumValue+":"+optimumC+":"+optimumGamma+"\n");
		return new OptimizationResult(optimumValue, optimumC, optimumGamma);

	}


	//	@Deprecated
	//	private void gridSearch(svm_parameter svmParameter, svm_problem svmProblem, double optimumValue, double optimumC, double optimumGamma){
	//		for (int cExponent = cStart; cExponent <= cEnd; cExponent++){
	//			for (int gammaExponent = gammaStart; gammaExponent <= gammaEnd; gammaExponent++){
	//				double[] target = new double[svmProblem.l];
	//				svmParameter.C = Math.pow(10.0,(cExponent/2));
	//				svmParameter.gamma = Math.pow(2.0, -gammaExponent);
	//				System.out.println("Predicting for c:gamma = " + svmParameter.C+" : " + svmParameter.gamma);
	//				svm.svm_cross_validation(svmProblem, svmParameter, nrFolds, target);
	//
	//				if (classification){
	//					int nrCorrect = 0;
	//					for (int i = 0; i < svmProblem.l; i++){
	//						if (target[i] == svmProblem.y[i]){ // Can you compare doubles like this in java or should it be abs(target-y) < eps?
	//							nrCorrect = nrCorrect + 1;
	//						}
	//					}
	//					double objectiveValue = 1.0*nrCorrect/svmProblem.l;
	//					if (objectiveValue > optimumValue){
	//						optimumValue = objectiveValue;
	//						optimumC = svmParameter.C;
	//						optimumGamma = svmParameter.gamma;
	//					}
	//					System.out.println("Objective Value:C:gamma: "+objectiveValue+":"+svmParameter.C+":"+svmParameter.gamma);
	//				}
	//				else{
	//					double sumSquareError = 0.0;
	//					for (int i = 0; i < svmProblem.l; i++){
	//						sumSquareError = sumSquareError + (target[i] - svmProblem.y[i]) * (target[i] - svmProblem.y[i]);
	//					}
	//					double objectiveValue = sumSquareError/svmProblem.l;
	//					if (objectiveValue < optimumValue){
	//						optimumValue = objectiveValue;
	//						optimumC = svmParameter.C;
	//						optimumGamma = svmParameter.gamma;
	//					}
	//					System.out.println("Objective Value:C:gamma: "+objectiveValue+":"+svmParameter.C+":"+svmParameter.gamma);
	//				}
	//			}
	//		}
	//		System.out.println("Optimum Value:C:gamma: "+optimumValue+":"+optimumC+":"+optimumGamma);
	//
	//	}

	/**
	 * Do a CV for a given c and gamma, return objectiveValue
	 */
	private double doSVM_CV(svm_parameter svmParameter, svm_problem svmProblem, int cExponent, int gammaExponent){

		double[] target = new double[svmProblem.l];
		svmParameter.C = Math.pow(10.0,(cExponent/2));
		svmParameter.gamma = Math.pow(2.0, -gammaExponent);
		System.out.println(svmParameter.C+" : " + svmParameter.gamma);
		svm.svm_cross_validation(svmProblem, svmParameter, nrFolds, target);

		double objectiveValue;

		if (classification){
			int nrCorrect = 0;
			for (int i = 0; i < svmProblem.l; i++){
				if (target[i] == svmProblem.y[i]){ // Can you compare doubles like this in java or should it be abs(target-y) < eps?
					nrCorrect = nrCorrect + 1;
				}
			}
			objectiveValue = 1.0*nrCorrect/svmProblem.l;
			//			System.out.println("Objective Value:C:gamma: "+objectiveValue+":"+svmParameter.C+":"+svmParameter.gamma);
		}
		else{
			double sumSquareError = 0.0;
			for (int i = 0; i < svmProblem.l; i++){
				sumSquareError = sumSquareError + (target[i] - svmProblem.y[i]) * (target[i] - svmProblem.y[i]);
			}
			objectiveValue = sumSquareError/svmProblem.l;
			//			System.out.println("Objective Value:C:gamma: "+objectiveValue+":"+svmParameter.C+":"+svmParameter.gamma);
		}

		//		System.out.println("Objective value:C:gamma: "+objectiveValue+":"+cExponent+":"+gammaExponent);
		return objectiveValue;
	}




	private void outputSettings() {

		System.out.println("Path to SDF: " + pathToSDFile);
		System.out.println("Output dir: " + outputDir);
		System.out.println("Activity property: " + activityProperty);
		System.out.println("Classification: " + classification);
		System.out.println("Positive activity: " + positiveActivity);

		System.out.println("\n == Output files ==");
		System.out.println("Output file for SVM model: " + out_svmModelName);
		System.out.println("Output file for Signatures: " + out_signaturesFilename);
		System.out.println("Output file for Training data: " + out_trainFilename);

		System.out.println("\n == Parameters for Model building ==");
		System.out.println("Nr CV folds: " + nrFolds);
		System.out.println("Signature height start: " + startHeight);
		System.out.println("Signature height end: " + endHeight);

		System.out.println("\n == Parameters for optimization ==");
		System.out.println("optimize: " + optimizationType);
		if ("array".equals(optimizationType)){
			System.out.println(arrayOptimizationParams.toString());
		}else if ("grid".equals(optimizationType)){
			System.out.println("cStart: " + cStart);
			System.out.println("cEnd: " + cEnd);
			System.out.println("gammaStart: " + gammaStart);
			System.out.println("gammaEnd: " + gammaEnd);
		}else{
			System.out.println("No optimization");
		}
		if (trainFinal)
			System.out.println("Train and save final model:  true");
		else
			System.out.println("Train and save final model:  false");

	}

	private void parseArgs(String[] args_in) {

		// Parse arguments
		ArrayList<String> args = new ArrayList<String>(Arrays.asList(args_in));
		Iterator<String> it = args.iterator();
		while (it.hasNext()) {
			String arg = it.next();

			if ("-h".equals(arg)) {
				printUsage();
				System.exit(0);
			}

			//input file
			if ("-i".equals(arg)) {
				pathToSDFile = it.next();
			}

			//output dir
			if ("-o".equals(arg)) {
				outputDir = it.next();
				updateOutputFilenames();
			}

			if ("-ap".equals(arg)) {
				activityProperty = it.next();
			}

			if ("-c".equals(arg)) {
				classification= Boolean.parseBoolean(it.next());
			}

			if ("-pa".equals(arg)) {
				positiveActivity = it.next();
			}

			//Optional args
			if ("-cstart".equals(arg)) {
				cStart = Integer.parseInt(it.next());
			}
			if ("-cend".equals(arg)) {
				cEnd = Integer.parseInt(it.next());
			}
			if ("-gammastart".equals(arg)) {
				gammaStart = Integer.parseInt(it.next());
			}
			if ("-gammaend".equals(arg)) {
				gammaEnd = Integer.parseInt(it.next());
			}

			if ("-folds".equals(arg)) {
				nrFolds = Integer.parseInt(it.next());
			}

			if ("-hstart".equals(arg)) {
				startHeight = Integer.parseInt(it.next());
			}
			if ("-hend".equals(arg)) {
				endHeight = Integer.parseInt(it.next());
			}

			if ("-pjobs".equals(arg)) {
				noParallelJobs = Integer.parseInt(it.next());
			}
			if ("-optimize".equals(arg)) {
				optimizationType = it.next();
			}
			if ("-optarray".equals(arg)) {
				arrayOptimizationParams=parseArrayOptimizationInput(it.next());
			}

			if ("-trainfinal".equals(arg)) {
				trainFinal= Boolean.parseBoolean(it.next());
			}

			if ("-ptype".equals(arg)) {
				parallelType= it.next();
			}


		}

	}



	private List<Point> parseArrayOptimizationInput(String arrayParams) {

		List<Point> parsedArray=new ArrayList<Point>();
		String[] entries = arrayParams.split(";");
		for (String entry : entries){
			String[] parts=entry.split(",");
			Point p = new Point(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]));
			parsedArray.add(p);
		}

		return parsedArray;
	}

	private void assertParameters() {

		//Assert all except file permissions, handled in buildModel

		if (pathToSDFile==null || pathToSDFile.isEmpty()) 
			throw new IllegalArgumentException("Input file is not defined");
		if (activityProperty==null || activityProperty.isEmpty()) 
			throw new IllegalArgumentException("Activity property is not defined");

		if (classification==true && (positiveActivity==null || positiveActivity.isEmpty())){
			throw new IllegalArgumentException("Missing parameter: POSITIVE ACTIVITY (required for classification)");
		}

	}


	private static void printUsage() {
		System.out.println();
		System.out.println("Signatures LibSVM model builder using gridsearch (supports classification and regression models)");
		System.out.println("If -optimize=grid, then perform grid search between [cstart to cend, gammastart-gammaend]");
		System.out.println("If -optimize=fixed, then optimize using paramarray (on form c1,gamma1;c2,gamma2");
		System.out.println("If -optimize=false, then predict using ");
		System.out.println();
		System.out.println("Usage: java -jar signbuild.jar <params>");
		System.out.println("-h                       display help");
		System.out.println("-i <input file>          path to SDFile [REQUIRED]");
		System.out.println("-o <output path>         path to where output files are written (DEFAULT=current dir)");
		System.out.println("-ap                      activity property in SDFile [REQUIRED]");
		System.out.println("-c                       if classification, set to 'true' (DEFAULT='false')");
		System.out.println("-pa                      positive activity (REQUIRED if classification model)");

		//For optimization
		System.out.println("-optimize                perform optimization [grid | array | false] (DEFAULT='grid')");
		System.out.println("-cstart     	         cStart for grid-search (DEFAULT=0)");
		System.out.println("-cend                    cEnd for grid-search (DEFAULT=5)");
		System.out.println("-gammastart              gammaStart for grid-search (DEFAULT=3)");
		System.out.println("-gammaend                gammaEnd for grid-search (DEFAULT=10)");
		System.out.println("-optarray                array of c,gamma combinations for array search (e.g. 4,6;4,7;4,8)");
		System.out.println("-trainfinal              if model on all data should be built and saved (DEFAULT='true')");

		//For model building
		System.out.println("-hstart                  signature start height (DEFAULT=0)");
		System.out.println("-hend                    signature end height (DEFAULT=3)");
		System.out.println("-folds                   nr folds in cross-validation (DEFAULT=5)");

		//For parallel setup
		System.out.println("-pjobs                   Generate script for jobs");
		System.out.println("-ptype                   Type of parallelization [threads | slurm | cloud] [DEFAULT=threads]");
//		System.out.println("-slurmTemplate           path to slurm template file. Project no should be $PROJECT_NO$.");
//		System.out.println("-slurmProject            path to slurm template file. Project no should be $PROJECT_NO$.");
		System.out.println();
	}


	/**
	 * The main class.
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {

		SignModel modelBuilder= new SignModel();
		modelBuilder.parseArgs(args);

		try{

			modelBuilder.assertParameters();
			modelBuilder.outputSettings();

			//If jobs > 1, return a script for execution
			if (modelBuilder.noParallelJobs>1){
				modelBuilder.generateParallelExecution();
				return;
			}

			Stopwatch stopwatch = new Stopwatch();
			stopwatch.start();

			//Optimize and build
			modelBuilder.doOptimizationAndBuildModel();

			stopwatch.stop();
			System.out.println("Model building finsihed. Elapsed time: " + stopwatch.toString());

		}catch (IllegalArgumentException e){
			System.err.println("Error: " + e.getMessage());
			System.err.flush();
			printUsage();
			System.exit(1);
		}

	}

	//	private void doArraySearchAndBuildModel() {
	//		//		throw new UnsupportedOperationException("doArraySearchAndBuildModel not implemented");
	//
	//		//Set up params for running the models
	//
	//		for (Point combo : arrayOptimizationParams){
	//
	//		}
	//
	//
	//	}

	/**
	 * Generate script, dividing up grid search over multiple model builders
	 */
	private void generateParallelExecution() {

		// Compute dimensions. Number of models: c * gamma
		int cSize=(cEnd-cStart+1);
		int gammaSize=(gammaEnd-gammaStart + 1);
		int totalSize=cSize*gammaSize;

		//Prune number of jobs if not enough models to fill
		if (totalSize<noParallelJobs){
			System.out.println("Number of models less than number oj jobs. Pruned number of jobs to: " + totalSize);
			noParallelJobs=totalSize;
		}

		int modelsPerJob = totalSize/noParallelJobs;

		System.out.println("Number of models to build: " + totalSize);
		System.out.println("Desired number of parrallel jobs: " + noParallelJobs);
		System.out.println("Number of models per job: " + modelsPerJob);

		//Set up list of jobs, each job is a list of points
		List<List<Point>> jobs = new ArrayList<List<Point>>();
		for (int i=0; i<noParallelJobs; i++){
			List<Point> job = new ArrayList<Point>();
			jobs.add(job);
		}

		int currjobix=0;
		int currjobSize=0;

		for (int c = cStart; c <= cEnd; c++){
			for (int g = gammaStart; g <= gammaEnd; g++){
				Point p = new Point(c, g);
				if (currjobix < (noParallelJobs-1)){    //Do not increase if we are in last job, this can be larger than the other
					if (currjobSize >= modelsPerJob){
						//Get next job
						currjobix++;
						currjobSize=0;
					}
				}
				List<Point> currjob = jobs.get(currjobix);
				currjob.add(p);
				currjobSize++;
			}
		}

		System.out.println("We have the following jobs:");

		int cnt=0;
		for (List<Point> job : jobs){
			System.out.println("Job " + cnt + ": " + job.toString());
			cnt++;
		}

		if ("slurm".equals(parallelType))
			generateSLURMfiles(jobs);
		else if ("threads".equals(parallelType))
			runInThreads(jobs);
		else if ("cloud".equals(parallelType))
			throw new UnsupportedOperationException("Parallel type CLOUD not implemented");

	}

	private void runInThreads(List<List<Point>> jobs) {

		final int cnt=1;
		Map<Thread, Worker> workmap = new HashMap<Thread, Worker>();

		for (final List<Point> job: jobs){
			//Start a thread

			Worker worker = new Worker(job){
				@Override
				public void run() {
					pathToSDFile.toString();
					SignModel mbuilder = new SignModel(pathToSDFile, activityProperty, outputDir, classification, positiveActivity);
					mbuilder.optimizationType="array";
					mbuilder.arrayOptimizationParams=job;
					mbuilder.outputDir=outputDir+cnt;
					mbuilder.nrFolds=nrFolds;

					try {
						//Set result
						setOptres(mbuilder.doOptimizationAndBuildModel());

					} catch (IOException e) {
						e.printStackTrace();
						return;
					}
				}
			};

			Thread thread = new Thread(worker, "job");
			thread.start();
			workmap.put(thread, worker);
		}
		
		System.out.println("Waiting for threads to finish...");

		for (Thread th : workmap.keySet()){
			try {
				th.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}	

		System.out.println("All threads finished");

		for (Thread th : workmap.keySet()){
			Worker worker = workmap.get(th);
			System.out.println("Thread " + th.getName() + " result = " + worker.getOptres().toString());
		}

		

	}

	private void generateSLURMfiles(List<List<Point>> jobs) {

		System.out.println("Generating parameter calls:");
		
		String params="-i " + pathToSDFile;
		params=params+" -o " + outputDir+"$NO$";
		params=params+" -ap \'" + activityProperty+"\'";
		params=params+" -c " + classification;
		if (positiveActivity!=null && !positiveActivity.isEmpty())
			params=params+" -pa " + positiveActivity;

		params=params+" -c " + classification;
		params=params+" -optarray " + "$ARRAY$";
		System.out.println(params);

		for (List<Point> job : jobs){

			String points="";
			for (Point p : job){
				points=points+p.x+","+p.y+";";
			}

			System.out.println(points.substring(0,points.length()-1));
		}

	}

}
