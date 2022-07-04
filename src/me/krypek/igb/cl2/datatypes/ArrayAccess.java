package me.krypek.igb.cl2.datatypes;

import java.util.ArrayList;

import me.krypek.igb.cl1.datatypes.Instruction;
import me.krypek.igb.cl2.solvers.EqSolver;
import me.krypek.utils.Utils;

public class ArrayAccess {
	public final Array array;
	public final String name;
	public final Field[] dims;

	public ArrayAccess(String name, Array arr, Field[] dims) {
		this.name = name;
		this.dims = dims;
		array = arr;
	}

	public ArrayList<Instruction> getAccess(EqSolver eqs, Field[] dims, int outCell) { return array.getAccess(eqs, dims, outCell); }

	@Override
	public String toString() { return name + Utils.arrayToString(dims, '[', ']'); }
}
