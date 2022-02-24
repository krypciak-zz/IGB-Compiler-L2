package me.krypek.igb.cl2.datatypes;

import java.util.ArrayList;

import me.krypek.igb.cl1.Instruction;
import me.krypek.utils.Utils.Generator;

public class Variable {
	public final int cell;
	public Generator<ArrayList<Instruction>, String> action;

	public Variable(int cell) { this.cell = cell; }

	public Variable(int cell, Generator<ArrayList<Instruction>, String> setAction) {
		this.cell = cell;
		action = setAction;
	}

	@Override
	public String toString() { return cell + (action == null ? "" : " (action)"); }
}
