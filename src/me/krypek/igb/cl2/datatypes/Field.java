package me.krypek.igb.cl2.datatypes;

import static me.krypek.igb.cl2.datatypes.FieldType.*;

import me.krypek.igb.cl2.IGB_CL2_Exception;

public class Field {
	public final FieldType fieldType;
	public double value;
	public int cell;
	public Equation eq;
	public FunctionCall funcCall;
	public ArrayAccess arrAccess;

	public Field(int cell) {
		fieldType = Var;
		this.cell = cell;
	}

	public Field(double val) {
		fieldType = Val;
		value = val;
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
				if(ope != '+' && ope != '-') {
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
