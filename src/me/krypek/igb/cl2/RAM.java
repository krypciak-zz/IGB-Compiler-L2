package me.krypek.igb.cl2;

import static me.krypek.igb.cl1.Instruction.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import me.krypek.igb.cl1.IGB_MA;
import me.krypek.igb.cl1.Instruction;
import me.krypek.igb.cl2.EqSolver.Field;
import me.krypek.utils.Pair;
import me.krypek.utils.TripleObject;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

class RAM {
	static final String[] illegalCharacters = { "{", "}", "(", ")", "[", "]", ";", "$", "=", "else" };

	private final boolean[] allocationArray;

	HashMap<String, Double> finalVars;

	private final Stack<Map<String, Variable>> variableStack;
	private final Stack<Map<String, Array>> arrayStack;

	private int allocStart;
	private int thread;

	public RAM(int ramSize, int allocStart, int thread) {
		finalVars = new HashMap<>();
		allocationArray = new boolean[ramSize];
		variableStack = new Stack<>();
		arrayStack = new Stack<>();
		nextStack();
		addCompilerVariables();
	}

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

	private int allocateSpace(int size, boolean write) {
		for1: for (int i = 0; i < allocationArray.length; i++) {
			final int _i = size + i;
			for (; i < _i; i++)
				if(allocationArray[i])
					continue for1;
			for (int h = i; h < i + size; h++)
				allocationArray[h] = true;
			return i - allocStart;
		}

		throw new IGB_CL2_Exception("Ran out of space in RAM!");
	}

	void checkName(String name) {
		for (String c : illegalCharacters)
			if(name.contains(c))
				throw new IGB_CL2_Exception("Variable name \"" + name + "\" contains illegal character: \"" + c + "\".");
	}

	public int[] reserve(int amount) {
		int[] arr = new int[amount];
		for (int i = 0; i < amount; i++)
			arr[i] = allocateSpace(1, false);
		return arr;
	}

	public int newVar(String name) {
		checkName(name);
		int cell = allocateSpace(1, true);
		variableStack.peek().put(name, new Variable(cell));
		return cell;
	}

	public int newVar(String name, Variable var) {
		checkName(name);
		variableStack.peek().put(name, var);
		return var.cell;
	}

	public int newArray(String name, int[] size) {
		checkName(name);
		int totalSize = calcArraySize(size);
		int cell = allocateSpace(totalSize, true);
		arrayStack.peek().put(name, new Array(cell, size, totalSize));
		return cell;
	}

	public int newArray(String name, int[] size, int cell) {
		checkName(name);
		int totalSize = calcArraySize(size);
		arrayStack.peek().put(name, new Array(cell, size, totalSize));
		return cell;
	}

	public static int calcArraySize(int[] size) {
		int totalSize = 1;
		for (int element : size)
			totalSize *= element;

		return totalSize;
	}

