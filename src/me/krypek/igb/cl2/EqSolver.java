package me.krypek.igb.cl2;

import static me.krypek.igb.cl2.EqSolver.Field.FieldType.Array;
import static me.krypek.igb.cl2.EqSolver.Field.FieldType.Eq;
import static me.krypek.igb.cl2.EqSolver.Field.FieldType.Func;
import static me.krypek.igb.cl2.EqSolver.Field.FieldType.Val;
import static me.krypek.igb.cl2.EqSolver.Field.FieldType.Var;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import me.krypek.igb.cl1.IGB_MA;
import me.krypek.igb.cl1.Instruction;
import me.krypek.igb.cl2.EqSolver.Field.FieldType;
import me.krypek.utils.Pair;
import me.krypek.utils.Utils;

class EqSolver {
	private final static Set<Character> operators = Set.of('+', '-', '*', '/', '%');

	public ArrayList<Instruction> solve(final String eqS, RAM ram, Functions funcs) {
		this.ram = ram;
		this.funcs = funcs;
		tempCell = IGB_MA.CHARLIB_TEMP_START;
		Equation eq = getEqFromString(eqS);
		System.out.println(eq);
		for (Instruction inst : ConvField.getInstructionsFromField(eq.fields[0], 2137).getSecond()) { System.out.println(inst); }
		return getInstructionListFromEq(eq);
	}

	private RAM ram;
	private Functions funcs;

	private int tempCell;

	private ArrayList<Instruction> getInstructionListFromEq(Equation eq) {
		Field[] fields = eq.fields;
		char[] opes = eq.operators;
		ArrayList<Instruction> list = new ArrayList<>();

		Integer[] cells = new Integer[opes.length];

		for (int i = 0; i < fields.length; i++) {
			final int i_ = i + 1;
			Field f = fields[i];
			char ope = i == opes.length ? '?' : opes[i];
			char nextOpe = i_ >= opes.length ? '?' : opes[i_];
			if(isAddi(ope))
				if(isAddi(nextOpe)) {
					cells[i] = tempCell;
					cells[i_] = tempCell++;
					list.add(Instruction.Add(ope, false, nextOpe, i));
				} else {

				}

		}

		return list;
	}

	private boolean isAddi(char ope) { return ope == '+' || ope == '-'; }

	static class ConvField {
		public final FieldType fieldType;
		public double value;
		public int cell;
		public final boolean isCell;
		public List<Instruction> inst;

		public ConvField(double value) {
			fieldType = Val;
			this.value = value;
			isCell = false;
		}

		public ConvField(int cell) {
			fieldType = Var;
			this.cell = cell;
			isCell = true;
		}

		public ConvField(Field f, int directCell) {
			isCell = switch (f.fieldType) {
			case Array, Func, Eq -> {
				Pair<Integer, ArrayList<Instruction>> obj = getInstructionsFromField(f, directCell);
				cell = obj.getFirst();
				fieldType = f.fieldType;
				inst = obj.getSecond();
				yield true;
			}
			case Val -> {
				fieldType = Val;
				value = f.value;
				yield false;
			}
			case Var -> {
				fieldType = Var;
				cell = f.cell;
				yield true;
			}
			};
		}

		public boolean isVal() { return fieldType == Val; }

		public boolean isVar() { return fieldType == Var; }

		public boolean isEq() { return fieldType == Eq; }

		public boolean isFunction() { return fieldType == Func; }

		public boolean isArray() { return fieldType == Array; }

		public static Pair<Integer, ArrayList<Instruction>> getInstructionsFromField(Field f) { return getInstructionsFromField(f, -1); }

		public static Pair<Integer, ArrayList<Instruction>> getInstructionsFromField(Field f, int directCell) {
			ArrayList<Instruction> list = new ArrayList<>();
			switch (f.fieldType) {
			case Val -> {
				final int outCell;
				if(directCell == -1)
					outCell = IGB_MA.TEMP_CELL_1;
				else
					outCell = directCell;
				return new Pair<>(outCell, Utils.listOf(Instruction.Init(f.value, outCell)));
			}
			case Var -> {
				if(f.cell == directCell)
					return new Pair<>(directCell, new ArrayList<>());
				final int outCell;
				if(directCell == -1)
					outCell = IGB_MA.TEMP_CELL_1;
				else
					outCell = directCell;
				return new Pair<>(outCell, Utils.listOf(Instruction.Copy(f.cell, outCell)));
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

				return new Pair<>(outCell, arr.getAccess(args, outCell));
			}
			case Eq -> {
				return null;
			}
			case Func -> {
				FunctionCall fc = f.funcCall;
				Function func = fc.func;
				Field[] args = fc.args;
				final int outCell;
				if(directCell == -1) {
					outCell = IGB_MA.FUNC_RETURN;
					list.addAll(func.getCall(args));
				} else {
					list.addAll(func.getCall(args, directCell));
					outCell = directCell;
				}
				return new Pair<>(outCell, list);
			}
			}
			return null;
		}
	}

	private Equation getEqFromString(final String str) {
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
			fields[i] = stringToField(fieldStringList.get(i));

		return new Equation(operators, fields);
	}

	private Field stringToField(String str) {
		Double v = Utils.parseDouble(str);
		if(v != null)
			return new Field(v);

		if(str.endsWith(")")) {
			if(str.startsWith("("))
				return new Field(getEqFromString(str.substring(1, str.length() - 1)));
			if(str.contains("(")) {
				int index = str.indexOf('(');
				String funcName = str.substring(0, index).stripTrailing();

				String[] args = Utils.getArrayElementsFromString(str.substring(index), '(', ')', ",");

				Field[] fa = stringArrayToFieldArray(args);

				return new Field(new FunctionCall(fa, funcs.getFunction(funcName, args.length)));
			} else
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

			return new Field(new ArrayAccess(arrName, fa));
		}

		v = ram.finalVars.get(str);
		if(v != null)
			return new Field(v);

		int cell = ram.getVariable(str);
		if(cell != -1)
			return new Field(cell);

		try {
			return new Field(getEqFromString(str));
		} catch (Exception e) {}

		throw new IGB_CL2_Exception("Unknown variable: \"" + str + "\".");
	}

