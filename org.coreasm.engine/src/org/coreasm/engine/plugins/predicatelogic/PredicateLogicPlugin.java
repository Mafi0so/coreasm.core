/*	
 * PredicateLogicPlugin.java 	1.0 	$Revision: 243 $
 * 
 * Copyright (C) 2006 George Ma
 * Copyright (c) 2007 Roozbeh Farahbod
 * 
 * Last modified on $Date: 2011-03-29 02:05:21 +0200 (Di, 29 Mrz 2011) $ by $Author: rfarahbod $
 *
 * Licensed under the Academic Free License version 3.0 
 *   http://www.opensource.org/licenses/afl-3.0.php
 *   http://www.coreasm.org/afl-3.0.php
 *
 */
package org.coreasm.engine.plugins.predicatelogic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jparsec.Parser;
import org.codehaus.jparsec.Parsers;
import org.coreasm.engine.VersionInfo;
import org.coreasm.engine.absstorage.BooleanElement;
import org.coreasm.engine.absstorage.Element;
import org.coreasm.engine.absstorage.Enumerable;
import org.coreasm.engine.interpreter.ASTNode;
import org.coreasm.engine.interpreter.Interpreter;
import org.coreasm.engine.interpreter.InterpreterException;
import org.coreasm.engine.interpreter.Node;
import org.coreasm.engine.kernel.Kernel;
import org.coreasm.engine.kernel.KernelServices;
import org.coreasm.engine.parser.GrammarRule;
import org.coreasm.engine.parser.ParserTools;
import org.coreasm.engine.parser.OperatorRule;
import org.coreasm.engine.parser.ParseMapN;
import org.coreasm.engine.parser.OperatorRule.OpType;
import org.coreasm.engine.plugin.InterpreterPlugin;
import org.coreasm.engine.plugin.OperatorProvider;
import org.coreasm.engine.plugin.ParserPlugin;
import org.coreasm.engine.plugin.Plugin;
import org.coreasm.util.Tools;

/** 
 * Plugin for predicate logic
 *   
 *  @author  George Ma, Roozbeh Farahbod
 *  
 */
public class PredicateLogicPlugin extends Plugin implements OperatorProvider, ParserPlugin, InterpreterPlugin {
    
	public static final VersionInfo VERSION_INFO = new VersionInfo(0, 4, 9, "");
	
	public static final String PLUGIN_NAME = PredicateLogicPlugin.class.getSimpleName();

	public static final String IMPLY_OP = "implies";
    public static final String OR_OP = "or";
    public static final String XOR_OP = "xor";
    public static final String AND_OP = "and";
    public static final String NOT_OP = "not";
    public static final String FORALL_EXP_TOKEN = "forall";
    public static final String EXISTS_EXP_TOKEN = "exists";
    public static final String NOT_EQ_OP = "!=";
    public static final String IN_OP = "memberof";
    public static final String NOTIN_OP = "notmemberof";
    
    // for keeping track of considered elements in Exists and Forall expressions
    private ThreadLocal<Map<ASTNode, List<Element>>> remained;
 
    private ArrayList<OperatorRule> opRules = null;
    private Map<String, GrammarRule> parsers = null; 
    
	private final String[] keywords = {IMPLY_OP, OR_OP, XOR_OP, AND_OP, NOT_OP, NOTIN_OP,
			"forall", "holds", "exists", "with", IN_OP, "in"};
	private final String[] operators = {"!="};
	
	/**
	 * Create a new instance of PredicateLogicPlugin
	 */
	public PredicateLogicPlugin() {
		super();
        remained = new ThreadLocal<Map<ASTNode, List<Element>>>() {
			@Override
			protected Map<ASTNode, List<Element>> initialValue() {
				return new HashMap<ASTNode, List<Element>>();
			}
        };
    }

	private Map<ASTNode, List<Element>> getRemainedMap() {
		return remained.get();
	}

	public String[] getKeywords() {
		return keywords;
	}

	public String[] getOperators() {
		return operators;
	}

	/* (non-Javadoc)
	 * @see org.coreasm.engine.Plugin#initialize()
	 */
	@Override
	public void initialize() {
	            
	}

	//--------------------------------
	// Operator Implementor Interface
	//--------------------------------

