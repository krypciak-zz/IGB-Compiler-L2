package me.krypek.igb.cl2.datatypes.function;

import java.util.ArrayList;

import me.krypek.igb.cl1.Instruction;
import me.krypek.igb.cl2.IGB_CL2_Exception.Err;
import me.krypek.igb.cl2.solvers.EqSolver;
import me.krypek.utils.Utils;

public class FunctionCall {
	public final Function func;
	public final FunctionCallField[] fields;
	public final EqSolver eqsolver;
	public final int outputCell;

	public FunctionCall(FunctionCallField[] fields, Function func, EqSolver eqsolver) {
		this.fields = fields;
		this.func = func;
		this.eqsolver = eqsolver;
		this.outputCell = -1;
	}

	public FunctionCall(FunctionCallField[] fields, Function func, EqSolver eqsolver, int outputCell) {
		if(!func.returnType)
			throw Err.normal("Function: \"" + func.name + "\" returns void.");

		this.fields = fields;
		this.func = func;
		this.eqsolver = eqsolver;
		this.outputCell = outputCell;
	}

	public FunctionCall cloneWithOutputCell(int outputCell) {
		assert outputCell != -1;
		return new FunctionCall(fields, func, eqsolver, outputCell);
	}

	public ArrayList<Instruction> call() { return func.call(this); }

	public ArrayList<Instruction> callReturn(EqSolver eqs) {
		if(outputCell == -1)
			throw Err.normal("Function: \"" + func.name + "\" returns void.");
		return call();
	}

	@Override
	public String toString() { return func.name + Utils.arrayToString(fields, '(', ')', ",") + " -> " + outputCell; }
}