/* Copyright (c) 2009  Arvid Berg <arvid.berg@farmbio.uu.se>
 *               2009  Egon Willighagen <egonw@user.sf.net>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contact: http://www.bioclipse.net/
 ******************************************************************************/
package net.bioclipse.ds.sdk.pdewizard;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import net.bioclipse.ds.sdk.Activator;
import net.bioclipse.ds.sdk.cdk.CDKHelper;
import net.bioclipse.ds.sdk.qsar.QSARbuilder;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.pde.core.plugin.IPluginBase;
import org.eclipse.pde.core.plugin.IPluginElement;
import org.eclipse.pde.core.plugin.IPluginExtension;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.core.plugin.IPluginModelFactory;
import org.eclipse.pde.core.plugin.IPluginReference;
import org.eclipse.pde.internal.core.bundle.BundlePluginBase;
import org.eclipse.pde.internal.core.ibundle.IBundle;
import org.eclipse.pde.ui.IFieldData;
import org.eclipse.pde.ui.templates.ComboChoiceOption;
import org.eclipse.pde.ui.templates.OptionTemplateSection;
import org.eclipse.pde.ui.templates.PluginReference;
import org.eclipse.pde.ui.templates.TemplateOption;
import org.openscience.cdk.aromaticity.CDKHueckelAromaticityDetector;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.fingerprint.Fingerprinter;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IMolecule;
import org.openscience.cdk.io.SDFWriter;
import org.openscience.cdk.io.iterator.IteratingMDLReader;
import org.openscience.cdk.nonotify.NoNotificationChemObjectBuilder;
import org.openscience.cdk.signature.MoleculeSignature;
import org.openscience.cdk.tools.CDKHydrogenAdder;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

public class DSTemplate extends OptionTemplateSection {

	//SDF properties written, to be read by DS predictors
	private static final Object CDK_FP_PROPERTY = "CDK Fingerprint";
	private static final Object MOLECULAR_SIGNATURE_PROPERTY = "Molecular Signature";

	//Endpoints
	private static final String KEY_NEW_ENDPOINT_ID = "newEndpointID";
	private static final String KEY_NEW_ENDPOINT_NAME = "newEndpointName";
	private static final String KEY_NEW_ENDPOINT_DESCRIPTION = "newEndpointDescription";

	//Data file
	static final String KEY_DATA_FILE = "sdFile";

	//models
	public static final String KEY_SELECTED_MODELS = "selectedModels";

	//exact matches
	private static final String EXACT_RESPONSE_PROPERTY_NAME = "EXACT_RESPONSE_PROPERTY_NAME";
	private static final String EXACT_RESPONSE_POSITIVE_VALUE = "EXACT_RESPONSE_POSITIVE_VALUE";
	private static final String EXACT_RESPONSE_NEGATIVE_VALUE = "EXACT_RESPONSE_NEGATIVE_VALUE";

	//nearest
	private static final String NEAREST_RESPONSE_NEGATIVE_VALUE = "NEAREST_RESPONSE_NEGATIVE_VALUE";
	private static final String NEAREST_RESPONSE_POSITIVE_VALUE = "NEAREST_RESPONSE_POSITIVE_VALUE";
	private static final String NEAREST_RESPONSE_PROPERTY_NAME = "NEAREST_RESPONSE_PROPERTY_NAME";
	public static final String NEAREST_TANIMOTO = "nn_tanimoto";

	//qsar
	private static final String QSAR_RESPONSE_PROPERTY_NAME = "responseProperty";
	private static final String QSAR_RESPONSE_POSITIVE_VALUE = "qsar_positive_value";
	public static final String QSAR_LIBSVM_GRID = "GRID SEARCH";
	public static final String QSAR_SIGNATURE_HEIGHT = "signagure_height";
	private static final String QSAR_MODEL_TYPE = "QSAR_MODEL_TYPE";
    private final String SIGNATURES_MIN_HEIGHT="signatures.min.height";
    private final String SIGNATURES_MAX_HEIGHT="signatures.max.height";

	
	//Generated files
	private static final String FILE_DATA_EXACT_MATCH = "data.exact.sdf";
	private static final String FILE_DATA_NEAREST_MATCH = "data.nearest.sdf";

	private static final String FILE_DATA_QSAR_SIGNATURES = "qsar.signatures";
	private static final String FILE_DATA_QSAR_MODEL = "qsar.model";
	private static final String FILE_DATA_QSAR_TRAIN = "qsar.train";

	//And things we need here..
	private Wizard wizard;
	private Map<Integer,List<TemplateOption>> optionControl;
	