	/* (non-Javadoc)
	 * @see org.coreasm.engine.plugin.OperatorProvider#getOperatorRules()
	 */
	public Collection<OperatorRule> getOperatorRules() {
	
		if (opRules == null) {
			opRules = new ArrayList<OperatorRule>();
			
			opRules.add(new OperatorRule(IMPLY_OP,
					    OpType.INFIX_LEFT,
					    375,
					    getName()));
	        
	        opRules.add(new OperatorRule(OR_OP,
	                    OpType.INFIX_LEFT,
	                    350,
	                    getName()));
	        
	        opRules.add(new OperatorRule(XOR_OP,
	                    OpType.INFIX_LEFT,
	                    350,
	                    getName()));
	        
	        opRules.add(new OperatorRule(AND_OP,
	                    OpType.INFIX_LEFT,
	                    400,
	                    getName()));
	        
	        opRules.add(new OperatorRule(NOT_OP,
	                    OpType.PREFIX,
	                    850,
	                    getName()));
	        
	        opRules.add(new OperatorRule(IN_OP,
	                    OpType.INFIX_LEFT,
	                    550,
	                    getName()));
	        
	        opRules.add(new OperatorRule(NOTIN_OP,
	                    OpType.INFIX_LEFT,
	                    550,
	                    getName()));
	        		
	        opRules.add(new OperatorRule(NOT_EQ_OP,
		                OpType.INFIX_LEFT,
		                600,
		                getName()));
		}
    		
		return opRules;
	}

	/* (non-Javadoc)
	 * @see org.coreasm.engine.plugin.OperatorProvider#interpretOperatorNode(org.coreasm.engine.interpreter.Node)
	 */
	public Element interpretOperatorNode(Interpreter interpreter, ASTNode opNode) throws InterpreterException {
        Element result = null;
        String x = opNode.getToken();
        String gClass = opNode.getGrammarClass();
        
        // if class of operator is binary
        if (gClass.equals(ASTNode.BINARY_OPERATOR_CLASS)) {
            
            // get operand nodes
            ASTNode alpha = opNode.getFirst();
            ASTNode beta = alpha.getNext();
            
            // get operand values
            Element l = alpha.getValue();
            Element r = beta.getValue();
            
            if (x.equals(NOT_EQ_OP)) {
            	result = BooleanElement.valueOf(!Kernel.evaluateEquality(l, r));
            }
            else if (x.equals(IN_OP) || x.equals(NOTIN_OP)) {
            	if (r.equals(Element.UNDEF)) {
            		result = Element.UNDEF;
					capi.warning(PLUGIN_NAME, "The operand of the unary operator '" + x + "' was undef.", opNode, interpreter);
            	} else 
	                if (r instanceof Enumerable) {
		                Enumerable enumerableElement = (Enumerable) r;
		                
		                if (x.equals(IN_OP)) {
		                    result = BooleanElement.valueOf(enumerableElement.contains(l));
		                }
		                else if (x.equals(NOTIN_OP)) {
		                    result = BooleanElement.valueOf(!enumerableElement.contains(l));
		                }
	                }
            }
            else {
                // confirm that operands are boolean elements, otherwise throw an error
            	if ((l instanceof BooleanElement || l.equals(Element.UNDEF)) 
            			&& (r instanceof BooleanElement || r.equals(Element.UNDEF))) {
            		if (r instanceof BooleanElement && l instanceof BooleanElement) {
                        // convert operands to boolean elements
                        BooleanElement eL = (BooleanElement)l;
                        BooleanElement eR = (BooleanElement)r;
                        
                        if (x.equals(IMPLY_OP))
                            result = BooleanElement.valueOf((!eL.getValue()) | eR.getValue());
                        else if (x.equals(OR_OP))
                            result = BooleanElement.valueOf(eL.getValue() | eR.getValue());
                        else if (x.equals(XOR_OP))
                            result = BooleanElement.valueOf(eL.getValue() ^ eR.getValue());
                        else if (x.equals(AND_OP))
                            result = BooleanElement.valueOf(eL.getValue() & eR.getValue());
            		} else {
    					result = Element.UNDEF;
    					if (l.equals(Element.UNDEF) && r.equals(Element.UNDEF))
    						capi.warning(PLUGIN_NAME, "Both operands of the '" + x + "' operator were undef.", opNode, interpreter);
    					else
    						if (l.equals(Element.UNDEF))
    							capi.warning(PLUGIN_NAME, "The left operand of the '" + x + "' operator was undef.", opNode, interpreter);
    						else
    							if (r.equals(Element.UNDEF))
    								capi.warning(PLUGIN_NAME, "The right operand of the '" + x + "' operator was undef.", opNode, interpreter);
            		}
            	}
            }
        }
        // if class of operator is unary
        if (gClass.equals(ASTNode.UNARY_OPERATOR_CLASS))
        {
            // get operand nodes
            ASTNode alpha = opNode.getFirst();
            
            // get operand values
            Element o = alpha.getValue();
            
            if (o.equals(Element.UNDEF)) {
            	result = Element.UNDEF;
				capi.warning(PLUGIN_NAME, "The operand of the unary operator '" + x + "' was undef.", opNode, interpreter);
        	} else 
	            // confirm that operand is Boolean element
	            if (o instanceof BooleanElement) {
		            // convert operand to boolean element
		            BooleanElement eO = (BooleanElement)o;
		            // logical negation
		            if (x.equals(NOT_OP)) {
		                result = BooleanElement.valueOf(! eO.getValue());
		            }
	            }
        }
        
        return result;
	}

