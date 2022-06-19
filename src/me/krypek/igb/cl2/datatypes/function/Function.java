package me.krypek.igb.cl2.datatypes.function;

import java.util.ArrayList;

import me.krypek.igb.cl1.Instruction;
import me.krypek.igb.cl2.IGB_CL2_Exception.Err;
import me.krypek.igb.cl2.RAM;
import me.krypek.utils.Utils;

public abstract class Function {
	public final String name;
	public final boolean returnType;
	public final FunctionField[] fields;

	protected Function(String name, boolean returnType, FunctionField[] fields) {
		this.name = name;
		this.returnType = returnType;
		this.fields = fields;
		checkNames();
	}

	public abstract ArrayList<Instruction> call(FunctionCall call);

	private void checkNames() {
		checkFunctionName(name);
		for (FunctionField ff : fields)
			if(ff instanceof FunctionNormalField)
				checkFunctionName(((FunctionNormalField) ff).name);
	}

	private static void checkFunctionName(String name) {
		if(RAM.illegalNames.contains(name))
			throw Err.normal("Illegal function name: \"" + name + "\".");
		for (String c : RAM.illegalCharacters)
			if(name.contains(c))
				throw Err.normal("Name contains illegal character: \"" + name + "\".");

	}

	public static FunctionField[] fieldsOf(FunctionField... fields) { return fields; }

	@Override
	public String toString() { return name + '(' + Utils.arrayToString(fields, ", ") + ')' + (returnType ? " -->" : ""); }
}
