package me.krypek.igb.cl2.datatypes.function;

import me.krypek.igb.cl2.datatypes.Field;
import me.krypek.igb.cl2.datatypes.function.FunctionCompilerField.FunctionCallCompilerField;
import me.krypek.igb.cl2.solvers.EqSolver;

public class FunctionNormalField implements FunctionField {
	public final String name;
	public final int cell;

	public FunctionNormalField(String name, int cell) {
		this.name = name;
		this.cell = cell;
	}

	public FunctionCallField get(String str, EqSolver eqsolver) {
		Field field = eqsolver.stringToField(str, false);
		return new FunctionCallCompilerField(field);
	}

	public static class FunctionCallNormalField implements FunctionCallField {
		public final Field field;

		public FunctionCallNormalField(Field field) { this.field = field; }

		@Override
		public String toString() { return field.toString(); }
	}

}
