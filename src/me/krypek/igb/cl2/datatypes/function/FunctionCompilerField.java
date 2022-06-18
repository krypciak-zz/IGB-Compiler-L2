package me.krypek.igb.cl2.datatypes.function;

import me.krypek.igb.cl2.datatypes.Field;
import me.krypek.igb.cl2.solvers.EqSolver;

public class FunctionCompilerField implements FunctionField {

	@Override
	public FunctionCallCompilerField get(String str, EqSolver eqsolver) {
		Field field = eqsolver.stringToField(str, false);
		return new FunctionCallCompilerField(field);
	}

	public static class FunctionCallCompilerField implements FunctionCallField {
		public final Field field;

		public FunctionCallCompilerField(Field field) { this.field = field; }

		@Override
		public String toString() { return field.toString(); }
	}
}
