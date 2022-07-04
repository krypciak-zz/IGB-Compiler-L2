package me.krypek.igb.cl2.datatypes;

import java.util.ArrayList;

import me.krypek.igb.cl1.datatypes.Instruction;

public class Variable {
	public final int cell;
	public java.util.function.Function<String, ArrayList<Instruction>> action;

	public Variable(int cell) { this.cell = cell; }

	public Variable(int cell, java.util.function.Function<String, ArrayList<Instruction>> setAction) {
		this.cell = cell;
		action = setAction;
	}

	@Override
	public String toString() { return cell + (action == null ? "" : " (action)"); }
}