    //--------------------------------
    // ParserPlugin Interface
    //--------------------------------
    
	public Set<Parser<? extends Object>> getLexers() {
		return Collections.emptySet();
	}

	/**
	 * @return <code>null</code>
	 */
	public Parser<Node> getParser(String nonterminal) {
		return null;
	}

   public Map<String, GrammarRule> getParsers() {
    	if (parsers == null) {
			parsers = new HashMap<String, GrammarRule>();
			KernelServices kernel = (KernelServices) capi.getPlugin("Kernel")
					.getPluginInterface();

			Parser<Node> termParser = kernel.getTermParser();

			ParserTools pTools = ParserTools.getInstance(capi);
			Parser<Node> idParser = pTools.getIdParser();

			// ForallExp : 'forall' ID 'in' Term 'holds' Term
			Parser<Node> forallExpParser = Parsers.array(
					new Parser[] {
						pTools.getKeywParser(FORALL_EXP_TOKEN, PLUGIN_NAME),
						idParser,
						pTools.getKeywParser("in", PLUGIN_NAME),
						termParser,
						pTools.getKeywParser("holds", PLUGIN_NAME),
						termParser
					}).map(
					new ParserTools.ArrayParseMap(PLUGIN_NAME) {
						public Node map(Object... vals) {
							Node node = new ForallExpNode(((Node)vals[0]).getScannerInfo());
							addChildren(node, vals);
							return node;
						}
			} );
			parsers.put("forallExp",
					new GrammarRule("forallExp", 
							"'forall' ID 'in' Term 'holds' Term", forallExpParser, PLUGIN_NAME));

			// ExistsExp : 'exists' ID 'in' Term 'with' Term
			Parser<Node> existsExpParser = Parsers.array(
					new Parser[] {
						pTools.getKeywParser(EXISTS_EXP_TOKEN, PLUGIN_NAME),
						idParser,
						pTools.getKeywParser("in", PLUGIN_NAME),
						termParser,
						pTools.getKeywParser("with", PLUGIN_NAME),
						termParser
					}).map(
					new ParserTools.ArrayParseMap(PLUGIN_NAME) {
						public Node map(Object... vals) {
							Node node = new ExistsExpNode(((Node)vals[0]).getScannerInfo());
							addChildren(node, vals);
							return node;
						}
			} );
			parsers.put("ExistsExp",
					new GrammarRule("ExistsExp", 
							"'exists' ID 'in' Term 'with' Term", existsExpParser, PLUGIN_NAME));
			
			// PredicateBasicTerm : ForallExp | ExistsExp
			Parser<Node> _parser = Parsers.or(forallExpParser, existsExpParser);
			parsers.put("BasicTerm", 
					new GrammarRule("PredicateBasicTerm", "ForallExp | ExistsExp",
							_parser, PLUGIN_NAME));
    	}
    	
    	return parsers;
    }
    
    //--------------------------------
    // InterpreterPlugin Interface
    //--------------------------------
    