	private int noMols;
	private List<String> availProps;
	

	public Map<Integer, List<TemplateOption>> getOptionControl() {
		return optionControl;
	}
	public Wizard getWizard() {
		return wizard;
	}
	public void setWizard(Wizard wizard) {
		this.wizard = wizard;
	}

	public DSTemplate() {
		super();
		setPageCount(5);
		createOptions();
	}
	@Override
	public void addPages(Wizard wizard) {
		
		this.wizard=wizard;
		
		WizardPage page = createPage(0);
		page.setTitle("Endpoint and data");
		page.setDescription("Create endpoints and select a data file (SD-file)");
		page.setPageComplete(false);
		wizard.addPage(page);
		
		WizardPage page2 = createPage(1);
		page2.setTitle("Model selection");
		page2.setDescription("Select the models to build");
		page2.setPageComplete(false);
		wizard.addPage(page2);

		//
		WizardPage page3 = createPage(2);
		page3.setTitle("Exact matches");
		page3.setDescription("Set parameters for exact matches");
		wizard.addPage(page3);

		WizardPage page4 = createPage(3);
		page4.setTitle("Near neighbours");
		page4.setDescription("Set parameters for near neighbours");
		wizard.addPage(page4);

		WizardPage page5 = createPage(4);
		page5.setTitle("QSAR model");
		page5.setDescription("Set parameters for the QSAR model");
		wizard.addPage(page5);

//		WizardPage page6 = createPage(5);
//		page6.setTitle("Structural alerts");
//		page6.setDescription("Set parameters for the QSAR model");
//		wizard.addPage(page6);

		markPagesAdded();
	}
	
	
	

	private void createOptions() {

		optionControl = new HashMap<Integer, List<TemplateOption>>();
		
		//Page 1
		addOption(KEY_NEW_ENDPOINT_ID, "Endpoint ID", null, 0);
		addOption(KEY_NEW_ENDPOINT_NAME, "Endpoint Name", null, 0);
		addOption(KEY_NEW_ENDPOINT_DESCRIPTION, "Endpoint Description", null, 0);

		FileOption option = new FileOption(this, KEY_DATA_FILE, "Data file");
		registerOption(option, null, 0);

		//Page 2
		ModelsOption modelOption = new ModelsOption(this, KEY_SELECTED_MODELS, "Select models");
		registerOption(modelOption, null, 1);

		
		//Page 3 - EXACT
		DSComboChoiceOption o = new DSComboChoiceOption(this, EXACT_RESPONSE_PROPERTY_NAME, "Response property", new String[0][0]);
		registerOption(o, null, 2);
		o.setRequired(false);
		validateOptions(o);
		TemplateOption o2 = addOption(EXACT_RESPONSE_POSITIVE_VALUE, "Positive value", null, 2);
		validateOptions(o2);
		o2.setRequired(false);
		TemplateOption o3 = addOption(EXACT_RESPONSE_NEGATIVE_VALUE, "Negative value", null, 2);
		validateOptions(o3);
		o3.setRequired(false);
		
		List<TemplateOption> pagetemplates= new ArrayList<TemplateOption>();
		pagetemplates.add(o);
		pagetemplates.add(o2);
		pagetemplates.add(o3);
		optionControl.put(2,pagetemplates);


		//Page 4 - NEAREST
		DSComboChoiceOption p4o1 = new DSComboChoiceOption(this, NEAREST_RESPONSE_PROPERTY_NAME, "Response property", new String[0][0]);
		registerOption(p4o1, null, 3);
		p4o1.setRequired(false);
		validateOptions(p4o1);

		TemplateOption p4o2 = addOption(NEAREST_RESPONSE_POSITIVE_VALUE, "Positive value", null, 3);
		p4o2.setRequired(false);
		validateOptions(p4o2);
		TemplateOption p4o3 = addOption(NEAREST_RESPONSE_NEGATIVE_VALUE, "Negative value", null, 3);
		p4o3.setRequired(false);
		validateOptions(p4o3);
		TemplateOption p4o4 = addOption(NEAREST_TANIMOTO, "Tanimoto (0-1)", null, 3);
		validateOptions(p4o4);
		p4o4.setRequired(false);
		
		List<TemplateOption> page4Templates= new ArrayList<TemplateOption>();
		page4Templates.add(p4o1);
		page4Templates.add(p4o2);
		page4Templates.add(p4o3);
		page4Templates.add(p4o4);
		optionControl.put(3,page4Templates);

		
		//Page 5 - QSAR
		DSComboChoiceOption p5o = new DSComboChoiceOption(this, QSAR_RESPONSE_PROPERTY_NAME, "Response property", new String[0][0]);
		registerOption(p5o, null, 4);
		p5o.setRequired(false);
		validateOptions(p5o);
		
		String[][] qsarType = new String[2][2];
		qsarType[0][0] = "Regression model";
		qsarType[0][1] = "Regression model";
		qsarType[1][0] = "Classification model";
		qsarType[1][1] = "Classification model";
		ComboChoiceOption p5o2 = new ComboChoiceOption(this, QSAR_MODEL_TYPE, "Model type", qsarType);
		registerOption(p5o2, "Classification", 4);
		p5o2.setRequired(false);
		
		TemplateOption p5o3 = addOption(SIGNATURES_MIN_HEIGHT, "Signatures min height", null, 4);
		p5o3.setRequired(false);
		p5o3.setValue("1");
		validateOptions(p5o3);

		TemplateOption p5o4 = addOption(SIGNATURES_MAX_HEIGHT, "Signatures max height", null, 4);
		p5o4.setRequired(false);
		validateOptions(p5o4);
		p5o4.setValue("3");

		TemplateOption p5o5 = addOption(QSAR_RESPONSE_POSITIVE_VALUE, "Positive property value", null, 4);
		p5o5.setRequired(false);
		validateOptions(p5o5);
		p5o5.setValue("mutagen");

		

		List<TemplateOption> page5Templates= new ArrayList<TemplateOption>();
		page5Templates.add(p5o);
		page5Templates.add(p5o2);
		page5Templates.add(p5o3);
		page5Templates.add(p5o4);
		page5Templates.add(p5o5);
		optionControl.put(4,page5Templates);

		
		
		//Page 6
//		addOption("wee", "Structural alerts option", null, 5);
		
	}

