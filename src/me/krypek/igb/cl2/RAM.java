package me.krypek.igb.cl2;

import static me.krypek.igb.cl1.datatypes.Instruction.Device_ScreenUpdate;
import static me.krypek.igb.cl1.datatypes.Instruction.Init;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import me.krypek.igb.cl1.IGB_CL1;
import me.krypek.igb.cl1.IGB_MA;
import me.krypek.igb.cl2.IGB_CL2_Exception.Err;
import me.krypek.igb.cl2.datatypes.Array;
import me.krypek.igb.cl2.datatypes.Variable;
import me.krypek.utils.Pair;
import me.krypek.utils.Utils;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

public class RAM {
	public static final String[] illegalCharacters = { "{", "}", "(", ")", "[", "]", ";", "$", "=", "|", ">", "<" };
	public static final Set<String> illegalNames = Set.of("else", "if", "for", "while");

	private final boolean[] allocationArray;

	HashMap<String, Double> finalVars;

	private final Stack<Map<String, Variable>> variableStack;
	private final Stack<Map<String, Array>> arrayStack;

	private final int ramcell;
	private int thread;

	public RAM(int ramlimit, int ramcell, int thread) {
		this.thread = thread;
		this.ramcell = ramcell;
		finalVars = getDefaultFinalVars();
		allocationArray = new boolean[ramlimit];
		variableStack = getDefaultVariables();
		arrayStack = new Stack<>();
		next();
	}

	public void setFinalVariables(HashMap<String, Double> finalVars) { this.finalVars = finalVars; }

	private int allocateSpace(int size) {
		for1: for (int i = 0; i < allocationArray.length; i++) {
			final int _i = size + i;
			if(_i >= allocationArray.length)
				throw Err.normal("Ran out of space in RAM!");
			final int startI = i;
			for (; i < _i; i++)
				if(allocationArray[i])
					continue for1;

			for (int h = startI; h < startI + size; h++)
				allocationArray[h] = true;

			return startI + ramcell;
		}

		throw Err.normal("Ran out of space in RAM!");
	}

	public int checkName(String name, int size) {
		int cell = -1;
		if(name.endsWith("|")) {
			String[] split1 = name.split("\\|");
			if(split1.length != 2)
				throw Err.normal("Variable cell syntax Error.");

			boolean doAllocateSetCell = false;

			if(split1[1].startsWith("!")) {
				split1[1] = split1[1].substring(1);
				doAllocateSetCell = true;
			}
			cell = (int) solveFinalEq(split1[1]);
			if(cell < 0)
				throw Err.normal("Variable cell cannot be negative.");

			if(doAllocateSetCell)
				allocateSetCell(cell, size);

		}
		if(finalVars.containsKey(name))
			throw Err.normal("Cannot create a variable that is named the same as a final variable.");

		if(illegalNames.contains(name))
			throw Err.normal("Illegal variable name: \"" + name + "\".");

		for (String c : illegalCharacters)
			if(name.contains(c) && (!c.equals("" + '|') || cell == -1))
				throw Err.normal("Variable name \"" + name + "\" contains illegal character: \"" + c + "\".");

		return cell;
	}

	public int[] reserve(int amount) {
		int[] arr = new int[amount];
		for (int i = 0; i < amount; i++)
			arr[i] = allocateSpace(1);
		return arr;
	}

	public int newVar(String name) {
		var pair = preNewVar(name);

		variableStack.peek().put(pair.getFirst(), new Variable(pair.getSecond()));
		return pair.getSecond();
	}

	private void allocateSetCell(int cell1, int size) {
		int cellInArray = cell1 - ramcell;
		if(cellInArray < 0 || cellInArray >= allocationArray.length)
			throw Err.normal("Cannot allocate outside of allocation array.");
		if(allocationArray[cellInArray])
			throw Err.normal("Cell already allocated.");

		for (int cell = cellInArray; cell < cellInArray + size; cell++)
			allocationArray[cell] = true;
	}

	public Pair<String, Integer> preNewVar(String name) {
		int cell1 = checkName(name, 1);
		final int cell;
		if(cell1 == -1)
			cell = allocateSpace(1);
		else {
			name = name.substring(0, name.indexOf('|')).strip();
			cell = cell1;
		}
		return new Pair<>(name, cell);
	}

