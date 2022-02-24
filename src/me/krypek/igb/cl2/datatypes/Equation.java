package me.krypek.igb.cl2.datatypes;

import me.krypek.igb.cl2.IGB_CL2_Exception;

public class Equation {
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