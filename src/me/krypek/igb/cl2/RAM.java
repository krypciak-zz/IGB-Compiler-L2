package me.krypek.igb.cl2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import me.krypek.igb.cl1.IGB_MA;
import me.krypek.igb.cl1.Instruction;
import me.krypek.igb.cl2.EqSolver.ConvField;
import me.krypek.igb.cl2.EqSolver.Field;
import me.krypek.utils.Pair;
import me.krypek.utils.Utils;

class RAM {
	static final String[] illegalCharacters = new String[] { "{", "}", "(", ")", "[", "]", ";", "$", "=", "else" };

	private boolean[] allocationArray;

	HashMap<String, Double> finalVars;

	private Stack<Map<String, Variable>> variableStack;
	private Stack<Map<String, Array>> arrayStack;

	private int allocStart;

	public RAM(int ramSize, int allocStart) {
		finalVars = new HashMap<>();
		allocationArray = new boolean[ramSize];
		variableStack = new Stack<>();
		arrayStack = new Stack<>();
		nextStack();
		addCompilerVariables();
	}

	private void addCompilerVariables() {
		newVar("keyboard", new Variable(IGB_MA.KEYBOARD_INPUT));
		newVar("screenWidth", new Variable(IGB_MA.SCREEN_WIDTH));
		newVar("screenHeight", new Variable(IGB_MA.SCREEN_HEIGHT));
		newVar("screenType", new Variable(IGB_MA.SCREEN_TYPE, (str) -> {
			String str1 = str.toLowerCase();
			if(str1.equals("rgb"))
				return Instruction.Init(1, IGB_MA.SCREEN_TYPE);
			else if(str1.equals("16c"))
				return Instruction.Init(0, IGB_MA.SCREEN_TYPE);
			else
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
		for (int i = 0; i < size.length; i++)
			totalSize *= size[i];

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

}

class Variable {
	public final int cell;
	public VariableSetAction action;

	public Variable(int cell) { this.cell = cell; }

	public Variable(int cell, VariableSetAction setAction) {
		this.cell = cell;
		this.action = setAction;
	}

	public void setAction(String arg) {
		if(action == null)
			throw new IGB_CL2_Exception();
		action.set(arg);
	}

	interface VariableSetAction {
		public abstract Instruction set(String eq);
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

	public ArrayList<Instruction> getAccess(Field[] args, int outCell) {
		boolean isAllVal = true;
		int cell = 0;

		ArrayList<Pair<Integer, Integer>> cellList = new ArrayList<>();
		for (int i = args.length - 1, x = 1; i >= 0; i--, x *= size[i]) {
			Field f = args[i];
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
		if(isAllVal) {
			list.add(Instruction.Copy(cell, outCell));
			return list;
		} else {
			boolean inited = cell == 0;
			boolean set = false;

			if(cellList.size() == 1) {
				var pair = cellList.get(0);
				int i = pair.getFirst();
				int x = pair.getSecond();
				Field f = args[i];
				if(cell == 0) {
					if(i == args.length - 1) {
						var pair1 = ConvField.getInstructionsFromField(f, outCell);
						list.addAll(pair1.getSecond());
						list.add(Instruction.Math_CC(outCell, outCell));
					} else {
						var pair1 = ConvField.getInstructionsFromField(f, -1);
						list.addAll(pair1.getSecond());
						list.add(Instruction.Math("*", pair1.getFirst(), false, x, outCell));
						list.add(Instruction.Math_CC(outCell, outCell));
					}
				} else {
					if(i == args.length - 1) {
						var pair1 = ConvField.getInstructionsFromField(f, IGB_MA.TEMP_CELL_2);
						list.addAll(pair1.getSecond());
						list.add(Instruction.Math_CC(outCell, outCell));
					} else {
						var pair1 = ConvField.getInstructionsFromField(f, -1);
						list.addAll(pair1.getSecond());
						list.add(Instruction.Math("*", pair1.getFirst(), false, x, outCell));
						list.add(Instruction.Math_CC(outCell, outCell));
					}
					
					
				}
				return list;
			}

			for (int i = 0; i < cellList.size(); i++) {
				Pair<Integer, Integer> pair1 = cellList.get(i);
				Field f = args[i];
				if(!f.isVal()) {
					if(inited) {
						Pair<Integer, ArrayList<Instruction>> pair = ConvField.getInstructionsFromField(f, -1);
						int cell1 = pair.getFirst();
						ArrayList<Instruction> list1 = pair.getSecond();
						list.addAll(list1);
						if(set) {
							list.add(Instruction.Add(IGB_MA.TEMP_CELL_2, true, cell1, IGB_MA.TEMP_CELL_2));
						} else {
							list.add(Instruction.Add(cell1, false, cell, IGB_MA.TEMP_CELL_2));
							set = true;
						}
						return list;
					}

				}
			}
		}
	}
}