	@Override
	protected void initializeFields(IFieldData data) {
		String packageName = getFormattedPackageName(data.getId());
		initFields(packageName);
	}
	public void initializeFields(IPluginModelBase model) {
		String packageName = getFormattedPackageName(model.getPluginBase().getId());
		initFields(packageName);
	}

	private void initFields(String packageName) {
			if(packageName == null || packageName.length()<=0) return;

			initializeOption(KEY_NEW_ENDPOINT_ID, packageName + ".endpoint");
			initializeOption(KEY_NEW_ENDPOINT_NAME, getStringOption(KEY_PLUGIN_NAME));
			initializeOption(KEY_PACKAGE_NAME, packageName);
			
//			initializeOption(KEY_RESPONSE_PROPERTY_NAME, r);
	}
	@Override
	protected URL getInstallURL() {
		return Activator.getDefault().getBundle().getEntry("/");
	}

	@Override
	protected String getTemplateDirectory() {
		return super.getTemplateDirectory();
	}

	@Override
	public boolean isDependentOnParentWizard() {
		return true;
	}
	@Override
	public String getSectionId() {
		return "dstemplate";
	}

	@Override
	protected ResourceBundle getPluginResourceBundle() {
		return Platform.getResourceBundle(Activator.getDefault().getBundle());
	}

