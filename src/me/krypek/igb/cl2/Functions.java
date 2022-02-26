package me.krypek.igb.cl2;

import static me.krypek.igb.cl1.Instruction.*;

import java.util.ArrayList;
import java.util.HashMap;

import me.krypek.igb.cl1.IGB_MA;
import me.krypek.igb.cl1.Instruction;
import me.krypek.igb.cl2.datatypes.Field;
import me.krypek.igb.cl2.datatypes.Function;
import me.krypek.igb.cl2.solvers.EqSolver;
import me.krypek.utils.Pair;
import me.krypek.utils.Utils;

public class Functions {
	private final HashMap<String, HashMap<Integer, Function>> functionMap;

	private int file;
	private int line;

	public Function getFunction(String name, int argLength) {
		HashMap<Integer, Function> map = functionMap.get(name);

		if(map == null)
			throw new IGB_CL2_Exception("Function: \"" + name + "\" doesn't exist.");

		Function func = map.get(argLength);
		if(func == null)
			throw new IGB_CL2_Exception("Function: \"" + name + "\" doesn't exist with " + argLength + " arguments.");
		return func;
	}

	private void addFunction(Function func, boolean ignoreName) {
		String name = func.name;
		if(!ignoreName && (RAM.illegalNames.contains(name) || name.equals("sqrt")))
			throw new IGB_CL2_Exception("Illegal function name: \"" + name + "\".");

		for (String c : RAM.illegalCharacters)
			if(name.contains(c))
				throw new IGB_CL2_Exception("Variable name \"" + name + "\" contains illegal character: \"" + c + "\".");

		HashMap<Integer, Function> map = functionMap.get(name);
		if(map == null) {
			functionMap.put(name, new HashMap<>());
			map = functionMap.get(name);
		}
		final int argLength = func.argCells.length;
		if(map.containsKey(argLength))
			throw new IGB_CL2_Exception("Function: \"" + name + "\" with " + argLength + " arguments already exists.");
		map.put(argLength, func);
	}

	private void initFunction(boolean returnType, String rest, boolean ignoreFirstError, RAM ram) {
		int bracketIndex = rest.indexOf('(');
		if(bracketIndex == -1) {
			if(ignoreFirstError)
				return;
			throw new IGB_CL2_Exception("Function syntax error.");
		}
		String funcName = rest.substring(0, bracketIndex);
		String[] splited = Utils.getArrayElementsFromString(rest.substring(bracketIndex), '(', ')', ",");
		for (int i = 0; i < splited.length; i++) {
			String arg = splited[i];
			int spaceIndex = arg.indexOf(' ');
			if(spaceIndex == -1)
				throw new IGB_CL2_Exception("Function syntax error.");

			String type = arg.substring(0, spaceIndex);
			if(!IGB_CL2.varStr.contains(type))
				throw new IGB_CL2_Exception("Invalid type: \"" + type + "\".");
			String argName = arg.substring(spaceIndex + 1);
			splited[i] = argName;
		}
		String startPointer = ":f_" + funcName + "_" + splited.length + "_start";
		String endPointer = ":f_" + funcName + "_" + splited.length + "_end";
		Function func = new Function(funcName, startPointer, endPointer, splited, returnType, ram);
		addFunction(func, false);
	}

