package me.krypek.igb.cl2.datatypes.function;

import static me.krypek.igb.cl1.Instruction.Cell_Call;
import static me.krypek.igb.cl1.Instruction.Copy;

import java.util.ArrayList;

import me.krypek.igb.cl1.IGB_MA;
import me.krypek.igb.cl1.Instruction;
import me.krypek.igb.cl2.IGB_CL2_Exception.Err;
import me.krypek.igb.cl2.RAM;
import me.krypek.igb.cl2.datatypes.Variable;
import me.krypek.igb.cl2.datatypes.function.FunctionNormalField.FunctionCallNormalField;
import me.krypek.igb.cl2.datatypes.function.FunctionStringField.FunctionCallStringField;

public class UserFunction extends Function {
	public final String startPointer;
	public final String endPointer;
	public final FunctionNormalField[] fields;

	public UserFunction(String name, String startPointer, String endPointer, FunctionNormalField[] fields, boolean returnType) {
		super(name, returnType, fields);
		this.fields = fields;
		this.startPointer = startPointer;
		this.endPointer = endPointer;
	}

	@Override
	public ArrayList<Instruction> call(FunctionCall call) {

		ArrayList<Instruction> list = new ArrayList<>();
		for (int i = 0; i < call.fields.length; i++) {
			FunctionCallField field = call.fields[i];
			if(field instanceof FunctionCallStringField)
				throw Err.normal("Function argument " + i + ": Strings aren't accepted for user functions.");

			if(!(field instanceof FunctionCallNormalField fcnf))
				throw Err.normal("Function argument " + i + " i forgor to implement handling");

			var obj = call.eqsolver.getInstructionsFromField(fcnf.field, fields[i].cell);
			if(obj.getSecond() != null)
				list.addAll(obj.getSecond());
		}
		list.add(Cell_Call(startPointer));

		if(returnType && call.outputCell != -1)
			list.add(Copy(IGB_MA.FUNC_RETURN, call.outputCell));
		return list;
	}

	public int getArgumentLength() { return fields.length; }

	public void initVariables(RAM ram) {
		for (FunctionNormalField field : fields)
			ram.newVar(field.name, new Variable(field.cell));
	}

}