	@Override
	protected void updateModel(IProgressMonitor monitor) throws CoreException {
		
		System.out.println("WHAT WE GOT:");

		System.out.println(getStringOption(KEY_NEW_ENDPOINT_ID));
		System.out.println(getStringOption(KEY_NEW_ENDPOINT_NAME));
		System.out.println(getStringOption(KEY_NEW_ENDPOINT_DESCRIPTION));

		System.out.println(getOptionByName(KEY_DATA_FILE).getValue());

		System.out.println(getOptionByName(KEY_SELECTED_MODELS).getValue());

		System.out.println(getOptionByName(EXACT_RESPONSE_PROPERTY_NAME).getValue());
		System.out.println(getStringOption(EXACT_RESPONSE_POSITIVE_VALUE));		
		System.out.println(getStringOption(EXACT_RESPONSE_NEGATIVE_VALUE));		

		System.out.println(getStringOption(NEAREST_RESPONSE_NEGATIVE_VALUE));		
		System.out.println(getStringOption(NEAREST_RESPONSE_POSITIVE_VALUE));
		System.out.println(getOptionByName(NEAREST_RESPONSE_PROPERTY_NAME).getValue());
		System.out.println(getStringOption(NEAREST_TANIMOTO));		
		
		System.out.println(getOptionByName(QSAR_RESPONSE_PROPERTY_NAME).getValue());
		System.out.println(getOptionByName(QSAR_MODEL_TYPE).getValue());
		System.out.println(getStringOption("qsar grid: "+ QSAR_LIBSVM_GRID));		//problem
		System.out.println(getStringOption("qsar height: "+ QSAR_SIGNATURE_HEIGHT));		//problem
		
		System.out.println("Now creating plugin...");
		
		
		IPluginBase plugin = model.getPluginBase();
		IPluginModelFactory factory = model.getPluginFactory();

		IPluginExtension extension = createExtension(
					"net.bioclipse.decisionsupport", true);

		//The endpoint
		IPluginElement element = factory.createElement(extension);
		element.setName("endpoint");
		element.setAttribute("id", getStringOption(KEY_NEW_ENDPOINT_ID));
		element.setAttribute("name", getStringOption(KEY_NEW_ENDPOINT_NAME));
		element.setAttribute("description", getStringOption(KEY_NEW_ENDPOINT_DESCRIPTION));
		extension.add(element);

		//===============
		//The exact model
		//===============
		@SuppressWarnings("unchecked")
		List<String> selectedModels=(List<String>) getOptionByName(KEY_SELECTED_MODELS).getValue();
		if (selectedModels.contains("net.bioclipse.ds.matcher.SDFPosNegExactMatchSignatures")){
			element = factory.createElement(extension);
			element.setName("test");
			element.setAttribute("endpoint", getStringOption(KEY_NEW_ENDPOINT_ID));
			element.setAttribute("id", getStringOption(KEY_NEW_ENDPOINT_ID) + ".exact.signatures");
			element.setAttribute("name", getStringOption(KEY_NEW_ENDPOINT_NAME) + " exact matches");
			element.setAttribute("class", "net.bioclipse.ds.matcher.SDFPosNegExactMatchSignatures");
			extension.add(element);

			IPluginElement child = factory.createElement(element);
			child.setName("resource");
			child.setAttribute("name", "file");
			child.setAttribute("path", "data/" + FILE_DATA_EXACT_MATCH);
			element.add(child);

			child = factory.createElement(element);
			child.setName("parameter");
			child.setAttribute("name", "responseProperty");
			if (getOptionByName(EXACT_RESPONSE_PROPERTY_NAME)!=null)
				if (getOptionByName(EXACT_RESPONSE_PROPERTY_NAME).getValue()!=null)
					child.setAttribute("value", getOptionByName(EXACT_RESPONSE_PROPERTY_NAME).getValue().toString());
			element.add(child);

			child = factory.createElement(element);
			child.setName("parameter");
			child.setAttribute("name", "positiveValue");
			child.setAttribute("value", getStringOption(EXACT_RESPONSE_POSITIVE_VALUE));
			element.add(child);

			child = factory.createElement(element);
			child.setName("parameter");
			child.setAttribute("name", "negativeValue");
			child.setAttribute("value", getStringOption(EXACT_RESPONSE_NEGATIVE_VALUE));
			element.add(child);
			
		}

		if (selectedModels.contains("net.bioclipse.ds.matcher.SDFPosNegNearestMatchFP")){
			
			element = factory.createElement(extension);
			element.setName("test");
			element.setAttribute("endpoint", getStringOption(KEY_NEW_ENDPOINT_ID));
			element.setAttribute("id", getStringOption(KEY_NEW_ENDPOINT_ID) + ".nearest.signatures");
			element.setAttribute("name", getStringOption(KEY_NEW_ENDPOINT_NAME) + " nearest matches");
			element.setAttribute("class", "net.bioclipse.ds.matcher.SDFPosNegNearestMatchFP");
			extension.add(element);

			
			IPluginElement child = factory.createElement(element);
			child.setName("resource");
			child.setAttribute("name", "file");
			child.setAttribute("path", "data/" + FILE_DATA_NEAREST_MATCH);
			element.add(child);

			child = factory.createElement(element);
			child.setName("parameter");
			child.setAttribute("name", "responseProperty");
			if (getOptionByName(NEAREST_RESPONSE_PROPERTY_NAME)!=null)
				if (getOptionByName(NEAREST_RESPONSE_PROPERTY_NAME).getValue()!=null)
					child.setAttribute("value", getOptionByName(NEAREST_RESPONSE_PROPERTY_NAME).getValue().toString());
			element.add(child);

			child = factory.createElement(element);
			child.setName("parameter");
			child.setAttribute("name", "positiveValue");
			child.setAttribute("value", getStringOption(NEAREST_RESPONSE_POSITIVE_VALUE));
			element.add(child);

			child = factory.createElement(element);
			child.setName("parameter");
			child.setAttribute("name", "negativeValue");
			child.setAttribute("value", getStringOption(NEAREST_RESPONSE_NEGATIVE_VALUE));
			element.add(child);

			child = factory.createElement(element);
			child.setName("parameter");
			child.setAttribute("name", "distance.tanimoto");
			child.setAttribute("value", getStringOption(NEAREST_TANIMOTO));
			element.add(child);

		}

		if (selectedModels.contains("qsar.libsvm.atomsign")){
			
//			 <test
//	            id="ames.signatures"
//	            name="Ames Signature Significance"
//	            class="net.bioclipse.ds.libsvm.Signatures2ClassesPredictionTest"
//	            endpoint="net.bioclipse.ds.mutagenicity"
//	            propertycalculator="Ames Signature Significance">
//	            <resource name="modelfile" path="models/ames.model" />
//	            <resource name="rangefile" path="models/ames.range" />
//	            <resource name="signaturesfile" path="models/ames.signatures" />
//	      </test>
			
//		     <test
//	            id="cpdb.signatures"
//	            name="CPDB Signature Significance"
//	            class="net.bioclipse.ds.libsvm.SignaturesRegressionTest"
//	            endpoint="net.bioclipse.ds.carcinogenicity"
//	            informative="true"
//	            propertycalculator="CPDB Signature Significance">
//	            <resource name="modelfile" path="models/cpdb.train.model" />
//	            <resource name="rangefile" path="models/cpdb.train.range" />
//	            <resource name="signaturesfile" path="models/cpdb.train.signatures" />
//	            <parameter name="lowPercentile" value="0.1" />
//	            <parameter name="highPercentile" value="1.365681" />
//	      </test>


			element = factory.createElement(extension);
			element.setName("test");
			element.setAttribute("endpoint", getStringOption(KEY_NEW_ENDPOINT_ID));
			element.setAttribute("id", getStringOption(KEY_NEW_ENDPOINT_ID) + ".qsar.sign.libsvm");
			element.setAttribute("name", getStringOption(KEY_NEW_ENDPOINT_NAME) + " Signature Significance");
			element.setAttribute("class", "net.bioclipse.ds.libsvm.SignaturesLibSVMPrediction");
			
			//Regression model has extra parameters
			if (getOptionByName(QSAR_MODEL_TYPE).getValue().equals("Regression model")){
				
				IPluginElement child3 = factory.createElement(element);
				child3.setName("parameter");
				child3.setAttribute("name", "lowPercentile");
				child3.setAttribute("value", "" + 0);
				element.add(child3);

				IPluginElement child4 = factory.createElement(element);
				child4.setName("parameter");
				child4.setAttribute("name", "highPercentile");
				child4.setAttribute("value", "" + 1.4);
				element.add(child4);
				
			}
			
			extension.add(element);



			//Parameters

			IPluginElement child = factory.createElement(element);
			child.setName("resource");
			child.setAttribute("name", "file");
			child.setAttribute("path", "data/" + FILE_DATA_NEAREST_MATCH);
			element.add(child);

			child = factory.createElement(element);
			child.setName("parameter");
			child.setAttribute("name", "responseProperty");
			if (getOptionByName(QSAR_RESPONSE_PROPERTY_NAME)!=null)
				if (getOptionByName(QSAR_RESPONSE_PROPERTY_NAME).getValue()!=null)
					child.setAttribute("value", getOptionByName(QSAR_RESPONSE_PROPERTY_NAME).getValue().toString());
			element.add(child);

			child = factory.createElement(element);
			child.setName("parameter");
			child.setAttribute("name", "positiveValue");
			child.setAttribute("value", getStringOption(QSAR_RESPONSE_POSITIVE_VALUE));
			element.add(child);
			
			child = factory.createElement(element);
			child.setName("resource");
			child.setAttribute("name", "modelfile");
			child.setAttribute("path", "models/" + FILE_DATA_QSAR_MODEL);
			element.add(child);

			IPluginElement child2 = factory.createElement(element);
			child2.setName("resource");
			child2.setAttribute("name", "signaturesfile");
			child2.setAttribute("path", "models/" + FILE_DATA_QSAR_SIGNATURES);
			element.add(child2);

			child2 = factory.createElement(element);
			child2.setName("resource");
			child2.setAttribute("name", "trainFile");
			child2.setAttribute("path", "models/" + FILE_DATA_QSAR_TRAIN);
			element.add(child2);
			
			IPluginElement child5 = factory.createElement(element);
			child5.setName("parameter");
			child5.setAttribute("name", SIGNATURES_MIN_HEIGHT);
			child5.setAttribute("value", getStringOption(SIGNATURES_MIN_HEIGHT));
			element.add(child5);

			IPluginElement child6 = factory.createElement(element);
			child6.setName("parameter");
			child6.setAttribute("name", SIGNATURES_MAX_HEIGHT);
			child6.setAttribute("value", getStringOption(SIGNATURES_MAX_HEIGHT));
			element.add(child6);

		}
	

		plugin.add(extension);

	}