	private Field[] stringArrayToFieldArray(String[] arr) {
		Field[] fa = new Field[arr.length];
		for (int i = 0; i < arr.length; i++)
			fa[i] = stringToField(arr[i]);
		return fa;
	}

	static class Equation {
		public final char[] operators;
		public final Field[] fields;

		public Equation(char[] operators, Field[] fields) {
			if(operators.length != fields.length - 1)
				throw new IGB_CL2_Exception("Equation syntax error.");
			this.operators = operators;
			this.fields = fields;
		}

		@Override
		public String toString() { return '(' + toStringNoBrackets() + ')'; }

		public String toStringNoBrackets() {
			if(fields.length == 0)
				return "";
			StringBuilder sb = new StringBuilder(fields[0].toString());
			for (int i = 1; i < fields.length; i++) {
				sb.append(" ");
				sb.append(operators[i - 1]);
				sb.append(" ");
				sb.append(fields[i]);

			}
			return sb.toString();
		}

		@SuppressWarnings("unused")
		public static Equation simplyfy(Equation eq) {
			ArrayList<Character> opeL = new ArrayList<>(eq.operators.length);
			ArrayList<Field> fieldL = new ArrayList<>(eq.fields.length);

			{
				Field[] fields = eq.fields;
				char[] opes = eq.operators;
				if(fields.length == 0)
					return null;
				if(fields.length == 1)
					return new Equation(new char[] {}, new Field[] { fields[0] });

			}

			char[] operators = opeL.stream().map(String::valueOf).collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString()
					.toCharArray();
			return new Equation(operators, fieldL.toArray(Field[]::new));
		}

	}

	class ArrayAccess {
		public final Array array;
		public final String name;
		public final Field[] dims;

		public ArrayAccess(String name, Field[] dims) {
			this.name = name;
			this.dims = dims;
			array = ram.getArray(name);
		}

		public ArrayList<Instruction> getAccess(Field[] dims, int outCell) { return array.getAccess(dims, outCell); }

		@Override
		public String toString() { return name + Utils.arrayToString(dims, '[', ']'); }
	}

	static class FunctionCall {
		public final Function func;
		public final Field[] args;

		public FunctionCall(Field[] args, Function func) {
			this.args = args;
			this.func = func;
		}

		public List<Instruction> getCall() { return func.getCall(args); }

		public List<Instruction> getCall(int outCell) { return func.getCall(args, outCell); }

		@Override
		public String toString() { return func.name + Utils.arrayToString(args, '(', ')', ","); }
	}

	static class Field {
		enum FieldType {
			Val, Var, Eq, Func, Array
		}

		public final FieldType fieldType;
		public double value;
		public int cell;
		public Equation eq;
		public FunctionCall funcCall;
		public ArrayAccess arrAccess;

		public Field(double value) {
			fieldType = Val;
			this.value = value;
		}

		public Field(int cell) {
			fieldType = Var;
			this.cell = cell;
		}

		public Field(Equation eq) {
			Equation eq1 = eq;
			while (eq1.fields.length == 1 && eq1.fields[0].isEq()) eq1 = eq1.fields[0].eq;

			Field[] fields = eq1.fields;
			char[] opes = eq1.operators;
			if(fields.length == 0)
				throw new IGB_CL2_Exception("Equation syntax error");
			if(fields.length == 1) {
				Field f = fields[0];
				switch (f.fieldType) {
				case Array -> {
					fieldType = Array;
					arrAccess = f.arrAccess;
				}
				case Eq -> throw new IGB_CL2_Exception("how");
				case Func -> {
					fieldType = Func;
					funcCall = f.funcCall;
				}
				case Val -> {
					fieldType = Val;
					value = f.value;
				}
				case Var -> {
					fieldType = Var;
					cell = f.cell;
				}
				default -> throw new IGB_CL2_Exception("how");

				}
			} else {

				boolean replace = true;
				for (char ope : opes)
					if(((ope != '+') && (ope != '-'))) {
						replace = false;
						break;
					}
				if(replace) {
					int val = 0;

					for (int i = 0; i < fields.length; i++) {
						Field f = fields[i];
						if(!f.isVal()) {
							replace = false;
							break;
						}
						char ope = i == 0 ? '+' : opes[i - 1];
						if(ope == '+')
							val += f.value;
						else
							val -= f.value;
					}
					if(replace) {
						fieldType = Val;
						value = val;
					} else {
						fieldType = Eq;
						this.eq = eq1;
					}
				} else {
					fieldType = Eq;
					this.eq = eq1;
				}
			}
		}

		public Field(FunctionCall funcCall) {
			fieldType = Func;
			this.funcCall = funcCall;
		}

		public Field(ArrayAccess arrAccess) {
			fieldType = Array;
			this.arrAccess = arrAccess;
		}

		public boolean isVal() { return fieldType == Val; }

		public boolean isVar() { return fieldType == Var; }

		public boolean isEq() { return fieldType == Eq; }

		public boolean isFunction() { return fieldType == Func; }

		public boolean isArray() { return fieldType == Array; }

		@Override
		public String toString() {
			return switch (fieldType) {
			case Array -> arrAccess.toString();
			case Eq -> eq.toString();
			case Func -> funcCall.toString();
			case Val -> value + "d";
			case Var -> cell + "c";
			};
		}
	}

}
