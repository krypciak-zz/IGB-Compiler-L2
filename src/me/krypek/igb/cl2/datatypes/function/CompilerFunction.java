package me.krypek.igb.cl2.datatypes.function;

import java.util.ArrayList;

import me.krypek.igb.cl1.datatypes.Instruction;

public class CompilerFunction extends Function {

	public final FunctionField[] fields;
	private final java.util.function.Function<FunctionCall, ArrayList<Instruction>> generator;

	public CompilerFunction(String name, boolean returnType, FunctionField[] fields, java.util.function.Function<FunctionCall, ArrayList<Instruction>> generator) {
		super(name, returnType, fields);
		this.fields = fields;
		this.generator = generator;
	}

	@Override
	public ArrayList<Instruction> call(FunctionCall call) { return generator.apply(call); }

	public int getArgumentLength() { return fields.length; }
}