	TemplateOption getOptionByName(String optionName) {
		
		for (int page = 0; page<getPageCount(); page++){
			for (TemplateOption opt : getOptions(page)){
				if (optionName.equals(opt.getName()))
					return opt;
			}
		}

		return null;
		
	}

	
	public String[] getNewFiles() {
		
		//TODO: UPDATE HERE SO BUILD.PROPS is ok
		return new String[]{"data/","models/"};
	}

	public String getUsedExtensionPoint() {
		return "net.bioclipse.decisionsupport";
	}

	public IPluginReference[] getDependencies(String schemaVersion) {
		return createDependencies(
				 "org.eclipse.ui",
				 "org.eclipse.core.runtime",
				 "net.bioclipse.core",
				 "net.bioclipse.ds",
				 "net.bioclipse.cdk.ui.sdfeditor",
				 "net.bioclipse.cdk.business",
				 "net.bioclipse.ds.common",
				 "net.bioclipse.ds.ui",
				 "net.bioclipse.ds.libsvm"
		);
	}

	private IPluginReference[] createDependencies(String... plugins) {
		List<IPluginReference> deps = new ArrayList<IPluginReference>();
		for(String dep:plugins) {
			deps.add(new PluginReference(dep,null,0));
		}
		return deps.toArray(new IPluginReference[deps.size()]);
	}