	public int newVar(String name, Variable var) {
		int cell = checkName(name, 1);
		assert cell == -1;
		variableStack.peek().put(name, var);
		return var.cell;
	}

	public int newArray(String name, int[] size) {
		int totalSize = calcArraySize(size);

		int cell1 = checkName(name, totalSize);
		final int cell;
		if(cell1 == -1)
			cell = allocateSpace(totalSize);
		else {
			name = name.substring(0, name.indexOf('|'));
			cell = cell1;
		}

		arrayStack.peek().put(name, new Array(cell, size, totalSize));
		return cell;
	}

	public static int calcArraySize(int[] size) {
		int totalSize = 1;
		for (int element : size)
			totalSize *= element;

		return totalSize;
	}

	@SuppressWarnings("unused")
	public void pop() {
		Map<String, Variable> t1 = variableStack.pop();
		/*
		 * for (Variable var : t1.values()) { int cell = var.cell - ramcell; if(cell < 0
		 * || cell >= allocationArray.length) continue; allocationArray[cell] = false; }
		 */
		Map<String, Array> t2 = arrayStack.pop();
		/*
		 * for (Array array : t2.values()) { int from = array.cell - ramcell; int to =
		 * from + array.totalSize;
		 * 
		 * if(from < 0 || from >= allocationArray.length || to < 0 || to >=
		 * allocationArray.length) continue;
		 * 
		 * for (int i = from; i < to; i++) allocationArray[i] = false; }
		 */
	}

	public int getVariableCell(String name) {
		for (int i = variableStack.size() - 1; i >= 0; i--) {
			Variable var = variableStack.elementAt(i).get(name);
			if(var != null)
				return var.cell;
		}

		throw Err.normal("Variable \"" + name + "\" doesn't exist.");
	}

	public Variable getVariable(String name) {
		for (int i = variableStack.size() - 1; i >= 0; i--) {
			Variable var = variableStack.elementAt(i).get(name);
			if(var != null)
				return var;
		}

		throw Err.normal("Variable \"" + name + "\" doesn't exist.");
	}

	public int getVariableCellNoExc(String name) {
		for (int i = variableStack.size() - 1; i >= 0; i--) {
			Variable var = variableStack.elementAt(i).get(name);
			if(var != null)
				return var.cell;
		}

		return -1;
	}

	public Array getArray(String name) {
		for (int i = arrayStack.size() - 1; i >= 0; i--) {
			Array var = arrayStack.elementAt(i).get(name);
			if(var != null)
				return var;
		}
		throw Err.normal("Array \"" + name + "\" doesn't exist.");
	}

	public void next() {
		variableStack.add(new HashMap<>());
		arrayStack.add(new HashMap<>());
	}

	//@f:off
	public final int IF_TEMP1 = switch(thread) {case 0->IGB_MA.IF_TEMP1_THREAD0; case 1->IGB_MA.IF_TEMP1_THREAD1; default -> -1;};
	public final int IF_TEMP2 = switch(thread) {case 0->IGB_MA.IF_TEMP2_THREAD0; case 1->IGB_MA.IF_TEMP1_THREAD1; default -> -1;};

	public final int EQ_TEMP1 = switch(thread) {case 0->IGB_MA.IF_TEMP1_THREAD0; case 1->IGB_MA.IF_TEMP1_THREAD1; default -> -1;};
	public boolean isEQ_TEMP1_used = false;
	public final int EQ_TEMP2 = switch(thread) {case 0->IGB_MA.IF_TEMP2_THREAD0; case 1->IGB_MA.IF_TEMP2_THREAD1; default -> -1;};
	public boolean isEQ_TEMP2_used = false;
	public final int TEMP1 = switch(thread) {case 0->IGB_MA.IF_TEMP3_THREAD0; case 1->IGB_MA.IF_TEMP3_THREAD1; default -> -1;};

	public static double solveFinalEq(String eq, HashMap<String, Double> finalVars) {
		// net.objecthunter.exp4j
		// https://www.objecthunter.net/exp4j/
		// https://github.com/fasseg/exp4j
		try {
		Expression e = new ExpressionBuilder(eq)
				.variables(finalVars.keySet())
				.build()
				.setVariables(finalVars);
		return e.evaluate();
		} catch(Exception e) {
			throw Err.normal("Syntax Error: \"" + eq + "\".", e);
		}
	}

