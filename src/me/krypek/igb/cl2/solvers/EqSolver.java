package me.krypek.igb.cl2.solvers;

import static me.krypek.igb.cl1.Instruction.Copy;
import static me.krypek.igb.cl1.Instruction.Init;
import static me.krypek.igb.cl1.Instruction.Math;

import java.util.ArrayList;
import java.util.Set;

import me.krypek.igb.cl1.IGB_MA;
import me.krypek.igb.cl1.Instruction;
import me.krypek.igb.cl2.Functions;
import me.krypek.igb.cl2.IGB_CL2_Exception;
import me.krypek.igb.cl2.RAM;
import me.krypek.igb.cl2.datatypes.Array;
import me.krypek.igb.cl2.datatypes.ArrayAccess;
import me.krypek.igb.cl2.datatypes.Equation;
import me.krypek.igb.cl2.datatypes.Field;
import me.krypek.igb.cl2.datatypes.Function;
import me.krypek.igb.cl2.datatypes.FunctionCall;
import me.krypek.utils.Pair;
import me.krypek.utils.TripleObject;
import me.krypek.utils.Utils;

public class EqSolver {
	final static Set<Character> operators = Set.of('+', '-', '*', '/', '%');

	public EqSolver(RAM ram, Functions funcs) {
		this.ram = ram;
		this.funcs = funcs;
		temp1 = ram.EQ_TEMP1;
		temp2 = ram.EQ_TEMP2;
	}

	public double solveFinalEq(String eq) { return ram.solveFinalEq(eq); }

	public Pair<ArrayList<Instruction>, Double> getNumCell(Field f,  int outCell) {
		if(f.isVal()) return new Pair<>(null, f.value);
		if(f.isVar()) return new Pair<>(new ArrayList<>(), (double)f.cell);
		Pair<Integer, ArrayList<Instruction>> pair = getInstructionsFromField(f, outCell);
		return new Pair<>(pair.getSecond(), (double) pair.getFirst());
	}
	
	public TripleObject<Double, Integer, ArrayList<Instruction>> solve(String eqS) {
		tempCell1 = IGB_MA.CHARLIB_TEMP_START;
		Equation eq = getEqFromString(eqS, false);
		if(eq.fields.length == 1 && eq.fields[0].isVal())
			return new TripleObject<>(eq.fields[0].value, -1, new ArrayList<>());

		var pair = getInstructionsFromField(new Field(eq), -1);
		return new TripleObject<>(null, pair.getFirst(), pair.getSecond());
	}

	public ArrayList<Instruction> solve(final String eqS, int outCell) {
		tempCell1 = IGB_MA.CHARLIB_TEMP_START;
		// System.out.print(eqS);
		Equation eq = getEqFromString(eqS, false);

		// System.out.println(eq + " -> " + outCell + " ->");
		// for (Instruction inst : list)
		// System.out.println(inst);
		// System.out.println();
		return getInstructionListFromEq(eq, outCell);
	}

	private final EqSolver eqs = this;
	private final RAM ram;
	private final Functions funcs;

	private final int temp1;
	private final int temp2;
	private int tempCell1;