    /* (non-Javadoc)
     * @see org.coreasm.engine.plugin.InterpreterPlugin#interpret(org.coreasm.engine.interpreter.Node)
     */
    public ASTNode interpret(Interpreter interpreter, ASTNode pos) {
        if (pos instanceof ExistsExpNode) { 
            return interpretExists(interpreter, pos);
        }
        else if (pos instanceof ForallExpNode) {
            return interpretForall(interpreter, pos);
        }                        
        return null;
    }
    
    /**
     * Interprets a node representing an exists expression
     * @param pos
     * @return
     */
    private ASTNode interpretExists(Interpreter interpreter, ASTNode pos) {
        ExistsExpNode existsExpNode = (ExistsExpNode) pos;
        
        Map<ASTNode, List<Element>> remained = getRemainedMap();
        
        if (!existsExpNode.getDomain().isEvaluated()) {
           
            // a new domain will be evaluated so clear the value of considered
            //   considered(beta) := {}
        	remained.remove(existsExpNode.getDomain());
        	//considered.put(existsExpNode.getDomain(),new ArrayList<Element>());
            
            // evaluate the domain
            //   pos := beta  
            return existsExpNode.getDomain();
        }
        else if (!existsExpNode.getCondition().isEvaluated()) {
            if (existsExpNode.getDomain().getValue() instanceof Enumerable) {
                
                // let s = enumerate(v)\considered(beta)
                Enumerable enumerableElement = (Enumerable) existsExpNode.getDomain().getValue();             
                
                List<Element> remainingElements = remained.get(existsExpNode.getDomain());
                if (remainingElements == null) {
        			Enumerable domain = (Enumerable)existsExpNode.getDomain().getValue();
        			if (domain.supportsIndexedView())
        				remainingElements = new ArrayList<Element>(domain.getIndexedView());
        			else
        				remainingElements = new ArrayList<Element>(enumerableElement.enumerate());
                	remained.put(existsExpNode.getDomain(), remainingElements);
                }
                //ArrayList<Element> remainingElements = new ArrayList<Element>(enumerableElement.enumerate());
                //remainingElements.removeAll(considered.get(existsExpNode.getDomain()));
                
                if (remainingElements.size() > 0) {
                    
                    // choose t in s (for simplicity just pick the first one)
                    Element chosenElement = remainingElements.get(0);
                    
                    // AddEnv(x,t)
                    interpreter.addEnv(existsExpNode.getVariable().getToken(),chosenElement);
                    
                    // considered(beta) := considered(beta) union {t}
                    remainingElements.remove(0);
                    //considered.get(existsExpNode.getDomain()).add(chosenElement);
                    
                    // pos := gamma
                    return existsExpNode.getCondition();
                }
                else {
                	remained.remove(existsExpNode.getDomain());
                    //considered.remove(existsExpNode.getDomain());
                    
                    // [pos] := (undef,undef,ff)
                    pos.setNode(null,null,BooleanElement.FALSE);
                    return pos;
                }
            }
            else {
                capi.error("The 'exists' predicate does not apply to " + 
                		Tools.sizeLimit(existsExpNode.getDomain().getValue().denotation()) + 
                		". The domain must be an enumerable element.", existsExpNode.getDomain(), interpreter);
            }
        }
        else {
            
            // get the value of the condition (gamma) and save it before we clear it
            boolean value = false;            
            if (existsExpNode.getCondition().getValue() instanceof BooleanElement) {
                value = ((BooleanElement) existsExpNode.getCondition().getValue()).getValue();
            }
            else {
                capi.error("value of exists condition is not Boolean.", existsExpNode.getCondition(), interpreter);
            }

            // ClearTree(gamma)
            interpreter.clearTree(existsExpNode.getCondition());
            
            // RemoveEnv(x)
            interpreter.removeEnv(existsExpNode.getVariable().getToken());
            
            if (value) {
                
            	remained.remove(existsExpNode.getDomain());
                //considered.remove(existsExpNode.getDomain());
                
                // [pos] := (undef,undef,tt)                
                pos.setNode(null,null,BooleanElement.TRUE);
                return pos;
            }
            else {
                // pos := beta
                return existsExpNode.getDomain();
            }
        }                                                
        
        return pos;
    }
    