	void popStack() {
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

	int getVariable(String name) {
		for (int i = variableStack.size() - 1; i >= 0; i--) {
			Variable var = variableStack.elementAt(i).get(name);
			if(var != null)
				return var.cell;
		}
		return -1;
		/// throw new IGB_CL2_Exception("Variable \"" + name + "\" doesn't exist.");
	}

	Array getArray(String name) {
		for (int i = arrayStack.size() - 1; i >= 0; i--) {
			Array var = arrayStack.elementAt(i).get(name);
			if(var != null)
				return var;
		}
		return null;
		/// throw new IGB_CL2_Exception("Variable \"" + name + "\" doesn't exist.");
	}

	void nextStack() {
		variableStack.add(new HashMap<>());
		arrayStack.add(new HashMap<>());
	}

	//@f:off
	final int IF_TEMP1 = switch(thread) {case 0->IGB_MA.IF_TEMP1_THREAD0; case 1->IGB_MA.IF_TEMP1_THREAD1; default -> -1;};
	final int IF_TEMP2 = switch(thread) {case 0->IGB_MA.IF_TEMP2_THREAD0; case 1->IGB_MA.IF_TEMP1_THREAD1; default -> -1;};
	final int EQ_TEMP1 = switch(thread) {case 0->IGB_MA.IF_TEMP1_THREAD0; case 1->IGB_MA.IF_TEMP1_THREAD1; default -> -1;};
	final int EQ_TEMP2 = switch(thread) {case 0->IGB_MA.IF_TEMP2_THREAD0; case 1->IGB_MA.IF_TEMP2_THREAD1; default -> -1;};


	public static double solveFinalEq(String eq, HashMap<String, Double> finalVars) {
		// net.objecthunter.exp4j
		// https://www.objecthunter.net/exp4j/
		// https://github.com/fasseg/exp4j
		Expression e = new ExpressionBuilder(eq)
				.variables(finalVars.keySet())
				.build()
				.setVariables(finalVars);
		double result = e.evaluate();
		return result;
	}

	public double solveFinalEq(String eq) {
		// net.objecthunter.exp4j
		// https://www.objecthunter.net/exp4j/
		// https://github.com/fasseg/exp4j
		Expression e = new ExpressionBuilder(eq)
				.variables(finalVars.keySet())
				.build()
				.setVariables(finalVars);
		double result = e.evaluate();
		return result;
	}
	//@f:on
}

class Variable {
	public final int cell;
	public VariableSetAction action;

	public Variable(int cell) { this.cell = cell; }

	public Variable(int cell, VariableSetAction setAction) {
		this.cell = cell;
		action = setAction;
	}

	public void setAction(String arg) {
		if(action == null)
			throw new IGB_CL2_Exception();
		action.set(arg);
	}

	interface VariableSetAction {
		Instruction set(String eq);
	}
}

class Array {
	public final int cell;
	public final int[] size;
	public final int totalSize;

	public Array(int cell, int[] size, int totalSize) {
		this.cell = cell;
		this.size = size;
		this.totalSize = totalSize;
	}