	protected String getFormattedPackageName(String id) {
		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < id.length(); i++) {
			char ch = id.charAt(i);
			if (buffer.length() == 0) {
				if (Character.isJavaIdentifierStart(ch))
					buffer.append(Character.toLowerCase(ch));
			} else {
				if (Character.isJavaIdentifierPart(ch) || ch == '.')
					buffer.append(ch);
			}
		}
		return buffer.toString().toLowerCase(Locale.ENGLISH);
	}

    @Override
    public void execute(IProject project, IPluginModelBase model,
            IProgressMonitor monitor) throws CoreException {
    	
    	monitor.beginTask("Preparing data and models...", 50);
    	
        IPluginBase pluginBase = model.createPluginBase();
        if (pluginBase instanceof BundlePluginBase) {
            IBundle bundle = ((BundlePluginBase) pluginBase).getBundle();
            bundle.setHeader("Import-Package", "org.apache.log4j");
            model.createPluginBase().getId();
            String packageName = getFormattedPackageName(
                model.getPluginBase().getId()
            );
        }
        
        //Create required directories
        IFolder dataFolder = project.getFolder("data");
        if (!dataFolder.exists())
        	dataFolder.create(true, false, monitor);
        IFolder modelsFolder = project.getFolder("models");
        if (!modelsFolder.exists())
        	modelsFolder.create(true, false, monitor);

        //==============
        //PROCESS FILES
        //==============
		@SuppressWarnings("unchecked")
		List<String> selectedModels=(List<String>) getOptionByName(KEY_SELECTED_MODELS).getValue();

		String datafile = getOptionByName(KEY_DATA_FILE).getValue().toString();

		if (selectedModels.contains("net.bioclipse.ds.matcher.SDFPosNegExactMatchSignatures")){
			
			monitor.subTask("Setting up data file for exact matches");
			
			//Create the data file and fill with preprocessed contents
			IFile dstFile = dataFolder.getFile(new Path(FILE_DATA_EXACT_MATCH));
			preProcessAndCopyFile(datafile, dstFile, new SubProgressMonitor(monitor, 5));
		}

		if (selectedModels.contains("net.bioclipse.ds.matcher.SDFPosNegNearestMatchFP")){
			
			monitor.subTask("Setting up data file for nearest matches");

			//Create the data file and fill with preprocessed contents
			IFile dstFile = dataFolder.getFile(new Path(FILE_DATA_NEAREST_MATCH));
			preProcessAndCopyFile(datafile, dstFile, new SubProgressMonitor(monitor, 5));
		}

		

		//If we have a qsar model...
		if (selectedModels.contains("qsar.libsvm.atomsign")){

			monitor.subTask("Building QSAR model");

			boolean classification=true;
			String qsar_resp = (String) getOptionByName(QSAR_RESPONSE_PROPERTY_NAME).getValue();
			String qsar_resp_val = null;
			
			
			if (getOptionByName(QSAR_MODEL_TYPE).getValue().equals("Regression model")){
				classification=false;
			}else{
				qsar_resp_val = (String) getOptionByName(QSAR_RESPONSE_POSITIVE_VALUE).getValue();
			}
			
			QSARbuilder builder = new QSARbuilder(classification);
			builder.setStartHeight(Integer.parseInt((String)(getOptionByName(SIGNATURES_MIN_HEIGHT).getValue())));
			builder.setEndHeight(Integer.parseInt((String)(getOptionByName(SIGNATURES_MAX_HEIGHT).getValue())));
//			builder.setEndHeight(Integer.parseInt(SIGNATURES_MAX_HEIGHT));
			
			System.out.println(getOptionByName(QSAR_MODEL_TYPE).getValue());
			System.out.println(getStringOption("qsar grid: "+ QSAR_LIBSVM_GRID));		//problem
			System.out.println(getStringOption("qsar height: "+ QSAR_SIGNATURE_HEIGHT));		//problem


			try {
				
				File tmpModelFile = File.createTempFile("qsar.model_", ".txt");
				File tmpSignFile = File.createTempFile("qsar.sign_", ".txt");
				File tmpTrainFile = File.createTempFile("qsar.train_", ".txt");
				
				builder.buildModel(datafile, qsar_resp, qsar_resp_val,
						tmpSignFile.getAbsolutePath(), 
						tmpModelFile.getAbsolutePath(), 
						tmpTrainFile.getAbsolutePath(), 
						new SubProgressMonitor(monitor, 40));
				
				//Copy model files in place
				IFile dstFile = modelsFolder.getFile(new Path(FILE_DATA_QSAR_MODEL));
				copyFileToProject(tmpModelFile, dstFile, new SubProgressMonitor(monitor, 1));
				dstFile = modelsFolder.getFile(new Path(FILE_DATA_QSAR_SIGNATURES));
				copyFileToProject(tmpSignFile, dstFile, new SubProgressMonitor(monitor, 1));
				dstFile = modelsFolder.getFile(new Path(FILE_DATA_QSAR_TRAIN));
				copyFileToProject(tmpTrainFile, dstFile, new SubProgressMonitor(monitor, 1));
				
				//QSAR also needs the SDF file so it can be used to look up near neighbours for hits
				dstFile = dataFolder.getFile(new Path(FILE_DATA_NEAREST_MATCH));
				preProcessAndCopyFile(datafile, dstFile, new SubProgressMonitor(monitor, 5));
				


			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			

		}

		monitor.done();
        
        super.execute(project, model, monitor);
    }

    
	private void copyFileToProject(File sourceFile, IFile dstFile, IProgressMonitor monitor) throws CoreException {
		
		try {
			InputStream stream = new BufferedInputStream(new FileInputStream(sourceFile));
			if (dstFile.exists()) {
				dstFile.setContents(stream, true, true, monitor);
			} else {
				dstFile.create(stream, true, monitor);
			}
			stream.close();

		} catch (IOException e) {
		}

	}
	private void preProcessAndCopyFile(String sdfile, IFile dstFile, IProgressMonitor monitor) throws CoreException {
		
        //Start with datafile
//        String dfile="/Users/ola/data/molsWithAct.sdf";

        //Preprocess file according to the models, now always do FP and molsign
        String tempFile="";
        try {
			tempFile=preprocessDataFile(sdfile);
		} catch (IOException e1) {
			e1.printStackTrace();
			throw new CoreException(new Status(IStatus.ERROR, Activator.PLUGIN_ID, 
					"unable to process data file: " + sdfile + " - Exception: " + e1.getMessage()));
		}
        

        //Copy the file into the project as datafile
        //TODO: update name?
		try {
			InputStream stream = new BufferedInputStream(new FileInputStream(tempFile));
			if (dstFile.exists()) {
				dstFile.setContents(stream, true, true, monitor);
			} else {
				dstFile.create(stream, true, monitor);
			}
			stream.close();

		} catch (IOException e) {
		}
		
		monitor.done();
		
	}
	
	
	private String preprocessDataFile(String datafile) throws IOException {

		//SDF reader
		BufferedReader br = new BufferedReader(new FileReader(new File(datafile)));
		IteratingMDLReader reader = new IteratingMDLReader(br, NoNotificationChemObjectBuilder.getInstance());

		//Temp file writer
		File tempFile = File.createTempFile("ds-data-", ".sdf");
		BufferedOutputStream outsream = new BufferedOutputStream(new FileOutputStream(tempFile));
		SDFWriter writer = new SDFWriter(outsream);

		//Initialize calculators
		Fingerprinter fingerprinter = new Fingerprinter(1024);
		

		while (reader.hasNext()){
			IMolecule mol = (IMolecule) reader.next();

			//Preprocess molecule same as DS does
			try {

				mol = (IMolecule) CDKHelper.standardizeMolecule(mol);

			} catch (CDKException e1) {
				e1.printStackTrace();
				continue;
			}

			//FINGERPRINT
			try {
				BitSet fp = fingerprinter.getFingerprint(mol);
				mol.setProperty(CDK_FP_PROPERTY, encodeFPBase64(fp));
			} catch (CDKException e) {
				e.printStackTrace();
			}
			
			//MOL SIGNATURE
			MoleculeSignature ms = new MoleculeSignature(mol);
			mol.setProperty(MOLECULAR_SIGNATURE_PROPERTY, ms.toCanonicalString());
			
			debugMol(mol, ms.toCanonicalString());


			//Write the new molecule to temp file
			try {
				writer.write(mol);
			} catch (CDKException e) {
				e.printStackTrace();
			}
			
		}

		//Finish up
		writer.close();
		reader.close();
		
		return tempFile.getAbsolutePath();
	}

	private void debugMol(IMolecule mol, String canonicalString) {

		System.out.println("mol with sign: " + canonicalString);
		for (IAtom a : mol.atoms()){
//			System.out.println(a);
		}
		
	}
	
	
	/**
	 * This method duplicates the encoding in the CDKFingerPrintPropertyCalculator. 
	 * This is required to read it back into 1024 bits.
	 * @param value
	 * @return
	 */
    public String encodeFPBase64( Object value ) {
        if(value instanceof String) return (String)value;
         BitSet val = (BitSet)value;
         byte[] bytes = new byte[val.length()/8+1];
         for(int i=0;i<val.length();i++) {
             if(val.get( i )) {
                 bytes[bytes.length-i/8-1] |= 1 <<(i%8);
             }
         }
        return new String(new Base64().encode( bytes ));
    }
	
	/**
	 * Validate options given a template option
	 */
	public void validateOptions(TemplateOption source) {
		if (source.isRequired() && source.isEmpty()) {
			flagMissingRequiredOption(source);
			return;
		}
		super.validateOptions(source);
	}
	
	/*
	 * ===============================
	 * BELOW ARE FOR OUR TEMPLATE ONLY
	 * ===============================
	 */

	public List<Integer> getPagesForSelectedOptions() {
		
		@SuppressWarnings("unchecked")
		List<String> o = (List<String>) getOptionByName(DSTemplate.KEY_SELECTED_MODELS).getValue();
		if (o==null) return null; //No options selected

		List<Integer> ret = new ArrayList<Integer>();
		for (String ms : o){
			
			int pagenr = ModelsOption.getPageNumber(ms);
			ret.add(pagenr);

		}

		Collections.sort(ret);
		return ret;
		
	}

	public int getNoMols() {
		return noMols;
	}
	public void setNoMols(int noMols) {
		this.noMols = noMols;
	}
	public List<String> getAvailProps() {
		return availProps;
	}
	
	public void setAvailProps(List<String> availProps) {
		this.availProps = availProps;

		String[][] props = new String[availProps.size()][2];
		for (int i = 0; i<availProps.size(); i++){
			props[i][0] = availProps.get(i);
			props[i][1] = availProps.get(i);
		}
		
		DSComboChoiceOption co = (DSComboChoiceOption) getOptionByName(EXACT_RESPONSE_PROPERTY_NAME);
		co.setChoices(props);
//		co.selectOptionChoice(props[0][0]);
		co.setValue(props[0][0]);
		validateOptions(co);
		
		co = (DSComboChoiceOption) getOptionByName(NEAREST_RESPONSE_PROPERTY_NAME);
		co.setChoices(props);
//		co.selectOptionChoice(props[0][0]);
		co.setValue(props[0][0]);
		validateOptions(co);
		
		co = (DSComboChoiceOption) getOptionByName(QSAR_RESPONSE_PROPERTY_NAME);
		co.setChoices(props);
		co.setValue(props[0][0]);
		validateOptions(co);
		
	}

}
