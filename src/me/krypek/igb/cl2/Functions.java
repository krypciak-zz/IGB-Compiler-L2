package me.krypek.igb.cl2;

import java.util.HashMap;
import java.util.List;

import me.krypek.igb.cl1.Instruction;
import me.krypek.utils.Utils;

class Functions {
	private HashMap<String, HashMap<Integer, Function>> functionMap;

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

	private void initFunction(boolean returnType, String rest, boolean ignoreFirstError, int file, int line) {
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
			if(!IGB_Compiler_L2.varStr.contains(type))
				throw new IGB_CL2_Exception(file, line, "Invalid type: \"" + type + "\".");
			String argName = arg.substring(spaceIndex + 1);
			splited[i] = argName;
		}
		Function func = new Function(funcName, ":f_" + funcName + "_" + splited.length, splited, returnType);
		addFunction(funcName, func);
	}

	public Functions(String[][] inputs, String[] fileNames) {
		functionMap = new HashMap<>();
		for (int i = 0; i < inputs.length; i++) {
			String[] input = inputs[i];
			for (int x = 0; x < input.length; x++) {
				String cmd = input[x];
				int spaceIndex = cmd.indexOf(' ');
				if(spaceIndex == -1)
					spaceIndex = cmd.length();
				String first = cmd.substring(0, spaceIndex);
				System.out.println(cmd);
				if(first.equals("void")) {
					initFunction(false, cmd.substring(spaceIndex + 1), true, i, x);
				} else if(IGB_Compiler_L2.varStr.contains(first)) {
					initFunction(true, cmd.substring(spaceIndex + 1), false, i, x);
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
	public final boolean returnType;

	public Function(String name, String pointerName, String[] argsName, boolean returnType) {
		this.name = name;
		this.pointerName = pointerName;
		this.argsName = argsName;
		this.returnType = returnType;
	}

	public List<Instruction> getCall(Field[] args) {

		return null;
	}

	@Override
	public String toString() { return pointerName + ", \t" + name + Utils.arrayToString(argsName, '(', ')', ",") + " " + returnType; }
}