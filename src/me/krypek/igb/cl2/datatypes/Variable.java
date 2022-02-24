package me.krypek.igb.cl2.datatypes;

import me.krypek.igb.cl1.Instruction;
import me.krypek.igb.cl2.IGB_CL2_Exception;
import me.krypek.utils.Utils.Generator;

public class Variable {
	public final int cell;
	public Generator<Instruction, String> action;

	public Variable(int cell) { this.cell = cell; }

	public Variable(int cell, Generator<Instruction, String> setAction) {
		this.cell = cell;
		action = setAction;
	}

	public Instruction setAction(String arg) {
		if(action == null)
			throw new IGB_CL2_Exception();
		return action.get(arg);
	}

	@Override
	public String toString() { return cell + (action == null ? "" : " (action)"); }
}
