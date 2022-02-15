package me.krypek.igb.cl2;

import java.util.ArrayList;
import java.util.HashMap;

import me.krypek.igb.cl1.IGB_MA;
import me.krypek.igb.cl1.Instruction;
import me.krypek.igb.cl2.EqSolver.Field;
import me.krypek.utils.Utils;

class Functions {
	private final HashMap<String, HashMap<Integer, Function>> functionMap;

	public Function getFunction(String name, int argLength) {
		HashMap<Integer, Function> map = functionMap.get(name);
		if(map == null)
			throw new IGB_CL2_Exception("Function: \"" + name + "\" doesn't exist.");
		Function func = map.get(argLength);
		if(func == null)
			throw new IGB_CL2_Exception("Function: \"" + name + "\" doesn't exist with " + argLength + " arguments.");
		return func;
	}

	private void addFunction(String name, Function func) {
		HashMap<Integer, Function> map = functionMap.get(name);
		if(map == null) {
			functionMap.put(name, new HashMap<>());
			map = functionMap.get(name);
		}
		final int argLength = func.argsName.length;
		if(map.containsKey(argLength))
			throw new IGB_CL2_Exception("Function: \"" + name + "\" with " + argLength + " arguments already exists.");
		map.put(argLength, func);
	}

	private void initFunction(boolean returnType, String rest, boolean ignoreFirstError, int file, int line, RAM ram) {
		int bracketIndex = rest.indexOf('(');
		if(bracketIndex == -1) {
			if(ignoreFirstError)
				return;
			throw new IGB_CL2_Exception(file, line, "Function syntax error.");
		}
		String funcName = rest.substring(0, bracketIndex);
		String[] splited = Utils.getArrayElementsFromString(rest.substring(bracketIndex), '(', ')', ",");
		for (int i = 0; i < splited.length; i++) {
			String arg = splited[i];
			int spaceIndex = arg.indexOf(' ');
			if(spaceIndex == -1)
				throw new IGB_CL2_Exception(file, line, "Function syntax error.");

			String type = arg.substring(0, spaceIndex);
			if(!IGB_CL2.varStr.contains(type))
				throw new IGB_CL2_Exception(file, line, "Invalid type: \"" + type + "\".");
			String argName = arg.substring(spaceIndex + 1);
			splited[i] = argName;
		}
		Function func = new Function(funcName, ":f_" + funcName + "_" + splited.length, splited, returnType, ram);
		addFunction(funcName, func);
	}

	HashMap<String, Double>[] finalVarsArr;

	@SuppressWarnings("unchecked")
	public Functions(String[][] inputs, String[] fileNames, RAM ram) {
		functionMap = new HashMap<>();

		finalVarsArr = new HashMap[inputs.length];

		for (int i = 0; i < inputs.length; i++) {
			String[] input = inputs[i];
			finalVarsArr[i] = new HashMap<>();
			for (int x = 0; x < input.length; x++) {
				String cmd = input[x];
				int spaceIndex = cmd.indexOf(' ');
				if(spaceIndex == -1)
					spaceIndex = cmd.length();
				String first = cmd.substring(0, spaceIndex);
				if(first.equals("void"))
					initFunction(false, cmd.substring(spaceIndex + 1), true, i, x, ram);
				else if(IGB_CL2.varStr.contains(first) && !cmd.contains("=")) {
					initFunction(true, cmd.substring(spaceIndex + 1), false, i, x, ram);
				} else if(first.equals("final")) {
					// init final vars
					if(cmd.contains("=")) {}
					String[] split = cmd.split("=");
					int spaceIndex1 = split[0].indexOf(' ');
					if(spaceIndex1 == -1)
						throw new IGB_CL2_Exception(i, x, "Syntax error.");
					split[0] = split[0].substring(spaceIndex1);
					if(split.length != 2)
						throw new IGB_CL2_Exception(i, x, "Syntax error.");
					String eq = split[1].strip();
					spaceIndex1 = split[0].indexOf(' ');
					if(spaceIndex1 == -1)
						throw new IGB_CL2_Exception(i, x, "Syntax error.");

					String varname = split[0].stripLeading().substring(spaceIndex1).strip();
					String type = split[0].stripLeading().substring(0, spaceIndex + 1).strip();
					if(!IGB_CL2.varStr.contains(type))
						throw new IGB_CL2_Exception(i, x, "Unknown variable type: \"" + type + "\".");
					double val = ram.solveFinalEq(eq);
					if(finalVarsArr[i].containsKey(varname))
						throw new IGB_CL2_Exception(i, x, "Final variable already exists: \"" + varname + "\".");
					finalVarsArr[i].put(varname, val);

				}

			}
		}
	}

	@Override
	public String toString() {
		if(functionMap.size() == 0)
			return "Functions: {}";
		StringBuilder sb = new StringBuilder("Functions: {\n");
		functionMap.forEach((k, v) -> {
			v.forEach((k1, v1) -> {
				sb.append("\t");
				sb.append(v1.toString());
				sb.append("\n");

			});
		});
		sb.append("}");
		return sb.toString();
	}
}

class Function {
	public final String name;
	public final String pointerName;
	public final String[] argsName;
	public final int[] argCells;
	public final boolean returnType;

	public Function(String name, String pointerName, String[] argsName, boolean returnType, RAM ram) {
		this.name = name;
		this.pointerName = pointerName;
		argCells = ram.reserve(argsName.length);
		this.argsName = argsName;
		this.returnType = returnType;
	}

	public ArrayList<Instruction> getCall(EqSolver eqs, Field[] args) {
		ArrayList<Instruction> list = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			var obj = eqs.getInstructionsFromField(args[i], argCells[i]);
			if(obj.getSecond() != null)
				list.addAll(obj.getSecond());
		}
		list.add(Instruction.Cell_Call(pointerName));
		return list;
	}

	public ArrayList<Instruction> getCall(EqSolver eqs, Field[] args, int outputCell) {
		if(!returnType)
			throw new IGB_CL2_Exception("Function: \"" + name + "\" doesn't return any variables, it returns void.");

		ArrayList<Instruction> list = getCall(eqs, args);
		list.add(Instruction.Copy(IGB_MA.FUNC_RETURN, outputCell));

		return list;
	}

	@Override
	public String toString() { return pointerName + ", \t" + name + Utils.arrayToString(argsName, '(', ')', ",") + " " + returnType; }
}