package me.krypek.igb.cl2.datatypes.function;

import me.krypek.igb.cl2.IGB_CL2_Exception.Err;
import me.krypek.igb.cl2.solvers.EqSolver;

public class FunctionStringField implements FunctionField {

	@Override
	public FunctionCallStringField get(String str, EqSolver eqsolver) {
		if(str.length() < 2 || str.charAt(0) != '\"' || str.charAt(str.length() - 1) != '\"')
			throw Err.normal("Comment has to start and end with \".");
		return new FunctionCallStringField(str.substring(1, str.length() - 1));
	}

	public static class FunctionCallStringField implements FunctionCallField {
		public final String str;

		public FunctionCallStringField(String str) { this.str = str; }

		@Override
		public String toString() { return '\"' + str + '\''; }
	}

}
