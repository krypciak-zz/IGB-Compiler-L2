package me.krypek.igb.cl2;

import static me.krypek.igb.cl1.Instruction.Device_ScreenUpdate;
import static me.krypek.igb.cl1.Instruction.Init;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import me.krypek.igb.cl1.IGB_MA;
import me.krypek.igb.cl2.IGB_CL2_Exception.Err;
import me.krypek.igb.cl2.datatypes.Array;
import me.krypek.igb.cl2.datatypes.Variable;
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

	private final int allocStart;
	private int thread;

	public RAM(int ramSize, int allocStart, int thread) {
		this.thread = thread;
		this.allocStart = allocStart;
		finalVars = getDefaultFinalVars();
		allocationArray = new boolean[ramSize];
		variableStack = new Stack<>();
		arrayStack = new Stack<>();
		next();
		addCompilerVariables();
	}

	@Override
	public String toString() { return "RAM: {\n  " + variableStack + "\n  " + arrayStack + "\n  " + finalVars + "\n}"; }

	public void setFinalVariables(HashMap<String, Double> finalVars) { this.finalVars = finalVars; }

	private void addCompilerVariables() {
		newVar("keyboard", new Variable(IGB_MA.KEYBOARD_INPUT));
		newVar("width", new Variable(IGB_MA.SCREEN_WIDTH));
		newVar("height", new Variable(IGB_MA.SCREEN_HEIGHT));
		newVar("stype", new Variable(IGB_MA.SCREEN_TYPE, str -> {
			String str1 = str.toLowerCase().strip();
			if(str1.equals("rgb") || str1.equals("1"))
				return Utils.listOf(Init(1, IGB_MA.SCREEN_TYPE), Device_ScreenUpdate());
			if(str1.equals("16c") || str1.equals("0"))
				return Utils.listOf(Init(0, IGB_MA.SCREEN_TYPE), Device_ScreenUpdate());
			throw Err.normal("Invalid value: \"" + str + "\" screenType can be only \"rgb\", \"16c\", \"0\" or \"1\".");
		}));
	}

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

			return startI + allocStart;
		}

		throw Err.normal("Ran out of space in RAM!");
	}

	public int checkName(String name) {
		int val = -1;
		if(name.endsWith("|")) {
			String[] split1 = name.split("\\|");
			if(split1.length != 2)
				throw Err.normal("Variable cell syntax Error.");
			val = (int) solveFinalEq(split1[1]);
			if(val < 0)
				throw Err.normal("Variable cell cannot be negative.");
		}
		if(finalVars.containsKey(name))
			throw Err.normal("Cannot create a variable that is named the same as a final variable.");

		if(illegalNames.contains(name))
			throw Err.normal("Illegal variable name: \"" + name + "\".");

		for (String c : illegalCharacters)
			if(name.contains(c) && (!c.equals("" + '|') || val == -1))
				throw Err.normal("Variable name \"" + name + "\" contains illegal character: \"" + c + "\".");

		return val;
	}

	public int[] reserve(int amount) {
		int[] arr = new int[amount];
		for (int i = 0; i < amount; i++)
			arr[i] = allocateSpace(1);
		return arr;
	}

	public int newVar(String name) {
		int cell1 = checkName(name);
		final int cell;
		if(cell1 == -1)
			cell = allocateSpace(1);
		else {
			name = name.substring(0, name.indexOf('|'));
			cell = cell1;
		}

		variableStack.peek().put(name, new Variable(cell));
		return cell;
	}

	public int newVar(String name, Variable var) {
		checkName(name);
		variableStack.peek().put(name, var);
		return var.cell;
	}

	public int newArray(String name, int[] size) {
		int totalSize = calcArraySize(size);

		int cell1 = checkName(name);
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

	public void pop() {
		Map<String, Variable> t1 = variableStack.pop();
		for (Variable var : t1.values()) {
			int cell = var.cell - allocStart;
			if(cell < 0 || cell >= allocationArray.length)
				continue;
			allocationArray[cell] = false;
		}
		Map<String, Array> t2 = arrayStack.pop();
		for (Array array : t2.values()) {
			int from = array.cell - allocStart;
			int to = from + array.totalSize;

			if(from < 0 || from >= allocationArray.length || to < 0 || to >= allocationArray.length)
				continue;

			for (int i = from; i < to; i++)
				allocationArray[i] = false;
		}
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
		throw Err.normal("Variable \"" + name + "\" doesn't exist.");
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


	private static double getMCRGBValue(int r, int g, int b) { return (r << 16) + (g << 8) + b; }

	//@f:off
	private final Map<String, Double> color_map = Map.ofEntries(
            new AbstractMap.SimpleEntry<>("rgb_white",		getMCRGBValue(249, 255, 255)),
            new AbstractMap.SimpleEntry<>("rgb_yellow", 	getMCRGBValue(255, 216, 61)),
            new AbstractMap.SimpleEntry<>("rgb_orange", 	getMCRGBValue(249, 128, 29)),
            new AbstractMap.SimpleEntry<>("rgb_red", 		getMCRGBValue(176, 46, 38)),
            new AbstractMap.SimpleEntry<>("rgb_magenta", 	getMCRGBValue(198, 79, 189)),
            new AbstractMap.SimpleEntry<>("rgb_purple", 	getMCRGBValue(137, 50, 183)),
            new AbstractMap.SimpleEntry<>("rgb_blue", 		getMCRGBValue(60, 68, 169)),
            new AbstractMap.SimpleEntry<>("rgb_lightBlue", 	getMCRGBValue(58, 179, 218)),
            new AbstractMap.SimpleEntry<>("rgb_lime", 		getMCRGBValue(128, 199, 31)),
            new AbstractMap.SimpleEntry<>("rgb_green",		getMCRGBValue(93, 124, 21)),
            new AbstractMap.SimpleEntry<>("rgb_brown", 		getMCRGBValue(130, 84, 50)),
            new AbstractMap.SimpleEntry<>("rgb_cyan", 		getMCRGBValue(22, 156, 157)),
            new AbstractMap.SimpleEntry<>("rgb_lightGray", 	getMCRGBValue(156, 157, 151)),
            new AbstractMap.SimpleEntry<>("rgb_pink", 		getMCRGBValue(172, 81, 114)),
            new AbstractMap.SimpleEntry<>("rgb_gray", 		getMCRGBValue(71, 79, 82)),
            new AbstractMap.SimpleEntry<>("rgb_black", 		getMCRGBValue(29, 28, 33)) );
    //@f:on

	public HashMap<String, Double> getDefaultFinalVars() {
		HashMap<String, Double> fvars = new HashMap<>(color_map);
		return fvars;
	}

}