    /**
     * Interprets a node representing a forall expression
     * @param pos
     * @return
     */
    private ASTNode interpretForall(Interpreter interpreter, ASTNode pos) {
        ForallExpNode forallExpNode = (ForallExpNode) pos;
        
        Map<ASTNode, List<Element>> remained = getRemainedMap();

        if (!forallExpNode.getDomain().isEvaluated()) {
            
            // a new domain will be evaluated so clear the value of considered
            //   considered(beta) := {}
            remained.remove(forallExpNode.getDomain());
        	//considered.put(forallExpNode.getDomain(),new ArrayList<Element>());
            
            // evaluate the domain
            //   pos := beta
            return forallExpNode.getDomain();
        }
        else if (!forallExpNode.getCondition().isEvaluated()) {
            if (forallExpNode.getDomain().getValue() instanceof Enumerable) {
                Enumerable enumerableElement = (Enumerable) forallExpNode.getDomain().getValue();
                
                if (enumerableElement.enumerate().size() > 0) {
                    
                    // let s = enumerate(v)\considered(beta)
                	List<Element> remainingElements = remained.get(forallExpNode.getDomain());
                	if (remainingElements == null) {
            			Enumerable domain = (Enumerable)forallExpNode.getDomain().getValue();
            			if (domain.supportsIndexedView())
            				remainingElements = new ArrayList<Element>(domain.getIndexedView());
            			else
            				remainingElements = new ArrayList<Element>(enumerableElement.enumerate());
                		remained.put(forallExpNode.getDomain(), remainingElements);
                	}
                    
                    //ArrayList<Element> remainingElements = new ArrayList<Element>(enumerableElement.enumerate());
                    //remainingElements.removeAll(considered.get(forallExpNode.getDomain()));
                    
                    if (remainingElements.size() > 0) {
                        
                        // choose t in s (for simplicity just pick the first one)                        
                        Element chosenElement = remainingElements.get(0);
                        
                        // AddEnv(x,t)
                        interpreter.addEnv(forallExpNode.getVariable().getToken(),chosenElement);
                        
                        // considered(beta) := considered(beta) union {t}
                        remainingElements.remove(0);
                        //considered.get(forallExpNode.getDomain()).add(chosenElement);
                        
                        // pos := gamma
                        return forallExpNode.getCondition();
                    }
                    else {    
                        remained.remove(forallExpNode.getDomain());
                    	//considered.remove(forallExpNode.getDomain());
                        
                        // [pos] := (undef,undef,tt)
                        pos.setNode(null,null,BooleanElement.TRUE);
                        return pos;
                    }
                }
                else {
                    remained.remove(forallExpNode.getDomain());
                    //considered.remove(forallExpNode.getDomain());
                    
                    // [pos] := (undef,undef,tt)
                    pos.setNode(null,null,BooleanElement.TRUE);
                    return pos;
                }
            }
            else {
                capi.error("The 'forall' predicate does not apply to " + 
                		Tools.sizeLimit(forallExpNode.getDomain().getValue().denotation()) + 
                		". The domain must be an enumerable element.", forallExpNode.getDomain(), interpreter);
            }
        }
        else {
            
            // get the value of the condition (gamma) and save it before we clear it
            boolean value = false;            
            if (forallExpNode.getCondition().getValue() instanceof BooleanElement) {
                value = ((BooleanElement) forallExpNode.getCondition().getValue()).getValue();
            }
            else {
                capi.error("value of forall condition is not Boolean.", forallExpNode.getCondition(), interpreter);
            }
            
            // ClearTree(gamma)
            interpreter.clearTree(forallExpNode.getCondition());
            
            // RemoveEnv(x)
            interpreter.removeEnv(forallExpNode.getVariable().getToken());
            
            if (value) {
                // pos := beta
                return forallExpNode.getDomain();                
            }
            else {
                remained.remove(forallExpNode.getDomain());
                //considered.remove(forallExpNode.getDomain());
                
                // [pos] := (undef,undef,ff)             
                pos.setNode(null,null,BooleanElement.FALSE);
                return pos;
            }
        }                                                
        
        return pos;
    }

    public VersionInfo getVersionInfo() {
		return VERSION_INFO;
	}

}
