package me.krypek.igb.cl2.datatypes;

import static me.krypek.igb.cl1.Instruction.Cell_Call;
import static me.krypek.igb.cl1.Instruction.Copy;

import java.util.ArrayList;

import me.krypek.igb.cl1.IGB_MA;
import me.krypek.igb.cl1.Instruction;
import me.krypek.igb.cl2.IGB_CL2_Exception;
import me.krypek.igb.cl2.RAM;
import me.krypek.igb.cl2.solvers.EqSolver;
import me.krypek.utils.TripleObject;
import me.krypek.utils.Utils;
import me.krypek.utils.Utils.Generator;

public class Function {
	public final String name;
	public final String startPointerName;
	public final String endPointerName;
	public final String[] argNames;
	public final int[] argCells;
	public final boolean returnType;

	public final Generator<ArrayList<Instruction>, TripleObject<EqSolver, Field[], Integer>> callAction;

	public Function(String name, String startPointer, String endPointer, String[] argsName, boolean returnType, RAM ram) {
		this.name = name;
		startPointerName = startPointer;
		endPointerName = endPointer;
		argCells = ram.reserve(argsName.length);
		argNames = argsName;
		this.returnType = returnType;
		callAction = null;
	}

	public Function(String name, Generator<ArrayList<Instruction>, TripleObject<EqSolver, Field[], Integer>> callAction, int argLen, boolean returnType) {
		this.name = name;
		this.callAction = callAction;
		this.returnType = returnType;
		startPointerName = null;
		endPointerName = null;
		argNames = null;
		argCells = new int[argLen];
	}

	public ArrayList<Instruction> getCall(EqSolver eqs, Field[] args) {
		if(callAction != null)
			return callAction.get(new TripleObject<>(eqs, args, 0));
		ArrayList<Instruction> list = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			var obj = eqs.getInstructionsFromField(args[i], argCells[i]);
			if(obj.getSecond() != null)
				list.addAll(obj.getSecond());
		}
		list.add(Cell_Call(startPointerName));
		return list;
	}

	public ArrayList<Instruction> getCall(EqSolver eqs, Field[] args, int outputCell) {
		if(!returnType)
			throw new IGB_CL2_Exception("Function: \"" + name + "\" doesn't return any variables, it returns void.");

		if(callAction != null)
			return callAction.get(new TripleObject<>(eqs, args, outputCell));

		ArrayList<Instruction> list = getCall(eqs, args);
		list.add(Copy(IGB_MA.FUNC_RETURN, outputCell));

		return list;
	}

	public void initVariables(RAM ram) {
		for (int i = 0; i < argCells.length; i++)
			ram.newVar(argNames[i], new Variable(argCells[i]));
	}

	@Override
	public String toString() {
		return callAction == null ? startPointerName + ", \t" + name + Utils.arrayToString(argNames, '(', ')', ",") + " " + returnType
				: name + ", len=" + argCells.length + ", " + returnType + ", " + callAction;
	}
}