	private void initCompilerFunctions() {
		addFunction(new Function("sqrt", obj -> {
			EqSolver eqsolver = obj.getValue1();
			Field field = obj.getValue2()[0];
			int outCell = obj.getValue3();
			ArrayList<Instruction> list = new ArrayList<>();
			var pair1 = eqsolver.getInstructionsFromField(field);
			list.addAll(pair1.getSecond());
			list.add(Math_Sqrt(pair1.getFirst(), outCell));
			return list;
		}, 1, true), true);

		addFunction(new Function("screenUpdate", obj -> Utils.listOf(Device_ScreenUpdate()), 0, false), false);
		addFunction(new Function("exit", obj -> Utils.listOf(Cell_Jump(-2)), 0, false), false);
		addFunction(new Function("wait", obj -> {
			if(!obj.getValue2()[0].isVal())
				throw new IGB_CL2_Exception("Wait function argument has to be an integer.");

			double ticks = obj.getValue2()[0].value;
			if(ticks % 1 != 0)
				throw new IGB_CL2_Exception("Wait function argument has to be an integer.");

			return Utils.listOf(Device_Wait((int) ticks));
		}, 1, false), false);
		addFunction(new Function("dlog", obj -> {
			EqSolver eqs = obj.getValue1();
			Field field = obj.getValue2()[0];

			ArrayList<Instruction> list = new ArrayList<>();
			var numcell1 = eqs.getNumCell(field, -1);
			boolean isCell1 = numcell1.getFirst() != null;
			if(isCell1)
				list.addAll(numcell1.getFirst());

			list.add(Device_Log(isCell1, numcell1.getSecond()));
			return list;
		}, 1, false), false);

		addFunction(new Function("setpixel", obj -> {
			EqSolver eqs = obj.getValue1();
			ArrayList<Instruction> list = new ArrayList<>();
			Field f1 = obj.getValue2()[0], f2 = obj.getValue2()[1];

			var numcell1 = eqs.getNumCell(f1, -1);
			boolean isCell1 = numcell1.getFirst() != null;

			var numcell2 = eqs.getNumCell(f2, -1);
			boolean isCell2 = numcell2.getFirst() != null;

			if(isCell1 && isCell2 && !(f1.isVar() || f2.isVar()) && numcell1.getSecond().intValue() == numcell2.getSecond().intValue())
				numcell1 = eqs.getNumCell(f1, IGB_MA.CHARLIB_TEMP_START + 9);
			if(isCell1)
				list.addAll(numcell1.getFirst());
			if(isCell2)
				list.addAll(numcell2.getFirst());

			list.add(Pixel(isCell1, numcell1.getSecond().intValue(), isCell2, numcell2.getSecond().intValue()));
			return list;
		}, 2, false), false);

		addFunction(new Function("pixelcache", obj -> {
			EqSolver eqs = obj.getValue1();
			ArrayList<Instruction> list = new ArrayList<>();
			Field f1 = obj.getValue2()[0];

			var numcell1 = eqs.getNumCell(f1, -1);
			boolean isCell1 = numcell1.getFirst() != null;
			if(isCell1)
				list.addAll(numcell1.getFirst());

			if(!isCell1)
				return Utils.listOf(Pixel_Cache_Raw(numcell1.getSecond().intValue()));

			list.add(Pixel_Cache(numcell1.getSecond().intValue()));
			return list;
		}, 1, false), false);

		addFunction(new Function("pixelcache", obj -> {
			EqSolver eqs = obj.getValue1();
			ArrayList<Instruction> list = new ArrayList<>();
			Field f1 = obj.getValue2()[0], f2 = obj.getValue2()[1], f3 = obj.getValue2()[2];

			var numcell1 = eqs.getNumCell(f1, -1);
			boolean isCell1 = numcell1.getFirst() != null;
			if(isCell1)
				list.addAll(numcell1.getFirst());

			var numcell2 = eqs.getNumCell(f2, -1);
			boolean isCell2 = numcell2.getFirst() != null;
			if(isCell2)
				list.addAll(numcell2.getFirst());

			var numcell3 = eqs.getNumCell(f3, -1);
			boolean isCell3 = numcell3.getFirst() != null;
			if(isCell3)
				list.addAll(numcell3.getFirst());

			if((f1.isVal() || f1.isVar()) || (f2.isVal() || f2.isVar()) || (f3.isVal() || f3.isVar())) {
				return Utils.listOf(Pixel_Cache(isCell1, isCell1 ? f1.cell : (int) f1.value, isCell2, isCell2 ? f2.cell : (int) f2.value, isCell3,
						isCell3 ? f3.cell : (int) f3.value));
			}

			list.add(Pixel_Cache(isCell1, numcell1.getSecond().intValue(), isCell2, numcell2.getSecond().intValue(), isCell3,
					numcell3.getSecond().intValue()));
			return list;
		}, 3, false), false);

		addFunction(new Function("getpixel", obj -> {
			EqSolver eqs = obj.getValue1();
			Field f1 = obj.getValue2()[0], f2 = obj.getValue2()[1];
			int cell = obj.getValue3();

			ArrayList<Instruction> list = new ArrayList<>();

			var numcell1 = eqs.getNumCell(f1, -1);
			boolean isCell1 = numcell1.getFirst() != null;

			var numcell2 = eqs.getNumCell(f2, -1);
			boolean isCell2 = numcell2.getFirst() != null;

			if(isCell1 && isCell2 && !(f1.isVar() || f2.isVar()) && numcell1.getSecond().intValue() == numcell2.getSecond().intValue())
				numcell1 = eqs.getNumCell(f1, IGB_MA.CHARLIB_TEMP_START + 11);

			if(isCell1)
				list.addAll(numcell1.getFirst());
			if(isCell2)
				list.addAll(numcell2.getFirst());

			list.add(Pixel_Get(isCell1, numcell1.getSecond().intValue(), isCell2, numcell2.getSecond().intValue(), cell));
			return list;
		}, 2, true), false);

		addFunction(new Function("getpixel", obj -> {
			EqSolver eqs = obj.getValue1();
			Field f1 = obj.getValue2()[0], f2 = obj.getValue2()[1], f3 = obj.getValue2()[2], f4 = obj.getValue2()[3], f5 = obj.getValue2()[4];

			Pair<Integer, ArrayList<Instruction>> pair = eqs.getInstructionsFromField(f3);
			ArrayList<Instruction> list = pair.getSecond();

			var numcell1 = eqs.getNumCell(f1, -1);
			boolean isCell1 = numcell1.getFirst() != null;

			var numcell2 = eqs.getNumCell(f2, -1);
			boolean isCell2 = numcell2.getFirst() != null;

			if(isCell1 && isCell2 && !(f1.isVar() || f2.isVar()) && numcell1.getSecond().intValue() == numcell2.getSecond().intValue())
				numcell1 = eqs.getNumCell(f1, IGB_MA.CHARLIB_TEMP_START + 11);

			if(isCell1)
				list.addAll(numcell1.getFirst());
			if(isCell2)
				list.addAll(numcell2.getFirst());

			int cell1 = switch (f3.fieldType) {
			case Val -> (int) f3.value;
			case Var -> f3.cell;
			default -> throw new IGB_CL2_Exception("Getpixel rgb arg 3: R output cell has to be an integer or a variable.");
			};
			int cell2 = switch (f4.fieldType) {
			case Val -> (int) f4.value;
			case Var -> f4.cell;
			default -> throw new IGB_CL2_Exception("Getpixel rgb arg 4: R output cell has to be an integer or a variable.");
			};
			int cell3 = switch (f5.fieldType) {
			case Val -> (int) f5.value;
			case Var -> f5.cell;
			default -> throw new IGB_CL2_Exception("Getpixel rgb arg 5: B output cell has to be an integer or a variable.");
			};

			int outCell = (cell1 == cell2 - 1 && cell1 == cell3 - 2) ? cell1 : IGB_MA.CHARLIB_TEMP_START + 15;

			list.add(Pixel_Get(isCell1, numcell1.getSecond().intValue(), isCell2, numcell2.getSecond().intValue(), outCell));
			if(outCell == IGB_MA.CHARLIB_TEMP_START + 15) {
				list.add(Copy(IGB_MA.CHARLIB_TEMP_START + 15, cell1));
				list.add(Copy(IGB_MA.CHARLIB_TEMP_START + 16, cell2));
				list.add(Copy(IGB_MA.CHARLIB_TEMP_START + 17, cell3));
			}

			return list;
		}, 5, false), false);
	}

