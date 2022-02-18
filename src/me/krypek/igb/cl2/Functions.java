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

	RAM[] rams;
	boolean[] assus;
	int[] startlines;
	int[] lenlimits;

	public Functions(String[][] inputs, String[] fileNames) {
		functionMap = new HashMap<>();
		rams = new RAM[inputs.length];
		assus = new boolean[inputs.length];
		startlines = new int[inputs.length];
		lenlimits = new int[inputs.length];

		for (int i = 0; i < inputs.length; i++) {
			String[] input = inputs[i];
			HashMap<String, Double> finalVars = new HashMap<>();
			int startline = -1, lenlimit = -1, thread = 0, ramcell = -1, ramlimit = -1;
			boolean assu = true, ramInited = false;

			for (int x = 0; x < input.length; x++) {
				String cmd = input[x];
				int spaceIndex = cmd.indexOf(' ');
				if(spaceIndex == -1)
					spaceIndex = cmd.length();
				String first = cmd.substring(0, spaceIndex);

				if(first.startsWith("$")) {
					String[] split = cmd.split("=");
					if(split.length == 1)
						throw new IGB_CL2_Exception(i, x, "Compiler variable has to be set.");
					if(split.length > 2)
						throw new IGB_CL2_Exception(i, x, "Syntax error.");
					String eq = split[1].strip();

					String name = split[0].substring(1).strip();

					if(name.toLowerCase().equals("autoscreensizeupdate") || name.toLowerCase().equals("assu")) {
						if(eq.equals("true") || eq.equals("1"))
							assu = true;
						else if(eq.equals("false") || eq.equals("0"))
							assu = false;
						else
							throw new IGB_CL2_Exception(i, x, "Assu value can be only \"true\" or \"false\".");
						continue;
					}

					double valD = RAM.solveFinalEq(eq, new HashMap<>());
					if(valD % 1 != 0)
						throw new IGB_CL2_Exception(i, x, "Compiler variable value has to be an integer.");
					int val = (int) valD;
					if(val < 0)
						throw new IGB_CL2_Exception(i, x, "Compiler variable value cannot be negative.");

					switch (name.toLowerCase()) {
					case "startline" -> {
						if(startline != -1)
							throw new IGB_CL2_Exception(i, x, "Cannot set startline twice.");
						startline = val;
					}
					case "lenlimit" -> {
						if(lenlimit != -1)
							throw new IGB_CL2_Exception(i, x, "Cannot set lenlimit twice.");
						lenlimit = val;
					}
					case "ramlimit" -> {
						if(ramlimit != -1)
							throw new IGB_CL2_Exception(i, x, "Cannot set ramlimit twice.");
						ramlimit = val;
					}
					case "ramcell" -> {
						if(ramcell != -1)
							throw new IGB_CL2_Exception(i, x, "Cannot set ramcell twice.");
						ramcell = val;
					}
					case "thread" -> {
						if(thread > 1)
							throw new IGB_CL2_Exception(i, x, "Thread can be only 0 or 1.");
					}
					default -> throw new IGB_CL2_Exception(i, x, "Unknown compiler variable: \"" + name + "\".");
					}
				} else {
					if(!ramInited) {
						if(startline == -1)
							throw new IGB_CL2_Exception(i, x, "Startline has to be set.");
						if(ramcell == -1)
							throw new IGB_CL2_Exception(i, x, "Ramcell has to be set.");
						if(ramlimit == -1)
							throw new IGB_CL2_Exception(i, x, "Ramlimit has to be set.");

						rams[i] = new RAM(ramlimit, ramcell, thread);
						startlines[i] = startline;
						assus[i] = assu;
						lenlimits[i] = lenlimit == -1 ? Integer.MAX_VALUE : lenlimit;
					}
					if(first.equals("void")) {
						initFunction(false, cmd.substring(spaceIndex + 1), true, i, x, rams[i]);
					} else if(IGB_CL2.varStr.contains(first) && !cmd.contains("=")) {
						initFunction(true, cmd.substring(spaceIndex + 1), false, i, x, rams[i]);
					} else if(first.equals("final")) {
						// init final vars
						if(!cmd.contains("="))
							throw new IGB_CL2_Exception(i, x, "Final variable has to be set.");
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
						double val = RAM.solveFinalEq(eq, finalVars);
						if(finalVars.containsKey(varname))
							throw new IGB_CL2_Exception(i, x, "Final variable already exists: \"" + varname + "\".");
						finalVars.put(varname, val);
					}
				}

			}
			rams[i].setFinalVariables(finalVars);
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