	public TripleObject<Boolean, Integer, ArrayList<Instruction>> getArrayCell(EqSolver eqs, Field[] dims, int outCell) {
		if(dims.length != size.length)
			throw new IGB_CL2_Exception("Expected " + size.length + " array dimensions, insted got " + dims.length + "\".");

		boolean isAllVal = true;
		int cell = 0;

		ArrayList<Pair<Integer, Integer>> cellList = new ArrayList<>();
		for (int i = dims.length - 1, x = 1; i >= 0; x *= size[i--]) {
			Field f = dims[i];
			if(!f.isVal()) {
				isAllVal = false;
				cellList.add(new Pair<>(i, x));
			} else {
				double val = f.value;
				if(val % 1 != 0)
					throw new IGB_CL2_Exception("Array dimension has to be an int, insted got: \"" + val + "\".");
				int val1 = (int) val;
				if(val1 >= size[i])
					throw new IGB_CL2_Exception("Index out of bounds: " + val1 + " out of " + size[i] + ".");

				cell += val1 * x;
			}
		}
		ArrayList<Instruction> list = new ArrayList<>();
		if(isAllVal)
			return new TripleObject<>(true, cell, list);

		final int len_ = dims.length - 1;
		if(cellList.size() == 1) {
			var pair = cellList.get(0);
			int i = pair.getFirst();
			int x = pair.getSecond();

			Field f = dims[i];
			if(cell == 0) {
				if(i == len_) {
					var pair1 = eqs.getInstructionsFromField(f, outCell);
					list.addAll(pair1.getSecond());
				} else {
					var pair1 = eqs.getInstructionsFromField(f);
					list.addAll(pair1.getSecond());
					list.add(Math("*", pair1.getFirst(), false, x, outCell));
				}
			} else if(i == len_) {
				var pair1 = eqs.getInstructionsFromField(f, outCell);
				list.addAll(pair1.getSecond());
				list.add(Add(outCell, false, cell, outCell));
			} else {
				var pair1 = eqs.getInstructionsFromField(f, -1);
				list.addAll(pair1.getSecond());
				list.add(Math("*", pair1.getFirst(), false, x, outCell));
				list.add(Add(outCell, false, cell, outCell));
			}
			return new TripleObject<>(false, outCell, list);
		}

		boolean set = false;
		boolean waitingForNext = false;
		for (int h = 0; h < cellList.size(); h++) {
			Pair<Integer, Integer> pair1 = cellList.get(h);
			int i = pair1.getFirst();
			int x = pair1.getSecond();
			Field f = dims[h];
			if(!f.isVal())
				if(set) {
					var pair2 = eqs.getInstructionsFromField(dims[h - 1]);
					int cell2 = pair2.getFirst();
					list.addAll(pair2.getSecond());
					list.add(Math("*", cell2, false, x, IGB_MA.TEMP_CELL_2));
					list.add(Add(outCell, true, IGB_MA.TEMP_CELL_2, outCell));

				} else {
					if(waitingForNext) {
						waitingForNext = false;
						set = true;

						var prevPair = eqs.getInstructionsFromField(dims[h - 1], IGB_MA.TEMP_CELL_3);
						list.addAll(prevPair.getSecond());

						var currPair = eqs.getInstructionsFromField(f, IGB_MA.TEMP_CELL_2);
						list.addAll(currPair.getSecond());
						list.add(Math("*", IGB_MA.TEMP_CELL_2, false, x, IGB_MA.TEMP_CELL_2));
						list.add(Add(IGB_MA.TEMP_CELL_2, true, IGB_MA.TEMP_CELL_3, outCell));
						continue;
					}

					if(cell == 0 && i == len_) {
						waitingForNext = true;
						continue;
					}
					var pair2 = eqs.getInstructionsFromField(f);
					int cell1 = pair2.getFirst();
					list.addAll(pair2.getSecond());
					if(cell == 0)
						list.add(Math("*", cell1, false, x, outCell));
					else if(i == len_)
						list.add(Add(cell1, false, cell, outCell));
					else {
						list.add(Math("*", cell1, false, x, outCell));
						list.add(Add(outCell, false, cell, outCell));
					}
					set = true;
				}
		}
		return new TripleObject<>(false, outCell, list);
	}

	public ArrayList<Instruction> getAccess(EqSolver eqs, Field[] dims, int outCell) {
		var obj = getArrayCell(eqs, dims, outCell);
		ArrayList<Instruction> list = obj.getValue3();
		if(obj.getValue1()) {
			list.add(Copy(cell, outCell));
			return list;
		}

		list.add(Math_CC(obj.getValue2(), outCell));
		return list;
	}

	public Pair<Integer, ArrayList<Instruction>> getAccess(EqSolver eqs, int tempCell, Field[] dims) {
		var obj = getArrayCell(eqs, dims, tempCell);
		ArrayList<Instruction> list = obj.getValue3();
		if(obj.getValue1())
			// list.add(Copy(cell, outCell));
			return new Pair<>(tempCell, list);

		list.add(Math_CC(obj.getValue2(), tempCell));
		return new Pair<>(tempCell, list);
	}

	public ArrayList<Instruction> getWrite(EqSolver eqs, Field[] dims, double value) {
		var obj = getArrayCell(eqs, dims, IGB_MA.TEMP_CELL_3);
		ArrayList<Instruction> list = obj.getValue3();
		list.add(Init(value, IGB_MA.TEMP_CELL_2));
		list.add(Math_CW(IGB_MA.TEMP_CELL_3, IGB_MA.TEMP_CELL_2));
		return list;
	}

	public ArrayList<Instruction> getWrite(EqSolver eqs, Field[] dims, int cell) {
		var obj = getArrayCell(eqs, dims, IGB_MA.TEMP_CELL_3);
		ArrayList<Instruction> list = obj.getValue3();
		list.add(Math_CW(IGB_MA.TEMP_CELL_3, cell));
		return list;
	}

}