	public double solveFinalEq(String eq) { return solveFinalEq(eq, finalVars); }


	private final Map<String, Double> DEFAULT_FINAL_VARIABLES = Map.ofEntries(
            new AbstractMap.SimpleEntry<>("rgb_white",		IGB_CL1.getMCRGBValueD(249, 255, 255)),
            new AbstractMap.SimpleEntry<>("rgb_yellow", 	IGB_CL1.getMCRGBValueD(255, 216, 61)),
            new AbstractMap.SimpleEntry<>("rgb_orange", 	IGB_CL1.getMCRGBValueD(249, 128, 29)),
            new AbstractMap.SimpleEntry<>("rgb_red", 		IGB_CL1.getMCRGBValueD(176, 46, 38)),
            new AbstractMap.SimpleEntry<>("rgb_magenta", 	IGB_CL1.getMCRGBValueD(198, 79, 189)),
            new AbstractMap.SimpleEntry<>("rgb_purple", 	IGB_CL1.getMCRGBValueD(137, 50, 183)),
            new AbstractMap.SimpleEntry<>("rgb_blue", 		IGB_CL1.getMCRGBValueD(60, 68, 169)),
            new AbstractMap.SimpleEntry<>("rgb_lightBlue", 	IGB_CL1.getMCRGBValueD(58, 179, 218)),
            new AbstractMap.SimpleEntry<>("rgb_lime", 		IGB_CL1.getMCRGBValueD(128, 199, 31)),
            new AbstractMap.SimpleEntry<>("rgb_green",		IGB_CL1.getMCRGBValueD(93, 124, 21)),
            new AbstractMap.SimpleEntry<>("rgb_brown", 		IGB_CL1.getMCRGBValueD(130, 84, 50)),
            new AbstractMap.SimpleEntry<>("rgb_cyan", 		IGB_CL1.getMCRGBValueD(22, 156, 157)),
            new AbstractMap.SimpleEntry<>("rgb_lightGray",  IGB_CL1.getMCRGBValueD(156, 157, 151)),
            new AbstractMap.SimpleEntry<>("rgb_pink", 		IGB_CL1.getMCRGBValueD(172, 81, 114)),
            new AbstractMap.SimpleEntry<>("rgb_gray", 		IGB_CL1.getMCRGBValueD(71, 79, 82)),
            new AbstractMap.SimpleEntry<>("rgb_black", 		IGB_CL1.getMCRGBValueD(29, 28, 33)) );


	public HashMap<String, Double> getDefaultFinalVars() { return new HashMap<>(DEFAULT_FINAL_VARIABLES); }

	
	private final Map<String, Variable> DEFAULT_VARIABLES = Map.ofEntries(
            new AbstractMap.SimpleEntry<>("keyboard",	new Variable(IGB_MA.KEYBOARD_INPUT)),
            new AbstractMap.SimpleEntry<>("width",		new Variable(IGB_MA.SCREEN_WIDTH)),
            new AbstractMap.SimpleEntry<>("height",		new Variable(IGB_MA.SCREEN_HEIGHT)),
            new AbstractMap.SimpleEntry<>("stype",		new Variable(IGB_MA.SCREEN_TYPE, str -> {
    			String str1 = str.toLowerCase().strip();
    			if(str1.equals("rgb") || str1.equals("1"))
    				return Utils.listOf(Init(1, IGB_MA.SCREEN_TYPE), Device_ScreenUpdate());
    			if(str1.equals("16c") || str1.equals("0"))
    				return Utils.listOf(Init(0, IGB_MA.SCREEN_TYPE), Device_ScreenUpdate());
    			throw Err.normal("Invalid value: \"" + str + "\" screenType can be only \"rgb\", \"16c\", \"0\" or \"1\".");
    		}))
            );
	