	RAM[] rams;
	int[] startlines;
	int[] lenlimits;

	public Functions(String[][] inputs, String[] fileNames, IGB_CL2 cl2) {
		functionMap = new HashMap<>();
		rams = new RAM[inputs.length];
		startlines = new int[inputs.length];
		lenlimits = new int[inputs.length];

		initCompilerFunctions();

		for (file = 0; file < inputs.length; file++) {
			cl2.file = file;
			String[] input = inputs[file];
			HashMap<String, Double> finalVars = new HashMap<>();

			int startline = -1, lenlimit = -1, thread = 0, ramcell = -1, ramlimit = -1;
			boolean ramInited = false;

			for (line = 0; line < input.length; line++) {
				cl2.line = line;
				String cmd = input[line];
				int spaceIndex = cmd.indexOf(' ');
				if(spaceIndex == -1)
					spaceIndex = cmd.length();
				String first = cmd.substring(0, spaceIndex);

				if(first.startsWith("$")) {
					String[] split = cmd.split("=");
					if(split.length == 1)
						throw new IGB_CL2_Exception("Compiler variable has to be set.");
					if(split.length > 2)
						throw new IGB_CL2_Exception("Syntax error.");
					String eq = split[1].strip();

					String name = split[0].substring(1).strip();

					double valD = RAM.solveFinalEq(eq, new HashMap<>());
					if(valD % 1 != 0)
						throw new IGB_CL2_Exception("Compiler variable value has to be an integer.");
					int val = (int) valD;
					if(val < 0)
						throw new IGB_CL2_Exception("Compiler variable value cannot be negative.");

					switch (name.toLowerCase()) {
					case "startline" -> {
						if(startline != -1)
							throw new IGB_CL2_Exception("Cannot set startline twice.");
						startline = val;
					}
					case "lenlimit" -> {
						if(lenlimit != -1)
							throw new IGB_CL2_Exception("Cannot set lenlimit twice.");
						lenlimit = val;
					}
					case "ramlimit" -> {
						if(ramlimit != -1)
							throw new IGB_CL2_Exception("Cannot set ramlimit twice.");
						ramlimit = val;
					}
					case "ramcell" -> {
						if(ramcell != -1)
							throw new IGB_CL2_Exception("Cannot set ramcell twice.");
						ramcell = val;
					}
					case "thread" -> {
						if(thread > 1)
							throw new IGB_CL2_Exception("Thread can be only 0 or 1.");
					}
					default -> throw new IGB_CL2_Exception("Unknown compiler variable: \"" + name + "\".");
					}
				} else {
					if(!ramInited) {
						if(startline == -1)
							throw new IGB_CL2_Exception("Startline has to be set.");
						if(ramcell == -1)
							throw new IGB_CL2_Exception("Ramcell has to be set.");
						if(ramlimit == -1)
							throw new IGB_CL2_Exception("Ramlimit has to be set.");

						rams[file] = new RAM(ramlimit, ramcell, thread);
						startlines[file] = startline;
						lenlimits[file] = lenlimit == -1 ? Integer.MAX_VALUE : lenlimit;
						ramInited = true;
					}
					if(first.equals("void"))
						initFunction(false, cmd.substring(spaceIndex + 1), false, rams[file]);
					else if(IGB_CL2.varStr.contains(first) && !cmd.contains("="))
						initFunction(true, cmd.substring(spaceIndex + 1), true, rams[file]);
					else if(first.equals("final")) {
						// init final vars
						if(!cmd.contains("="))
							throw new IGB_CL2_Exception("Final variable has to be set.");
						String[] split = cmd.split("=");
						int spaceIndex1 = split[0].indexOf(' ');
						if(spaceIndex1 == -1)
							throw new IGB_CL2_Exception("Syntax error.");
						split[0] = split[0].substring(spaceIndex1).strip();
						if(split.length != 2)
							throw new IGB_CL2_Exception("Syntax error.");
						String eq = split[1].strip();
						spaceIndex1 = split[0].indexOf(' ');
						if(spaceIndex1 == -1)
							throw new IGB_CL2_Exception("Syntax error.");

						split[0] = split[0].stripLeading();
						String varname = split[0].substring(spaceIndex1).strip();
						String type = split[0].substring(0, spaceIndex + 1).strip();

						if(!IGB_CL2.varStr.contains(type))
							throw new IGB_CL2_Exception("Unknown variable type: \"" + type + "\".");
						double val = RAM.solveFinalEq(eq, finalVars);
						if(finalVars.containsKey(varname))
							throw new IGB_CL2_Exception("Final variable already exists: \"" + varname + "\".");
						finalVars.put(varname, val);
					}
				}

			}
			rams[file].setFinalVariables(finalVars);
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
