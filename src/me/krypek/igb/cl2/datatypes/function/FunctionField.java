package me.krypek.igb.cl2.datatypes.function;

import me.krypek.igb.cl2.solvers.EqSolver;

public interface FunctionField {
	public FunctionCallField get(String str, EqSolver eqsolver);
}