	private Stack<Map<String, Variable>> getDefaultVariables() {
		Stack<Map<String, Variable>> stack = new Stack<>();
		stack.add(new HashMap<>(DEFAULT_VARIABLES));
		return stack;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(IGB_CL2.getTabs() + "RAM: {\n");
		IGB_CL2.toStringTabs++;
		sb.append(
				IGB_CL2.getTabs()+"ramcell: " + ramcell + "\n" +
				IGB_CL2.getTabs()+"ramlimit: " + allocationArray.length + "\n" +
				IGB_CL2.getTabs()+"thread: " + thread + "\n" +
				finalVariablesToString()+
				variableStackToString()+
				arrayStackToString()+
				allocationArrayToString()
				);
		
		IGB_CL2.toStringTabs--;
		sb.append(IGB_CL2.getTabs() + "}");
		return sb.toString();
	}
	
	public String finalVariablesToString() {	
		StringBuilder sb = new StringBuilder(IGB_CL2.getTabs()+"final variables: {");
		IGB_CL2.toStringTabs++;
		
		boolean newlineAdded = false;
		
		var defFinalVarSet = DEFAULT_FINAL_VARIABLES.entrySet();
		for(var entry : finalVars.entrySet()) {
			if(defFinalVarSet.contains(entry)) 
				continue;
			if(!newlineAdded) { 
				sb.append("\n"); 
				newlineAdded = true;
			}
			sb.append(IGB_CL2.getTabs()+entry.getKey()+" = "+entry.getValue()+"\n");
		}
		
		IGB_CL2.toStringTabs--;
		if(newlineAdded) 
			sb.append(IGB_CL2.getTabs());
		sb.append("}\n");
		return sb.toString();
	}
	
	public String variableStackToString() {
		StringBuilder sb = new StringBuilder(IGB_CL2.getTabs()+"variable stack: {");
		
		int starting = IGB_CL2.toStringTabs;
		IGB_CL2.toStringTabs--;
		boolean newlineAdded = false;
		
		var defVarSet = DEFAULT_VARIABLES.entrySet();
		for(Map<String, Variable> map : variableStack) {
			IGB_CL2.toStringTabs++;
			for(var entry : map.entrySet()) {
				if(defVarSet.contains(entry)) 
					continue;
				if(!newlineAdded) { 
					sb.append("\n"); 
					newlineAdded = true;
				}
				sb.append(IGB_CL2.getTabs() + entry.getKey() + "|" + entry.getValue() + "|\n");
			}
			
		}
		IGB_CL2.toStringTabs = starting;
		if(newlineAdded) 
			sb.append(IGB_CL2.getTabs());
		sb.append("}\n");
		return sb.toString();
	}
	
	public String arrayStackToString() {
		StringBuilder sb = new StringBuilder(IGB_CL2.getTabs()+"array stack: {");
		
		int starting = IGB_CL2.toStringTabs;
		
		boolean newlineAdded = false;
		
		for(Map<String, Array> map : arrayStack) {
			IGB_CL2.toStringTabs++;
			for(var entry : map.entrySet()) {
				if(!newlineAdded) { 
					sb.append('\n'); 
					newlineAdded = true;
				}
				sb.append(IGB_CL2.getTabs() + entry.getKey() + entry.getValue().toString() + "\n");
			}
		}
		IGB_CL2.toStringTabs = starting;
		if(newlineAdded) 
			sb.append(IGB_CL2.getTabs());
		sb.append("}\n");
		return sb.toString();
	}
	
	public String allocationArrayToString() {
		StringBuilder sb = new StringBuilder(IGB_CL2.getTabs() + "allocation array (" + ramcell + " - " + (ramcell + allocationArray.length) + "): {");
		IGB_CL2.toStringTabs++;
		boolean newlineAdded = false;
		for(int i = 0, cell = ramcell; i < allocationArray.length; i++, cell++) {
			char c = allocationArray[i] ? '#' : 'o';
			if(i % 10 == 0) {
				if(!newlineAdded) { 
					sb.append("\n"); 
					newlineAdded = true;
				}
				String cellStr = cell + "";
				final int spacesToAdd = 6 - cellStr.length();
				
				sb.append(IGB_CL2.getTabs() + cell + ":");
				for(int h=0; h<spacesToAdd; h++) 
					sb.append(' ');
			}
			sb.append(c);
			if(i % 10 == 9) {
				sb.append('\n');
			}
		}
		
		IGB_CL2.toStringTabs--;
		if(newlineAdded) 
			sb.append(IGB_CL2.getTabs());
		sb.append("}\n");
		return sb.toString();
	}
	
	//@f:on

}
