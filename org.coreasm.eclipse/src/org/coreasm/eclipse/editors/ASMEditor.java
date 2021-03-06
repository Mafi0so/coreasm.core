package org.coreasm.eclipse.editors;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.coreasm.eclipse.CoreASMPlugin;
import org.coreasm.eclipse.editors.errors.AbstractError;
import org.coreasm.eclipse.editors.errors.ErrorManager;
import org.coreasm.eclipse.editors.errors.SimpleError;
import org.coreasm.eclipse.editors.errors.SyntaxError;
import org.coreasm.eclipse.editors.errors.UndefinedError;
import org.coreasm.eclipse.editors.outlining.AbstractContentPage;
import org.coreasm.eclipse.editors.outlining.ParsedOutlinePage;
import org.coreasm.eclipse.editors.warnings.AbstractWarning;
import org.coreasm.eclipse.preferences.PreferenceConstants;
import org.coreasm.eclipse.tools.ColorManager;
import org.coreasm.util.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.source.DefaultCharacterPairMatcher;
import org.eclipse.jface.text.source.ICharacterPairMatcher;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.texteditor.ChainedPreferenceStore;
import org.eclipse.ui.texteditor.MarkerUtilities;
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

/**
 * The ASMEditor class is the root class of each CoreASM editor which initiates
 * all other classes of the editor.
 * @see		org.eclipse.ui.editors.text.TextEditor
 * @author 	Markus Müller
 */