	private ArrayList<Instruction> getInstructionListFromEq(Equation eq, int outCell) {
		Field[] fields = eq.fields;
		char[] opes = eq.operators;
		ArrayList<Instruction> list = new ArrayList<>();

		if(fields.length == 1)
			return getInstructionsFromField(fields[0], outCell).getSecond();

		int tempCell = outCell;
		for (int i = 0; i < fields.length - 1; i++) {
			final int i_ = i + 1;
			Field f = fields[i];
			Field f1 = i == opes.length ? null : fields[i + 1];
			char ope = i == opes.length ? '?' : opes[i];
			char nextOpe = i_ >= opes.length ? '?' : opes[i_];

			if(isAddi(ope) && nextOpe != '?' && !isAddi(nextOpe)) {
				final int tCell1;
				if(ram.isEQ_TEMP1_used)
					tCell1 = tempCell1++;
				else {
					tCell1 = temp1;
					ram.isEQ_TEMP1_used = true;
				}
				final int tCell2;
				if(ram.isEQ_TEMP2_used)
					tCell2 = tempCell1++;
				else {
					tCell2 = temp2;
					ram.isEQ_TEMP2_used = true;
				}

				var numcell2 = getNumCell(f1, list, tCell1);
				var numcell3 = getNumCell(fields[i + 2], list, tCell2);
				boolean c2 = numcell2.getFirst(), c3 = numcell3.getFirst();
				double v2 = numcell2.getSecond(), v3 = numcell3.getSecond();
				if(!c2 && !c3) {
					double sum = getVal(v2, v3, nextOpe);
					list.add(Init(sum, tempCell));
				} else if(c2)
					list.add(Math(ope, (int) v2, c3, v3, tempCell));
				else
					list.addAll(revertOperation(v2, (int) v3, nextOpe, tempCell));
				var numcell1 = getNumCell(f, list, tCell1);
				list.add(Math(ope, tempCell, numcell1.getFirst(), numcell1.getSecond(), tempCell));

				if(tCell1 == temp1)
					ram.isEQ_TEMP1_used = false;
				if(tCell2 == temp2)
					ram.isEQ_TEMP2_used = false;

				i++;
			} else if(i == 0) {
				final int tCell1;
				if(ram.isEQ_TEMP1_used)
					tCell1 = tempCell1++;
				else {
					tCell1 = temp1;
					ram.isEQ_TEMP1_used = true;
				}
				final int tCell2;
				if(ram.isEQ_TEMP2_used)
					tCell2 = tempCell1++;
				else {
					tCell2 = temp2;
					ram.isEQ_TEMP2_used = true;
				}

				var numcell1 = getNumCell(f, list, tCell1);
				var numcell2 = getNumCell(f1, list, tCell2);
				boolean c1 = numcell1.getFirst(), c2 = numcell2.getFirst();
				double v1 = numcell1.getSecond(), v2 = numcell2.getSecond();
				if(!c1 && !c2) {
					double sum = getVal(v1, v2, ope);
					list.add(Init(sum, tempCell));
				} else if(c1)
					list.add(Math(ope, (int) v1, c2, v2, tempCell));
				else
					list.addAll(revertOperation(v1, (int) v2, ope, tempCell));

				if(tCell1 == temp1)
					ram.isEQ_TEMP1_used = false;
				if(tCell2 == temp2)
					ram.isEQ_TEMP2_used = false;
			} else {
				final int tCell2;
				if(ram.isEQ_TEMP2_used)
					tCell2 = tempCell1++;
				else {
					tCell2 = temp2;
					ram.isEQ_TEMP2_used = true;
				}
				var numcell2 = getNumCell(f1, list, tCell2);
				list.add(Math(ope, tempCell, numcell2.getFirst(), numcell2.getSecond(), tempCell));

				if(tCell2 == temp2)
					ram.isEQ_TEMP2_used = false;
			}
			if(tempCell1 >= IGB_MA.CHARLIB_CHAR)
				throw new IGB_CL2_Exception("This equation is too long.");
		}

		return list;

	}

	private ArrayList<Instruction> revertOperation(double v1, int v2, char ope, int outCell) {
		ArrayList<Instruction> list = new ArrayList<>();
		switch (ope) {
		case '+', '*' -> {
			list.add(Math(ope, v2, false, v1, outCell));
		}
		case '-', '/', '%' -> {
			list.add(Init(v1, outCell));
			list.add(Math(ope, outCell, true, v2, outCell));
		}
		default -> throw new IllegalArgumentException("Unexpected value: " + ope);
		}
		return list;
	}

	private static double getVal(double v1, double v2, char ope) {
		return switch (ope) {
		case '+' -> v1 + v2;
		case '-' -> v1 - v2;
		case '*' -> v1 * v2;
		case '/' -> v1 / v2;
		case '%' -> v1 % v2;
		default -> throw new IllegalArgumentException("Unexpected value: " + ope);
		};
	}

	private Pair<Boolean, Double> getNumCell(Field f, ArrayList<Instruction> list, int temp) {
		if(f.isVal())
			return new Pair<>(false, f.value);
		if(f.isVar())
			return new Pair<>(true, (double) f.cell);
		var pair = getInstructionsFromField(f, temp);
		list.addAll(pair.getSecond());
		return new Pair<>(true, (double) pair.getFirst());
	}

	private static boolean isAddi(char ope) { return ope == '+' || ope == '-'; }

	public Pair<Integer, ArrayList<Instruction>> getInstructionsFromField(Field f) { return getInstructionsFromField(f, -1); }

