package me.krypek.igb.cl2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import me.krypek.igb.cl1.Instruction;
import me.krypek.utils.Utils;

class EqSolver {
	private final static Set<Character> operators = Set.of('+', '-', '*', '/', '%');

	public ArrayList<Instruction> solve(final String eqS, RAM ram, Functions funcs) {
		this.ram = ram;
		this.funcs = funcs;
		Equation eq = getFieldListFromString(eqS);
		System.out.println(eq);
		return null;
	}

	private RAM ram;
	private Functions funcs;

	private Equation getFieldListFromString(final String str) {
		ArrayList<Character> operatorList = new ArrayList<>();
		ArrayList<String> fieldStringList = new ArrayList<>();
		{
			char[] charA = str.toCharArray();
			StringBuilder sb = new StringBuilder(32);
			for (char c : charA) {
				if(operators.contains(c)) {
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
				return new Field(stringToField(str.substring(1, str.length() - 1)));
			else if(str.contains("(")) {
				int index = str.indexOf('(');
				String funcName = str.substring(0, index);

				String[] args = Utils.getArrayElementsFromString(str.substring(index), '(', ')', ",");

				Field[] fa = new Field[args.length];
				for (int i = 0; i < args.length; i++)
					fa[i] = stringToField(args[i]);

				return new Field(new FunctionCall(fa, funcs.getFunction(funcName, args.length)));
			} else
				throw new IGB_CL2_Exception("Please enter a valid amount of brackets.");
		}

		if(str.endsWith("]") && str.contains("[")) {
			String arrName = "";
		}

		v = ram.finalVars.get(str);
		if(v != null)
			return new Field(v);

		int cell = ram.getVariable(str);
		if(cell != -1)
			return new Field(cell);

		throw new IGB_CL2_Exception("Syntax error.");
	}
}

class Equation {
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
}

class ArrayAccess {
	public final String name;
	public final Field[] args;

	public ArrayAccess(String name, Field[] args) {
		this.name = name;
		this.args = args;
	}

	@Override
	public String toString() { return name + Utils.arrayToString(args, '[', ']'); }
}

class FunctionCall {
	public final Function func;
	public final Field[] args;

	public FunctionCall(Field[] args, Function func) {
		this.args = args;
		this.func = func;
	}

	public List<Instruction> getCall() { return func.getCall(args); }

	@Override
	public String toString() { return func.name + Utils.arrayToString(args, '(', ')', ","); }
}

class Field {
	enum FieldType {
		Val, Var, Field, Func, Array
	}

	public final FieldType fieldType;
	public double value;
	public int cell;
	public Field field;
	public FunctionCall funcCall;
	public ArrayAccess arrAccess;

	public Field(double value) {
		this.fieldType = FieldType.Val;
		this.value = value;
	}

	public Field(int cell) {
		this.fieldType = FieldType.Var;
		this.cell = cell;
	}

	public Field(Field field) {
		this.fieldType = FieldType.Field;
		this.field = field;
	}

	public Field(FunctionCall funcCall) {
		this.fieldType = FieldType.Func;
		this.funcCall = funcCall;
	}

	public Field(ArrayAccess arrAccess) {
		this.fieldType = FieldType.Array;
		this.arrAccess = arrAccess;
	}

	public boolean isVal() { return fieldType == FieldType.Val; }

	public boolean isVar() { return fieldType == FieldType.Var; }

	public boolean isField() { return fieldType == FieldType.Field; }

	public boolean isFunction() { return fieldType == FieldType.Func; }

	public boolean isArray() { return fieldType == FieldType.Array; }

	@Override
	public String toString() {
		return switch (fieldType) {
		case Array -> arrAccess.toString();
		case Field -> field.toString();
		case Func -> funcCall.toString();
		case Val -> value + "d";
		case Var -> cell + "c";
		};
	}
}
