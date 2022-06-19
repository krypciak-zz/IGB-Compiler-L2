package me.krypek.igb.cl2.datatypes.function;

import me.krypek.igb.cl2.IGB_CL2_Exception.Err;
import me.krypek.igb.cl2.RAM;
import me.krypek.igb.cl2.datatypes.Field;
import me.krypek.igb.cl2.solvers.EqSolver;

public class FunctionNormalField implements FunctionField {
	public final String name;
	public final int cell;

	public FunctionNormalField(String name, RAM ram) {

		int index = name.indexOf('|');
		if(index != -1) {
			this.name = name.substring(0, index);
			int index1 = name.lastIndexOf('|');
			if(index == index1)
				throw Err.normal("Variable cell setting syntax error.");

			cell = (int) ram.solveFinalEq(name.substring(index + 1, index1));
		} else {
			this.name = name;
			cell = ram.reserve(1)[0];
		}
	}

	@Override
	public FunctionCallField get(String str, EqSolver eqsolver) {
		Field field = eqsolver.stringToField(str, false);
		return new FunctionCallNormalField(field);
	}

	public static class FunctionCallNormalField implements FunctionCallField {
		public final Field field;

		public FunctionCallNormalField(Field field) { this.field = field; }

		@Override
		public String toString() { return field.toString(); }
	}

}