public class ASMEditor
extends TextEditor
implements IDocumentListener
{
	/** Logger intended for debugging output */
	public static final Logger LOGGER_UI_DEBUG = new Logger("UI-Debug");
	
	/** The default time in milliseconds before the parser will be delayed after
	 * the last occurred edit. */
	public final static int REPARSE_DELAY = 500;
	
	// constants for partition types
	public static final String PARTITION_CODE = "__asm_default";
	public static final String PARTITION_COMMENT = "__asm_comment";

	// constant for marker types
	public static final String MARKER_TYPE_PROBLEM = "org.coreasm.eclipse.markers.ProblemMarker";
	public static final String MARKER_TYPE_PLUGINS = "org.coreasm.eclipse.markers.PluginMarker";
	public static final String MARKER_TYPE_INCLUDE = "org.coreasm.eclipse.markers.IncludeMarker";
	public static final String MARKER_TYPE_DECLARATIONS = "asm.markerType.declarations";
	
	private ASMDocumentProvider documentProvider;
	private ASMParser parser;
	private ASMIncludeWatcher includeWatcher;
	private AbstractContentPage outlinePage;
	private IEditorInput input;
	private ColorManager colorManager;

	static {
		LOGGER_UI_DEBUG.setVisible(false);
	}
	
	public ASMEditor()
	{
		super();
		
		// The full engine should be ready as soon as possible, which means that
		// all available plugins are loaded and initialized. So we're calling
		// SlimEngine.getFullEngine() here, which creates a new full engine in
		// its first call (which will be the first call of this constructor).
		SlimEngine.getFullEngine();
		
		colorManager = new ColorManager();
		documentProvider = new ASMDocumentProvider(this);
		setSourceViewerConfiguration(new ASMConfiguration(this, colorManager));
		setDocumentProvider(documentProvider);
		
		includeWatcher = new ASMIncludeWatcher(this);
		parser = new ASMParser(this);
		parser.addObserver(new ErrorManager(this));
		parser.addObserver(includeWatcher);
		parser.addObserver(new ASMDeclarationWatcher(this));
		
		
		// bind the includeWatcher as ResourceChangeListener to the Workspace
		ResourcesPlugin.getWorkspace().addResourceChangeListener(includeWatcher, IResourceChangeEvent.POST_CHANGE);

		parser.getJob().pause();
		parser.getJob().schedule();
	}
	
	/*
	 * create a common preference store of the editor's standard preference store and the CoreASMPlugin preference store
	 * Hint: First matching preference is used if one preference exists in both preference stores!
	 *
	 * (non-Javadoc)
	 * @see org.eclipse.ui.editors.text.TextEditor#initializeEditor()
	 */
	@Override
	protected void initializeEditor() {
		super.initializeEditor();
		ChainedPreferenceStore chainPrefStore = new ChainedPreferenceStore(
				new IPreferenceStore[]{getPreferenceStore(),CoreASMPlugin.getDefault().getPreferenceStore()}); 
		//use the CoreASM preference store to save and access preferences of the editor, e.g. the bracket highlighting
		setPreferenceStore(chainPrefStore);
	}


	@Override
	public void dispose()
	{
		// stop the ParsingJob
		parser.getJob().interrupt();
		try {
			parser.getJob().join();
		} catch (InterruptedException e) {
			;
		}
		
		ColorManager.dispose();
		if (outlinePage != null)
			outlinePage.setInput(null);
		super.dispose();
		
		// remove the childDocWatcher as WorkspaceListener
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(includeWatcher);
	}
	
	@Override
	protected void doSetInput(IEditorInput newInput)
	throws CoreException 
	{
		// System.out.println("*** doSetInput ***");
		Logger.log(Logger.INFORMATION, LOGGER_UI_DEBUG, "called: doSetInput()");
		
		super.doSetInput(newInput);
		this.input = newInput;
		
		// schedule an immediate reparse
		parser.getJob().unpause();
		parser.getJob().schedule(0);
		
	}

	@Override
	public Object getAdapter(Class required)
	{
		if (IContentOutlinePage.class.equals(required))
		{
			// This block binds an instance of ParsedOutlinePage for this
			// editor to the outline view. If the instance isn't existing yet
			// it is created.
			
			if (outlinePage == null)
			{
				outlinePage = new ParsedOutlinePage(this);
				parser.addObserver(outlinePage);
				
				if (getEditorInput() != null)
					outlinePage.setInput(getEditorInput());
				
				if (outlinePage instanceof ParsedOutlinePage)
					((ParsedOutlinePage) outlinePage).setupListener();
			}
			return outlinePage;
		}
		return super.getAdapter(required);
	}

	@Override
	protected void editorSaved() {
		super.editorSaved();
	}
	
	/**
	 * This method reconfigures the syntax highlighting of the editor with a new
	 * set of keywords and ids.
	 */
	public void setSyntaxHighlighting(Set<String> keywords, Set<String> ids)
	{
		ASMConfiguration configuration = (ASMConfiguration) getSourceViewerConfiguration();
		KeywordScanner keywordScanner = configuration.getASMKeywordScanner();
		keywordScanner.init(keywords, ids);
		
		Display display = getEditorSite().getWorkbenchWindow().getWorkbench().getDisplay();
		display.asyncExec(new Runnable() {
			public void run() {
				if (getSourceViewer() != null) {
					try {
						getSourceViewer().invalidateTextPresentation();
					} catch (IllegalArgumentException e) {
//						FIXME: Why does this call sometimes cause an IllegalArgumentException at org.eclipse.swt.graphics.TextStyle.<init>(TextStyle.java:171)?
//						foreground.isDisposed() is true, but why?
					}
				}
			}
		});
	}
	
	/**
	 * Creates an error mark for a SimpleError.
	 */
	public void createSimpleMark(SimpleError error, int severity)
	{
		IDocument document = getInputDocument();
		int line = 0;
		try {
			line = document.getLineOfOffset(error.getPosition());
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
		
		Map map = new HashMap();
		MarkerUtilities.setLineNumber(map, line);
		MarkerUtilities.setMessage(map, error.getDescription());
		map.put(IMarker.LOCATION, getInputFile().getFullPath().toString());
		map.put(IMarker.CHAR_START, error.getPosition());
		map.put(IMarker.CHAR_END, error.getPosition()+error.getLength());
		map.put(IMarker.SEVERITY, severity);
		map.put("data", error.encode());
		try {
			MarkerUtilities.createMarker(getInputFile(), map, MARKER_TYPE_PROBLEM);
		} catch (CoreException e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Creates an error mark for a SyntaxError.
	 */
	public void createSyntaxMark(SyntaxError error, int severity)
	{
		Map map = new HashMap();
		MarkerUtilities.setLineNumber(map, error.getLine());
		MarkerUtilities.setMessage(map, error.getDescription().replace('\n', ' '));
		map.put(IMarker.LOCATION, getInputFile().getFullPath().toString());
		map.put(IMarker.SEVERITY, severity);
		map.put(IMarker.CHAR_START, error.getPosition());
		map.put(IMarker.CHAR_END, error.getPosition()+error.getLength());
		map.put("data", error.encode());
		
		try {
			MarkerUtilities.createMarker(getInputFile(), map, MARKER_TYPE_PROBLEM);
		} catch (CoreException e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Creates an error mark for a UndefinedError.
	 */
	public void createUndefinedMark(UndefinedError error, int severity)
	{
		IDocument document = getInputDocument();
		int position = 0;
		try {
			position = document.getLineOffset(error.getLine() - 1) + error.getColumn() + 1;
		} catch (BadLocationException e) {
			e.printStackTrace();
		}

		Map<String, Comparable> map = new HashMap();
		MarkerUtilities.setLineNumber(map, error.getLine());
		MarkerUtilities.setMessage(map, error.getDescription());
		map.put(IMarker.LOCATION, getInputFile().getFullPath().toString());
		map.put(IMarker.SEVERITY, severity);
		map.put(IMarker.CHAR_START, position);
		map.put(IMarker.CHAR_END, position+1);
		map.put("data", error.encode());
		
		try {
			MarkerUtilities.createMarker(getInputFile(), map, MARKER_TYPE_PROBLEM);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Creates an invisible marker which stores the name of the plugins which
	 * are used by the CoreASM specification currently loaded by this editor.
	 * This is done by a marker so an ASMEditor can check the plugins for
	 * other specifications without loading those specifications.
	 */
	public void createPluginMark(Set<String> plugins)
	{
		// Delete the old plugin marker, if there is one
		try {
			getInputFile().deleteMarkers(MARKER_TYPE_PLUGINS, false, IResource.DEPTH_ZERO);
		} catch (CoreException e1) {
			e1.printStackTrace();
		}
		
		// Build a string with the names of all plugins (separator: '/')
		StringBuilder strPlugins = new StringBuilder();
		for (String p: plugins)
			strPlugins.append(p).append('/');
		if (strPlugins.length() > 0)
			strPlugins.deleteCharAt(strPlugins.length()-1);
		
		Map map = new HashMap();
		map.put(IMarker.LOCATION, getInputFile().getFullPath().toString());
		map.put("plugins", strPlugins.toString());
		map.put(IMarker.CHAR_START, 0);
		map.put(IMarker.CHAR_END, 1);		
		try {
			MarkerUtilities.createMarker(getInputFile(), map, MARKER_TYPE_PLUGINS);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Creates an invisible marker which stores the name of all modules which
	 * are included by the CoreASM specification currently loaded by this editor.
	 * This is done by a marker so an ASMEditor can check the includes modules for
	 * other specifications without loading those specifications.
	 */
	public void createIncludeMark(Set<IPath> includes)
	{
		// Delete the old plugin marker, if there is one
		try {
			getInputFile().deleteMarkers(MARKER_TYPE_INCLUDE, false, IResource.DEPTH_ZERO);
		} catch (CoreException e1) {
			e1.printStackTrace();
		}
		
		// Only create a new marker if there are includes
		if (includes.isEmpty())
			return;
		
		// Build a string with the names of all includes
		StringBuilder strIncludes = new StringBuilder();
		for (IPath p: includes)
			strIncludes.append(p).append(AbstractError.SEPERATOR_VAL);
		if (strIncludes.length() > 0)
			strIncludes.deleteCharAt(strIncludes.length()-1);
		
		Map map = new HashMap();
		map.put(IMarker.LOCATION, getInputFile().getFullPath().toString());
		map.put("includes", strIncludes.toString());
		map.put(IMarker.CHAR_START, 0);
		map.put(IMarker.CHAR_END, 1);		
		try {
			MarkerUtilities.createMarker(getInputFile(), map, MARKER_TYPE_INCLUDE);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
	
	public void createDeclarationsMark(String declarations) {
		try {
			getInputFile().deleteMarkers(MARKER_TYPE_DECLARATIONS, false, IResource.DEPTH_ZERO);
		} catch (CoreException e1) {
			e1.printStackTrace();
		}
		Map<String, Object> map = new HashMap<String, Object>();
		MarkerUtilities.setCharStart(map, 0);
		MarkerUtilities.setCharEnd(map, 1);
		map.put(IMarker.LOCATION, getInputFile().getFullPath().toString());
		map.put("declarations", declarations);
		try {
			MarkerUtilities.createMarker(getInputFile(), map, MARKER_TYPE_DECLARATIONS);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
	
	public void createErrorMark(AbstractError error)
	{
		IDocument document = getInputDocument();
		int line = 0;
		try {
			line = document.getLineOfOffset(error.getPosition());
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
		
		Map<String, Object> map = new HashMap<String, Object>();
		MarkerUtilities.setLineNumber(map, line);
		MarkerUtilities.setMessage(map, error.get(AbstractError.DESCRIPTION));
		MarkerUtilities.setCharStart(map, error.getPosition());
		MarkerUtilities.setCharEnd(map, error.getPosition() + error.getLength());
		map.put(IMarker.LOCATION, getInputFile().getFullPath().toString());
		map.put(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
		map.put("data", error.encode());
		try {
			MarkerUtilities.createMarker(getInputFile(), map, MARKER_TYPE_PROBLEM);
		} catch (CoreException e) {
			e.printStackTrace();
		}
		
	}
	
	public void createWarningMark(AbstractWarning warning) {
		int line = 0;
		try {
			line = getInputDocument().getLineOfOffset(warning.getPosition());
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
		
		Map<String, Object> map = new HashMap<String, Object>();
		MarkerUtilities.setLineNumber(map, line);
		MarkerUtilities.setMessage(map, warning.getDescription());
		MarkerUtilities.setCharStart(map, warning.getPosition());
		MarkerUtilities.setCharEnd(map, warning.getPosition() + warning.getLength());
		map.put(IMarker.LOCATION, getInputFile().getFullPath().toString());
		map.put(IMarker.SEVERITY, IMarker.SEVERITY_WARNING);
		map.put("data", warning.getData());
		try {
			MarkerUtilities.createMarker(getInputFile(), map, MARKER_TYPE_PROBLEM);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Removes all markers of the specified type from the current document
	 */
	public void removeMarkers(String type)
	{
		try {
			getInputFile().deleteMarkers(type, true, IResource.DEPTH_ZERO);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Returns the parser object which is bound to this ASMEditor instance.
	 */
	public ASMParser getParser()
	{
		return parser;
	}
	
	/**
	 * Returns an ASMDocument for the current editor input from the document provider
	 */
	public IDocument getInputDocument()
	{
		IDocument document = getDocumentProvider().getDocument(input);
		return document;
	}
	
	public IEditorInput getInput()
	{
		return input;
	}
	
	/**
	 * Returns the IFile object for the file the specification loaded in this
	 * editor is stored in.
	 */
	public IFile getInputFile()
	{
		if (input == null)
			return null;
		IFileEditorInput ife = (IFileEditorInput) input;
		IFile file = ife.getFile();
		return file;
	}
	

	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {
		// Nothing to do
	}

	/**
	 * This method is called each time the document is edited. It ensures that
	 * the document is parsed after it has been modified. However, the parser is
	 * not run after each edit, it is just rescheduled for a second after the last
	 * edit. So the document won't be parsed unless the user makes no input for
	 * at least one second.
	 * 
	 * @see org.eclipse.jface.text.IDocumentListener#documentChanged(org.eclipse.jface.text.DocumentEvent)
	 */
	@Override
	public void documentChanged(DocumentEvent event) {
		parser.getJob().cancel();
		parser.getJob().schedule(REPARSE_DELAY);
		
		// Don't forget to update the offset of the header elements in ASMDocument!
		IDocument doc = event.getDocument();
		if (doc instanceof ASMDocument) {
			int delta = event.getText().length() - event.getLength();
			((ASMDocument) doc).updateHeaders(event.getOffset(), delta);
		}
	}
	
	/**
	 * This method is a public interface for the protected method getSourceViewer()
	 * of superclass AbstractTextEditor.
	 * @return the ISourceViewer which is returned by AbstractTextEditor.getSourceViewer()
	 */
	public ISourceViewer getASMSourceViewer() {
		return getSourceViewer();
	}
	
	/**
	 * The method configureSourceViewerDecorationSupport implements the syntax highlighting for matching brackets (),{},[]
	 * The highlighting can be (de-)activated via CoreASM preference page and the highlighting Color can be selected their, too. Updates occur automatically by the framework.
	 * @param support 
	 */
	@Override
	protected void configureSourceViewerDecorationSupport (SourceViewerDecorationSupport support) {
		super.configureSourceViewerDecorationSupport(support);

		//create the matcher for pairs of brackets
		char[] matchChars = {'(', ')', '{', '}','[',']'}; //which brackets to match		
		ICharacterPairMatcher matcher = new DefaultCharacterPairMatcher(matchChars , IDocumentExtension3.DEFAULT_PARTITIONING);
		support.setCharacterPairMatcher(matcher);
		support.setMatchingCharacterPainterPreferenceKeys(PreferenceConstants.EDITOR_MATCHING_BRACKETS,PreferenceConstants.EDITOR_MATCHING_BRACKETS_COLOR);
		
		//Enable bracket highlighting in the preference store
		//and set default values
		IPreferenceStore store = CoreASMPlugin.getDefault().getPreferenceStore();
		store.setDefault(PreferenceConstants.EDITOR_MATCHING_BRACKETS, true); //highlighting activated
		store.setDefault(PreferenceConstants.EDITOR_MATCHING_BRACKETS_COLOR, "128,128,128"); //color of highlighting box is gray
	
	}
}