	public Pair<Integer, ArrayList<Instruction>> getInstructionsFromField(Field f, int directCell) {
		ArrayList<Instruction> list = new ArrayList<>();
		switch (f.fieldType) {
		case Val -> {
			final int outCell;
			if(directCell == -1)
				outCell = IGB_MA.TEMP_CELL_1;
			else
				outCell = directCell;
			return new Pair<>(outCell, Utils.listOf(Init(f.value, outCell)));
		}
		case Var -> {
			if(f.cell == directCell)
				return new Pair<>(directCell, new ArrayList<>());

			if(directCell == -1)
				return new Pair<>(f.cell, new ArrayList<>());
			return new Pair<>(directCell, Utils.listOf(Copy(f.cell, directCell)));
		}
		case Array -> {
			ArrayAccess aa = f.arrAccess;
			Field[] args = aa.dims;
			Array arr = aa.array;
			final int outCell;
			if(directCell == -1)
				outCell = IGB_MA.TEMP_CELL_1;
			else
				outCell = directCell;

			return arr.getAccess(eqs, outCell, args);
		}
		case Eq -> {
			final int cell1;
			if(directCell == -1) {
				ArrayList<Instruction> list1 = getInstructionListFromEq(f.eq, ram.EQ_TEMP1);
				return new Pair<>(ram.EQ_TEMP1, list1);
			}
			cell1 = directCell;
			list.addAll(getInstructionListFromEq(f.eq, cell1));
			return new Pair<>(cell1, list);
		}
		case Func -> {
			FunctionCall fc = f.funcCall;
			Function func = fc.func;
			Field[] args = fc.args;
			final int outCell;
			if(directCell == -1) {
				outCell = IGB_MA.FUNC_RETURN;
				list.addAll(func.getCall(eqs, args));
			} else {
				list.addAll(func.getCall(eqs, args, directCell));
				outCell = directCell;
			}
			return new Pair<>(outCell, list);
		}
		}
		return null;
	}

	private Equation getEqFromString(final String str, boolean epicFail) {
		ArrayList<Character> operatorList = new ArrayList<>();
		ArrayList<String> fieldStringList = new ArrayList<>();
		{
			int bracket = 0;
			char[] charA = str.toCharArray();
			StringBuilder sb = new StringBuilder(32);
			for (char c : charA) {
				if(c == '(')
					bracket++;
				else if(c == ')')
					bracket--;

				if(operators.contains(c) && bracket == 0) {
					operatorList.add(c);
					fieldStringList.add(sb.toString());
					sb = new StringBuilder(32);
				} else if(!Character.isWhitespace(c))
					sb.append(c);
			}
			if(!sb.isEmpty())
				fieldStringList.add(sb.toString());
		}

		char[] operators = operatorList.stream().map(String::valueOf).collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString()
				.toCharArray();

		Field[] fields = new Field[fieldStringList.size()];

		for (int i = 0; i < fieldStringList.size(); i++)
			fields[i] = stringToField(fieldStringList.get(i), epicFail);

		return new Equation(operators, fields);
	}

	Field stringToField(final String str, boolean epicFail) {
		try {
			double val = ram.solveFinalEq(str);
			return new Field(val);
		} catch (Exception e) {}

		if(str.endsWith(")")) {
			if(str.startsWith("("))
				return new Field(getEqFromString(str.substring(1, str.length() - 1), epicFail));
			if(str.contains("(")) {
				int index = str.indexOf('(');
				String funcName = str.substring(0, index).stripTrailing();

				String[] args = Utils.getArrayElementsFromStringIgnoreBrackets(str.substring(index), '(', ')', ',');

				Field[] fa = stringArrayToFieldArray(args);

				return new Field(new FunctionCall(fa, funcs.getFunction(funcName, args.length)));
			}
			throw new IGB_CL2_Exception("Please enter a valid amount of brackets.");
		}

		if(str.endsWith("]") && str.contains("[")) {
			int index = str.indexOf('[');
			String arrName = str.substring(0, index).stripTrailing();

			String[] dimsS = Utils.getArrayElementsFromString(str, '[', ']', new IGB_CL2_Exception("Array syntax error"));

			Field[] fa = stringArrayToFieldArray(dimsS);

			Array arr = ram.getArray(arrName);
			if(arr == null)
				throw new IGB_CL2_Exception("Array: \"" + arrName + "\" doesn't exist.");

			if(fa.length != arr.size.length)
				throw new IGB_CL2_Exception("Array: \"" + arrName + "\" Expected " + arr.size.length + " dimensions, got " + fa.length + ".");

			return new Field(new ArrayAccess(arrName, arr, fa));
		}

		int cell = ram.getVariableCellNoExc(str);
		if(cell != -1)
			return new Field(cell);

		if(!epicFail)
			try {
				return new Field(getEqFromString(str, true));
			} catch (IGB_CL2_Exception e) {
				throw e;
			}

		throw new IGB_CL2_Exception("Unknown variable: \"" + str + "\".");
	}

	private Field[] stringArrayToFieldArray(String[] arr) {
		Field[] fa = new Field[arr.length];
		for (int i = 0; i < arr.length; i++)
			fa[i] = stringToField(arr[i], false);
		return fa;
	}

}
