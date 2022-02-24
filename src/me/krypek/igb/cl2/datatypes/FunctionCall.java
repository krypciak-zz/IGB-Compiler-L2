package me.krypek.igb.cl2.datatypes;

import java.util.ArrayList;

import me.krypek.igb.cl1.Instruction;
import me.krypek.igb.cl2.solvers.EqSolver;
import me.krypek.utils.Utils;

public class FunctionCall {
	public final Function func;
	public final Field[] args;

	public FunctionCall(Field[] args, Function func) {
		this.args = args;
		this.func = func;
	}

	public ArrayList<Instruction> getCall(EqSolver eqs) { return func.getCall(eqs, args); }

	public ArrayList<Instruction> getCall(EqSolver eqs, int outCell) { return func.getCall(eqs, args, outCell); }

	@Override
	public String toString() { return func.name + Utils.arrayToString(args, '(', ')', ","); }
}