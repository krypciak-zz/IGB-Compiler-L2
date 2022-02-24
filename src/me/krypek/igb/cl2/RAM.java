package me.krypek.igb.cl2;

import static me.krypek.igb.cl1.Instruction.Init;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import me.krypek.igb.cl1.IGB_MA;
import me.krypek.igb.cl2.datatypes.Array;
import me.krypek.igb.cl2.datatypes.Variable;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

public class RAM {
	static final String[] illegalCharacters = { "{", "}", "(", ")", "[", "]", ";", "$", "=", "|" };
	static final Set<String> illegalNames = Set.of("else");

	private final boolean[] allocationArray;

	HashMap<String, Double> finalVars;

	private final Stack<Map<String, Variable>> variableStack;
	private final Stack<Map<String, Array>> arrayStack;

	private final int allocStart;
	private int thread;

	public RAM(int ramSize, int allocStart, int thread) {
		this.thread = thread;
		this.allocStart = allocStart;
		finalVars = new HashMap<>();
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
		newVar("screenWidth", new Variable(IGB_MA.SCREEN_WIDTH));
		newVar("screenHeight", new Variable(IGB_MA.SCREEN_HEIGHT));
		newVar("screenType", new Variable(IGB_MA.SCREEN_TYPE, str -> {
			String str1 = str.toLowerCase();
			if(str1.equals("rgb"))
				return Init(1, IGB_MA.SCREEN_TYPE);
			if(str1.equals("16c"))
				return Init(0, IGB_MA.SCREEN_TYPE);
			throw new IGB_CL2_Exception("Invalid value: \"" + str + "\" screenType can be only \"rgb\", \"16c\", \"0\" or \"1\".");
		}));
	}

	private int allocateSpace(int size) {
		for1: for (int i = 0; i < allocationArray.length; i++) {
			final int _i = size + i;
			if(_i >= allocationArray.length)
				throw new IGB_CL2_Exception("Ran out of space in RAM!");
			final int startI = i;
			for (; i < _i; i++)
				if(allocationArray[i])
					continue for1;

			for (int h = startI; h < startI + size; h++)
				allocationArray[h] = true;

			return startI + allocStart;
		}

		throw new IGB_CL2_Exception("Ran out of space in RAM!");
	}

	public int checkName(String name) {
		int val = -1;
		if(name.endsWith("|")) {
			String[] split1 = name.split("\\|");
			if(split1.length != 2)
				throw new IGB_CL2_Exception("Variable cell syntax error.");
			val = (int) solveFinalEq(split1[1]);
			if(val < 0)
				throw new IGB_CL2_Exception("Variable cell cannot be negative.");
		}
		if(finalVars.containsKey(name))
			throw new IGB_CL2_Exception("Cannot create a variable that is named the same as a final variable.");

		if(illegalNames.contains(name))
			throw new IGB_CL2_Exception("Illegal variable name: \"" + name + "\".");

		for (String c : illegalCharacters)
			if(name.contains(c) && (!c.equals("" + '|') || val == -1))
				throw new IGB_CL2_Exception("Variable name \"" + name + "\" contains illegal character: \"" + c + "\".");

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

	public int getVariable(String name) {
		for (int i = variableStack.size() - 1; i >= 0; i--) {
			Variable var = variableStack.elementAt(i).get(name);
			if(var != null)
				return var.cell;
		}

		throw new IGB_CL2_Exception("Variable \"" + name + "\" doesn't exist.");
	}

	public int getVariableNoExc(String name) {
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
		throw new IGB_CL2_Exception("Variable \"" + name + "\" doesn't exist.");
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
			throw new IGB_CL2_Exception("Syntax error: \"" + eq + "\".", e);
		}
	}

	public double solveFinalEq(String eq) { return solveFinalEq(eq, finalVars); }

}
